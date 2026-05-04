package kreyj.vortragsmanager.service;

import com.opencsv.bean.CsvToBean;
import com.opencsv.bean.CsvToBeanBuilder;
import io.quarkus.elytron.security.common.BcryptUtil;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotFoundException;
import kreyj.vortragsmanager.dto.VortragPrioDto;
import kreyj.vortragsmanager.dto.csv.TeilnehmerCsvDto;
import kreyj.vortragsmanager.entity.*;
import org.jboss.logging.Logger;
import jakarta.inject.Inject;

import java.io.FileReader;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@ApplicationScoped
public class TeilnehmerService {

    private static final Logger LOG = Logger.getLogger(TeilnehmerService.class);

    @Inject
    ProtokollService protokollService;

    public List<Nutzer> findAll(Long veranstaltungId) {
        return Nutzer.find("role = 'TEILNEHMER' and veranstaltung.id = ?1", veranstaltungId).list();
    }

    public Nutzer findById(Long id) {
        return Nutzer.findById(id);
    }

    @Transactional
    public Teilnehmer createTeilnehmer(Teilnehmer user, Long veranstaltungId) {
        if (user == null || user.email == null) {
            protokollService.log(ProtokollKategorie.NUTZER, "Teilnehmer-Erstellung fehlgeschlagen", "Ungültige Nutzerdaten.");
            return null;
        }

        Nutzer existing = Nutzer.findByEmail(user.email.trim().toLowerCase());
        if (existing != null) {
            LOG.warn("Teilnehmer konnte nicht erstellt werden: Email " + user.email + " bereits vergeben.");
            protokollService.log(ProtokollKategorie.NUTZER, "Teilnehmer-Erstellung fehlgeschlagen", "E-Mail bereits vergeben: " + user.email);
            return null;
        }

        Veranstaltung v = Veranstaltung.findById(veranstaltungId);
        if (v == null) {
            protokollService.log(ProtokollKategorie.NUTZER, "Teilnehmer-Erstellung fehlgeschlagen", "Veranstaltung nicht gefunden: " + veranstaltungId);
            throw new IllegalArgumentException("Veranstaltung nicht gefunden.");
        }

        user.addVeranstaltung(v);
        String tempPassword = UUID.randomUUID().toString();
        user.passwordHash = BcryptUtil.bcryptHash(tempPassword);

        user.persist();
        protokollService.log(ProtokollKategorie.NUTZER, "Teilnehmer erstellt", "Teilnehmer " + user.email + " für Veranstaltung " + v.name + " erstellt.", user.id);
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
            CsvToBean<TeilnehmerCsvDto> csvToBean = new CsvToBeanBuilder<TeilnehmerCsvDto>(reader)
                    .withType(TeilnehmerCsvDto.class)
                    .withIgnoreLeadingWhiteSpace(true)
                    .withSeparator(';')
                    .withFilter(line -> line.length > 0 && !line[0].startsWith("#"))
                    .withThrowExceptions(false)
                    .build();

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
                    tn.email = email;
                    tn.firstName = dto.firstName;
                    tn.lastName = dto.lastName;
                    tn.gruppe = dto.gruppe;

                    String tempPassword = "start123"; // UUID.randomUUID().toString();
                    tn.passwordHash = BcryptUtil.bcryptHash(tempPassword);

                    tn.persist();
                    tn.addVeranstaltung(v);

                    count++;
                    protokollService.log(ProtokollKategorie.NUTZER, "Teilnehmer importiert", "Teilnehmer " + tn.email + " für Veranstaltung " + v.name + " importiert.", tn.id);
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
        protokollService.log(ProtokollKategorie.NUTZER, "Teilnehmer-Import abgeschlossen", count + " Teilnehmer importiert für Veranstaltung " + v.name + ".");
        return count;
    }

    @Transactional
    public void deleteUser(Nutzer nutzer) {
        String email = nutzer.email;
        Long id = nutzer.id;
        nutzer.delete();
        protokollService.log(ProtokollKategorie.NUTZER, "Nutzer gelöscht", "Nutzer " + email + " gelöscht.", id);
    }

