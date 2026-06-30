package kreyj.konfplan.domain.service;

import com.opencsv.bean.CsvToBean;
import com.opencsv.bean.CsvToBeanBuilder;
import io.quarkus.elytron.security.common.BcryptUtil;
import io.quarkus.runtime.LaunchMode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.OptimisticLockException;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import kreyj.konfplan.adapter.in.web.dto.NutzerDto;
import kreyj.konfplan.adapter.in.web.dto.NutzerVerfuegbarkeitDto;
import kreyj.konfplan.adapter.in.web.dto.TeilnehmerVeranstaltungDto;
import kreyj.konfplan.adapter.in.web.dto.VortragDto;
import kreyj.konfplan.adapter.in.web.dto.VortragPrioDto;
import kreyj.konfplan.adapter.in.web.dto.csv.TeilnehmerCsvDto;
import kreyj.konfplan.application.port.in.TeilnehmerServiceInterface;
import kreyj.konfplan.persistence.Nutzer;
import kreyj.konfplan.persistence.NutzerVerfuegbarkeit;
import kreyj.konfplan.persistence.Pflichtvortrag;
import kreyj.konfplan.persistence.Planungsergebnis;
import kreyj.konfplan.persistence.Prioritaet;
import kreyj.konfplan.persistence.ProtokollKategorie;
import kreyj.konfplan.persistence.Teilnehmer;
import kreyj.konfplan.persistence.Veranstaltung;
import kreyj.konfplan.persistence.Vortrag;
import kreyj.konfplan.persistence.Wahlvortrag;
import org.apache.commons.lang3.StringUtils;
import org.jboss.logging.Logger;

import java.io.FileReader;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static kreyj.konfplan.persistence.NutzerVerfuegbarkeitId.nvIdL;

@ApplicationScoped
public class TeilnehmerService implements TeilnehmerServiceInterface {

    private static final Logger LOG = Logger.getLogger(TeilnehmerService.class);

    private final ProtokollService protokollService;
    private final MailService mailService;
    private final LaunchMode launchMode;


    public TeilnehmerService(ProtokollService protokollService, MailService mailService, LaunchMode launchMode) {
        this.protokollService = protokollService;
        this.mailService = mailService;
        this.launchMode = launchMode;
    }


    @Transactional
    @Override
    public List<Teilnehmer> findAll(Long veranstaltungId) {
        return Nutzer.find("role = 'TEILNEHMER' and veranstaltung.id = ?1", veranstaltungId).list();
    }


    @Transactional
    @Override
    public Teilnehmer findById(Long id) {
        return Nutzer.findById(id);
    }


    @Transactional
    @Override
    public Teilnehmer findByEmail(String email) {
        if (null == email) {
            return null;
        }
        return Teilnehmer.find("email", email.trim().toLowerCase()).firstResult();
    }


    @Transactional
    @Override
    public List<TeilnehmerVeranstaltungDto> getTeilnehmerVeranstaltungen(String email) {
        Teilnehmer teilnehmer = findByEmail(email);
        if (null == teilnehmer) {
            return Collections.emptyList();
        }

        return teilnehmer.getVeranstaltungen().stream()
            .map(e -> {
                TeilnehmerVeranstaltungDto dto = new TeilnehmerVeranstaltungDto();
                dto.id = e.getId();
                dto.name = e.getName();
                dto.beginntAm = e.getBeginntAm();
                dto.endetAm = e.getEndetAm();
                dto.deadlineTeilnehmer = e.getDeadlineTeilnehmer();
                dto.planErstellt = Planungsergebnis.count("veranstaltung", e) > 0;
                return dto;
            })
            .sorted(Comparator.comparing(e -> e.beginntAm))
            .toList();
    }


    @Transactional
    @Override
    public List<VortragDto> getVortraegeFuerTeilnehmerInVeranstaltung(Long veranstaltungId, String email) {
        Teilnehmer teilnehmer = findByEmail(email);
        if (null == teilnehmer) {
            throw new NotFoundException("Teilnehmer nicht gefunden.");
        }
        Veranstaltung veranstaltung = Veranstaltung.findById(veranstaltungId);
        if (null == veranstaltung) {
            throw new NotFoundException("Veranstaltung nicht gefunden.");
        }

        Set<Vortrag> alleVortraege = veranstaltung.getVortraege();
        Set<String> teilnehmerGruppen = teilnehmer.getGruppen();

        return alleVortraege.stream()
            .filter(vortrag -> {
                if (vortrag instanceof Pflichtvortrag pv) {
                    return teilnehmerGruppen.contains(pv.getPflichtgruppe());
                } else {
                    return true;
                }
            })
            .map(VortragDto::from)
            .collect(Collectors.toList());
    }


