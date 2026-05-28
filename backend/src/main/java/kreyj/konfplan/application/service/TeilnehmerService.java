package kreyj.konfplan.application.service;

import com.opencsv.bean.CsvToBean;
import com.opencsv.bean.CsvToBeanBuilder;
import io.quarkus.elytron.security.common.BcryptUtil;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.OptimisticLockException;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import kreyj.konfplan.presentation.dto.NutzerDto;
import kreyj.konfplan.presentation.dto.VortragPrioDto;
import kreyj.konfplan.presentation.dto.csv.TeilnehmerCsvDto;
import kreyj.konfplan.persistence.*;
import org.jboss.logging.Logger;

import java.io.FileReader;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class TeilnehmerService {

    private static final Logger LOG = Logger.getLogger(TeilnehmerService.class);

    private final ProtokollService protokollService;

    public TeilnehmerService(ProtokollService protokollService) {
        this.protokollService = protokollService;
    }

    public List<Teilnehmer> findAll(Long veranstaltungId) {
        return Nutzer.find("role = 'TEILNEHMER' and veranstaltung.id = ?1", veranstaltungId).list();
    }

    public Teilnehmer findById(Long id) {
        return Nutzer.findById(id);
    }

    public Teilnehmer findByEmail(String email) {
        if (null == email) {
            return null;
        }
        return Teilnehmer.find("email", email.trim().toLowerCase()).firstResult();
    }

    @Transactional
    public Teilnehmer createTeilnehmer(Teilnehmer user, Long veranstaltungId) {
        if (user == null || user.getEmail() == null) {
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
        if (v == null) {
            protokollService.log(ProtokollKategorie.NUTZER, "Teilnehmer-Erstellung fehlgeschlagen", "Veranstaltung nicht gefunden: " + veranstaltungId);
            throw new IllegalArgumentException("Veranstaltung nicht gefunden.");
        }

        user.addVeranstaltung(v);
        String tempPassword = UUID.randomUUID().toString();
        user.setPasswordHash(BcryptUtil.bcryptHash(tempPassword));

        user.persist();
        protokollService.log(ProtokollKategorie.NUTZER, "Teilnehmer erstellt", "Teilnehmer " + user.getEmail() + " für Veranstaltung " + v.getName() + " erstellt.", user.getId());
        return user;
    }

    @Transactional
    public int importFromCsv(Path csvFilePath, Long veranstaltungId) throws Exception {
        Veranstaltung v = Veranstaltung.findById(veranstaltungId);
        if (v == null) {
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

            for (TeilnehmerCsvDto dto : beans) {
                if (dto.email == null || dto.email.isBlank()) {
                    LOG.warn("Teilnehmer-Zeile übersprungen: Email fehlt.");
                    protokollService.log(ProtokollKategorie.NUTZER, "Teilnehmer-Import übersprungen", "E-Mail fehlte in CSV-Zeile.");
                    continue;
                }

                String email = dto.email.trim().toLowerCase();
                if (Nutzer.findByEmail(email) == null) {
                    Teilnehmer tn = new Teilnehmer();
                    tn.setEmail(email);
                    tn.setFirstName(dto.vorname);
                    tn.setLastName(dto.nachname);
                    tn.setGruppe(dto.gruppe);

                    String tempPassword = "start123"; // UUID.randomUUID().toString();
                    tn.setPasswordHash(BcryptUtil.bcryptHash(tempPassword));

                    tn.persist();
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
        LOG.info("CSV-Import abgeschlossen: " + count + " Teilnehmer erfolgreich importiert.");
        protokollService.log(ProtokollKategorie.NUTZER, "Teilnehmer-Import abgeschlossen", count + " Teilnehmer importiert für Veranstaltung " + v.getName() + ".");
        return count;
    }

    @Transactional
    public void deleteUser(Nutzer nutzer) {
        String email = nutzer.getEmail();
        Long id = nutzer.getId();
        nutzer.delete();
        protokollService.log(ProtokollKategorie.NUTZER, "Nutzer gelöscht", "Nutzer " + email + " gelöscht.", id);
    }

    @Transactional
    public void toggleActive(Nutzer nutzer) {
        nutzer.setActive(!nutzer.isActive());
        nutzer.persist();
        protokollService.log(ProtokollKategorie.NUTZER, "Nutzer-Status geändert", "Nutzer " + nutzer.getEmail() + " ist jetzt " + (nutzer.isActive() ? "aktiv" : "inaktiv") + ".", nutzer.getId());
    }

    @Transactional
    public Teilnehmer updateTeilnehmerProfile(Teilnehmer teilnehmer, NutzerDto dto) {
        if (teilnehmer == null) {
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
        teilnehmer.setGruppe(dto.gruppe);
        teilnehmer.setActive(dto.isActive);

        teilnehmer.persistAndFlush();

        return teilnehmer;
    }

    @Transactional
    public Teilnehmer updateTeilnehmer(Long id, NutzerDto tnDto, Long veranstaltungId) {
        Nutzer existing = Nutzer.findById(id);
        if (!(existing instanceof Teilnehmer)) {
            protokollService.log(ProtokollKategorie.NUTZER, "Teilnehmer-Update fehlgeschlagen", "Teilnehmer mit ID " + id + " nicht gefunden oder falscher Typ.");
            return null;
        }

        Teilnehmer tn = (Teilnehmer) existing;

        if (tnDto.version != null && !tn.getVersion().equals(tnDto.version)) {
            throw new OptimisticLockException("Der Teilnehmer wurde in der Zwischenzeit von einem anderen Benutzer geändert.");
        }

        String oldEmail = tn.getEmail();
        tn.setFirstName(tnDto.firstName);
        tn.setLastName(tnDto.lastName);
        tn.setEmail(tnDto.email == null ? existing.getEmail() : tnDto.email.trim().toLowerCase());
        tn.setGruppe(tnDto.gruppe);
        tn.setActive(tnDto.isActive);

        Veranstaltung veranstaltung = Veranstaltung.findById(veranstaltungId);
        if (null != veranstaltung) {
            tn.addVeranstaltung(veranstaltung);
        }

        tn.persist();

        protokollService.log(ProtokollKategorie.NUTZER, "Teilnehmer aktualisiert", "Teilnehmer " + oldEmail + " (ID: " + tn.getId() + ") aktualisiert. Neue E-Mail: " + tn.getEmail() + ".", tn.getId());
        return tn;
    }

    @Transactional
    public void savePriorities(Long userId, Long veranstaltungId, List<VortragPrioDto> priorityDtos) {
        Teilnehmer teilnehmer = Teilnehmer.findById(userId);
        if (teilnehmer == null) {
            protokollService.log(ProtokollKategorie.NUTZER, "Prioritäten-Speicherung fehlgeschlagen", "Teilnehmer mit ID " + userId + " nicht gefunden.");
            throw new NotFoundException("Teilnehmer mit ID " + userId + " nicht gefunden.");
        }

        Veranstaltung veranstaltung = Veranstaltung.findById(veranstaltungId);
        if (veranstaltung == null) {
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
                Vortrag vortrag = Vortrag.findById(dto.vortragId);
                if (vortrag == null) {
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
                prioritaet.persist();
            }
        }
        protokollService.log(ProtokollKategorie.NUTZER, "Prioritäten gespeichert", "Prioritäten für Teilnehmer " + teilnehmer.getEmail() + " in Veranstaltung " + veranstaltung.getName() + " gespeichert.", teilnehmer.getId());
    }
}