    @Transactional
    public void toggleActive(Nutzer nutzer) {
        nutzer.isActive = !nutzer.isActive;
        nutzer.persist();
        protokollService.log(ProtokollKategorie.NUTZER, "Nutzer-Status geändert", "Nutzer " + nutzer.email + " ist jetzt " + (nutzer.isActive ? "aktiv" : "inaktiv") + ".", nutzer.id);
    }

    @Transactional
    public Teilnehmer updateTeilnehmer(Long id, Teilnehmer teilnehmer, Long veranstaltungId) {
        Nutzer existing = Nutzer.findById(id);
        if (existing == null || !(existing instanceof Teilnehmer)) {
            protokollService.log(ProtokollKategorie.NUTZER, "Teilnehmer-Update fehlgeschlagen", "Teilnehmer mit ID " + id + " nicht gefunden oder falscher Typ.");
            return null;
        }

        Teilnehmer tn = (Teilnehmer) existing;
        String oldEmail = tn.email;
        tn.firstName = teilnehmer.firstName;
        tn.lastName = teilnehmer.lastName;
        tn.email = teilnehmer.email == null ? existing.email : teilnehmer.email.trim().toLowerCase();
        tn.gruppe = teilnehmer.gruppe;
        tn.isActive = teilnehmer.isActive;

        Veranstaltung veranstaltung = Veranstaltung.findById(veranstaltungId);
        if (null != veranstaltung) {
            tn.addVeranstaltung(veranstaltung);
        }
        protokollService.log(ProtokollKategorie.NUTZER, "Teilnehmer aktualisiert", "Teilnehmer " + oldEmail + " (ID: " + tn.id + ") aktualisiert. Neue E-Mail: " + tn.email + ".", tn.id);
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

        if (LocalDateTime.now().isAfter(veranstaltung.endetAm)) {
            protokollService.log(ProtokollKategorie.NUTZER, "Prioritäten-Speicherung fehlgeschlagen", "Veranstaltung " + veranstaltung.name + " ist beendet. Nutzer: " + teilnehmer.email + ".", teilnehmer.id);
            throw new ForbiddenException("Die Veranstaltung ist bereits beendet. Prioritäten können nicht mehr geändert werden.");
        }

        Prioritaet.delete("teilnehmer = ?1 and vortrag.veranstaltung = ?2", teilnehmer, veranstaltung);

        for (VortragPrioDto dto : priorityDtos) {
            if (dto.prioWert > 0) {
                Vortrag vortrag = Vortrag.findById(dto.vortragId);
                if (vortrag == null) {
                    LOG.warn("Vortrag mit ID " + dto.vortragId + " für Priorität von Teilnehmer " + userId + " nicht gefunden. Überspringe.");
                    protokollService.log(ProtokollKategorie.NUTZER, "Prioritäten-Speicherung Warnung", "Vortrag " + dto.vortragId + " für Priorität von " + teilnehmer.email + " nicht gefunden.", teilnehmer.id);
                    continue;
                }
                if (!vortrag.veranstaltung.id.equals(veranstaltungId)) {
                    protokollService.log(ProtokollKategorie.NUTZER, "Prioritäten-Speicherung fehlgeschlagen", "Vortrag " + dto.vortragId + " gehört nicht zur Veranstaltung " + veranstaltungId + ". Nutzer: " + teilnehmer.email + ".", teilnehmer.id);
                    throw new BadRequestException("Vortrag " + dto.vortragId + " gehört nicht zur Veranstaltung " + veranstaltungId);
                }

                Prioritaet prioritaet = new Prioritaet();
                prioritaet.teilnehmer = teilnehmer;
                prioritaet.vortrag = vortrag;
                prioritaet.prioWert = dto.prioWert;
                prioritaet.persist();
            }
        }
        protokollService.log(ProtokollKategorie.NUTZER, "Prioritäten gespeichert", "Prioritäten für Teilnehmer " + teilnehmer.email + " in Veranstaltung " + veranstaltung.name + " gespeichert.", teilnehmer.id);
    }
}