    @Transactional
    @Override
    public Teilnehmer createTeilnehmer(Teilnehmer user, Long veranstaltungId) {
        if (null == user || null == user.getEmail()) {
            protokollService.log(ProtokollKategorie.NUTZER, "Teilnehmer-Erstellung fehlgeschlagen", "Ungültige Nutzerdaten.");
            return null;
        }

        Teilnehmer existing = findByEmail(user.getEmail().trim().toLowerCase());
        if (existing != null) {
            LOG.warn("Teilnehmer konnte nicht erstellt werden: Email " + user.getEmail() + " bereits vergeben.");
            protokollService.log(ProtokollKategorie.NUTZER, "Teilnehmer-Erstellung fehlgeschlagen", "E-Mail bereits vergeben: " + user.getEmail());
            return null;
        }

        Veranstaltung v = Veranstaltung.findById(veranstaltungId);
        if (null == v) {
            protokollService.log(ProtokollKategorie.NUTZER, "Teilnehmer-Erstellung fehlgeschlagen", "Veranstaltung nicht gefunden: " + veranstaltungId);
            throw new IllegalArgumentException("Veranstaltung nicht gefunden.");
        }

        user.addVeranstaltung(v);
        String tempPassword = (launchMode.isDevOrTest() ? "konfplan" : UUID.randomUUID().toString());
        user.setPasswordHash(BcryptUtil.bcryptHash(tempPassword));

        user.persistAndFlush();
        protokollService.log(ProtokollKategorie.NUTZER, "Teilnehmer erstellt", "Teilnehmer " + user.getEmail() + " für Veranstaltung " + v.getName() + " erstellt.", user.getId());
        return user;
    }


    @Transactional
    @Override
    public int importFromCsv(Path csvFilePath, Long veranstaltungId) throws Exception {
        Veranstaltung v = Veranstaltung.findById(veranstaltungId);
        if (null == v) {
            LOG.error("CSV-Import abgebrochen: Veranstaltung mit ID " + veranstaltungId + " nicht gefunden.");
            protokollService.log(ProtokollKategorie.NUTZER, "Teilnehmer-Import fehlgeschlagen", "Veranstaltung nicht gefunden: " + veranstaltungId);
            throw new IllegalArgumentException("Veranstaltung nicht gefunden.");
        }

        int count = 0;
        try (FileReader reader = new FileReader(csvFilePath.toFile())) {
            CsvToBean<TeilnehmerCsvDto> csvToBean = new CsvToBeanBuilder<TeilnehmerCsvDto>(reader).withType(TeilnehmerCsvDto.class).withIgnoreLeadingWhiteSpace(true).withSeparator(';').withFilter(line -> line.length > 0 && !line[0].startsWith("#")).withThrowExceptions(false).build();

            List<TeilnehmerCsvDto> beans = csvToBean.parse();

            csvToBean.getCapturedExceptions().forEach(e -> {
                LOG.error("CSV-Parsing-Fehler in " + csvFilePath.getFileName() + " (Zeile " + e.getLineNumber() + "): " + e.getMessage());
                protokollService.log(ProtokollKategorie.SYSTEM, "CSV-Parsing-Fehler", "Teilnehmer-Import: " + e.getMessage() + " in Zeile " + e.getLineNumber());
            });

            for (TeilnehmerCsvDto csvDto : beans) {
                if (StringUtils.isBlank(csvDto.email)) {
                    LOG.warn("Teilnehmer-Zeile übersprungen: Email fehlt.");
                    protokollService.log(ProtokollKategorie.NUTZER, "Teilnehmer-Import übersprungen", "E-Mail fehlte in CSV-Zeile.");
                    continue;
                }

                String email = csvDto.email.trim().toLowerCase();
                if (Nutzer.findByEmail(email) == null) {
                    Teilnehmer tn = new Teilnehmer();
                    tn.setEmail(email);
                    tn.setFirstName(csvDto.vorname);
                    tn.setLastName(csvDto.nachname);

                    if (StringUtils.isNotBlank(csvDto.gruppen)) {
                        for (String splitter : csvDto.gruppen.split("\\|")) {
                            String gruppe = splitter.trim();
                            if (StringUtils.isBlank(gruppe)) {
                                continue;
                            }
                            // Unbekannte Gruppe automatisch in der Veranstaltung anlegen.
                            if (!v.getGruppen().contains(gruppe)) {
                                v.addGruppe(gruppe);
                                LOG.info("Neue Gruppe '" + gruppe + "' beim Import für Veranstaltung '"
                                    + v.getName() + "' angelegt.");
                            }
                            tn.addGruppe(gruppe);
                        }
                    }

                    String tempPassword = (launchMode.isDevOrTest() ? "konfplan" : UUID.randomUUID().toString());
                    tn.setPasswordHash(BcryptUtil.bcryptHash(tempPassword));

                    tn.persistAndFlush();
                    tn.addVeranstaltung(v);

                    count++;
                    protokollService.log(ProtokollKategorie.NUTZER, "Teilnehmer importiert", "Teilnehmer " + tn.getEmail() + " für Veranstaltung " + v.getName() + " importiert.", tn.getId());
                } else {
                    LOG.warn("Teilnehmer übersprungen: Email " + email + " existiert bereits.");
                    protokollService.log(ProtokollKategorie.NUTZER, "Teilnehmer-Import übersprungen", "E-Mail existiert bereits: " + email);
                }
            }
        } catch (Exception e) {
            LOG.error("Kritischer Fehler beim Importieren der Teilnehmer aus CSV: " + csvFilePath, e);
            protokollService.log(ProtokollKategorie.SYSTEM, "Kritischer Fehler beim Teilnehmer-Import", e.getMessage());
            throw e;
        }
        LOG.info("CSV-Import abgeschlossen: " + count + " Teilnehmer aus " + csvFilePath + " importiert.");
        protokollService.log(ProtokollKategorie.NUTZER, "Teilnehmer-Import abgeschlossen", count + " Teilnehmer importiert für Veranstaltung " + v.getName() + ".");
        return count;
    }


    @Transactional
    @Override
    public void deleteUser(Nutzer nutzer) {
        String email = nutzer.getEmail();
        Long id = nutzer.getId();
        nutzer.delete();
        protokollService.log(ProtokollKategorie.NUTZER, "Nutzer gelöscht", "Nutzer " + email + " gelöscht.", id);
    }


    @Transactional
    @Override
    public void toggleActive(Nutzer nutzer) {
        nutzer.setActive(!nutzer.isActive());
        nutzer.persistAndFlush();
        protokollService.log(ProtokollKategorie.NUTZER, "Nutzer-Status geändert", "Nutzer " + nutzer.getEmail() + " ist jetzt " + (nutzer.isActive() ? "aktiv" : "inaktiv") + ".", nutzer.getId());
    }


    @Transactional
    @Override
    public Teilnehmer updateTeilnehmerProfile(Teilnehmer teilnehmer, NutzerDto dto) {
        if (null == teilnehmer) {
            throw new WebApplicationException("Teilnehmer nicht gefunden", Response.Status.NOT_FOUND);
        }
        if (!teilnehmer.getEmail().equals(dto.email)) {
            throw new WebApplicationException("E-Mail kann nicht geändert werden", Response.Status.BAD_REQUEST);
        }

        if (dto.version != null && !teilnehmer.getVersion().equals(dto.version)) {
            throw new OptimisticLockException("Das Profil wurde in der Zwischenzeit von einem anderen Benutzer geändert.");
        }

        teilnehmer.setFirstName(dto.firstName);
        teilnehmer.setLastName(dto.lastName);
        dto.gruppen.forEach(teilnehmer::addGruppe);
        teilnehmer.setActive(dto.isActive);

        teilnehmer.persistAndFlush();

        return teilnehmer;
    }


    @Transactional
    @Override
    public Teilnehmer updateTeilnehmer(Long id, NutzerDto tnDto, Long veranstaltungId) {
        Nutzer existing = Nutzer.findById(id);
        if (!(existing instanceof Teilnehmer tn)) {
            protokollService.log(ProtokollKategorie.NUTZER, "Teilnehmer-Update fehlgeschlagen", "Teilnehmer mit ID " + id + " nicht gefunden oder falscher Typ.");
            return null;
        }

        if (tnDto.version != null && !tn.getVersion().equals(tnDto.version)) {
            throw new OptimisticLockException("Der Teilnehmer wurde in der Zwischenzeit von einem anderen Benutzer geändert.");
        }

        String oldEmail = tn.getEmail();
        tn.setFirstName(tnDto.firstName);
        tn.setLastName(tnDto.lastName);
        tn.setEmail(null == tnDto.email ? existing.getEmail() : tnDto.email.trim().toLowerCase());
        tnDto.gruppen.forEach(tn::addGruppe);
        tn.setActive(tnDto.isActive);

        Veranstaltung veranstaltung = Veranstaltung.findById(veranstaltungId);
        if (null != veranstaltung) {
            tn.addVeranstaltung(veranstaltung);
        }

        tn.persistAndFlush();

        protokollService.log(ProtokollKategorie.NUTZER, "Teilnehmer aktualisiert", "Teilnehmer " + oldEmail + " (ID: " + tn.getId() + ") aktualisiert. Neue E-Mail: " + tn.getEmail() + ".", tn.getId());
        return tn;
    }


    @Transactional
    @Override
    public void savePriorities(Long userId, Long veranstaltungId, List<VortragPrioDto> priorityDtos) {
        Teilnehmer teilnehmer = Teilnehmer.findById(userId);
        if (null == teilnehmer) {
            protokollService.log(ProtokollKategorie.NUTZER, "Prioritäten-Speicherung fehlgeschlagen", "Teilnehmer mit ID " + userId + " nicht gefunden.");
            throw new NotFoundException("Teilnehmer mit ID " + userId + " nicht gefunden.");
        }

        Veranstaltung veranstaltung = Veranstaltung.findById(veranstaltungId);
        if (null == veranstaltung) {
            protokollService.log(ProtokollKategorie.NUTZER, "Prioritäten-Speicherung fehlgeschlagen", "Veranstaltung mit ID " + veranstaltungId + " nicht gefunden.");
            throw new NotFoundException("Veranstaltung mit ID " + veranstaltungId + " nicht gefunden.");
        }

        if (LocalDateTime.now().isAfter(veranstaltung.getEndetAm())) {
            protokollService.log(ProtokollKategorie.NUTZER, "Prioritäten-Speicherung fehlgeschlagen", "Veranstaltung " + veranstaltung.getName() + " ist beendet. Nutzer: " + teilnehmer.getEmail() + ".", teilnehmer.getId());
            throw new ForbiddenException("Die Veranstaltung ist bereits beendet. Prioritäten können nicht mehr geändert werden.");
        }

        Prioritaet.delete("teilnehmer = ?1 and vortrag.veranstaltung = ?2", teilnehmer, veranstaltung);

        for (VortragPrioDto dto : priorityDtos) {
            if (dto.prioWert > 0) {
                Wahlvortrag vortrag = Wahlvortrag.findById(dto.vortragId);
                if (null == vortrag) {
                    LOG.warn("Vortrag mit ID " + dto.vortragId + " für Priorität von Teilnehmer " + userId + " nicht gefunden. Überspringe.");
                    protokollService.log(ProtokollKategorie.NUTZER, "Prioritäten-Speicherung Warnung", "Vortrag " + dto.vortragId + " für Priorität von " + teilnehmer.getEmail() + " nicht gefunden.", teilnehmer.getId());
                    continue;
                }
                if (!vortrag.getVeranstaltung().getId().equals(veranstaltungId)) {
                    protokollService.log(ProtokollKategorie.NUTZER, "Prioritäten-Speicherung fehlgeschlagen", "Vortrag " + dto.vortragId + " gehört nicht zur Veranstaltung " + veranstaltungId + ". Nutzer: " + teilnehmer.getEmail() + ".", teilnehmer.getId());
                    throw new BadRequestException("Vortrag " + dto.vortragId + " gehört nicht zur Veranstaltung " + veranstaltungId);
                }

                Prioritaet prioritaet = new Prioritaet();
                prioritaet.setTeilnehmer(teilnehmer);
                prioritaet.setVortrag(vortrag);
                prioritaet.setPrioWert(dto.prioWert);
                prioritaet.persistAndFlush();
            }
        }
        protokollService.log(ProtokollKategorie.NUTZER, "Prioritäten gespeichert", "Prioritäten für Teilnehmer " + teilnehmer.getEmail() + " in Veranstaltung " + veranstaltung.getName() + " gespeichert.", teilnehmer.getId());
    }


    @Transactional
    @Override
    public void updateVerfuegbarkeit(Long veranstaltungId, NutzerVerfuegbarkeitDto dto, String userEmail) {
        Nutzer nutzer = Nutzer.findByEmail(userEmail);
        if (!(nutzer instanceof Teilnehmer) || !nutzer.getId().equals(dto.nutzerId)) {
            throw new ForbiddenException("Keine Berechtigung.");
        }
        Veranstaltung veranstaltung = Veranstaltung.findById(veranstaltungId);
        if (null == veranstaltung) {
            throw new NotFoundException("Veranstaltung nicht gefunden.");
        }
        if (veranstaltung.getDeadlineTeilnehmer() != null && veranstaltung.getDeadlineTeilnehmer().isBefore(LocalDateTime.now())) {
            throw new ForbiddenException("Die Deadline für Teilnehmer ist bereits abgelaufen.");
        }
        NutzerVerfuegbarkeit v = NutzerVerfuegbarkeit.findById(nvIdL(nutzer.getId(), veranstaltungId));
        if (null == v) {
            throw new NotFoundException("Verfügbarkeitseintrag nicht gefunden.");
        }
        v.getVerfuegbareSlotIds().clear();
        v.getVerfuegbareSlotIds().addAll(dto.verfuegbareSlotIds);
        v.persist();

        mailService.sendVerfuegbarkeitChangedNotification(nutzer, veranstaltung);
    }
}
