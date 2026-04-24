package kreyj.vortragsmanager.service;

import com.opencsv.bean.CsvToBean;
import com.opencsv.bean.CsvToBeanBuilder;
import io.quarkus.elytron.security.common.BcryptUtil;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import kreyj.vortragsmanager.dto.csv.TeilnehmerCsvDto;
import kreyj.vortragsmanager.entity.Nutzer;
import kreyj.vortragsmanager.entity.Teilnehmer;
import kreyj.vortragsmanager.entity.Veranstaltung;
import org.jboss.logging.Logger;

import java.io.FileReader;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class TeilnehmerService {

    private static final Logger LOG = Logger.getLogger(TeilnehmerService.class);

    public List<Nutzer> findAll(Long veranstaltungId) {
        return Nutzer.find("role = 'TEILNEHMER' and veranstaltung.id = ?1", veranstaltungId).list();
    }

    public Nutzer findById(Long id) {
        return Nutzer.findById(id);
    }

    @Transactional
    public Teilnehmer createTeilnehmer(Teilnehmer user, Long veranstaltungId) {
        if (user == null || user.email == null) {
            return null;
        }

        Nutzer existing = Nutzer.findByEmail(user.email.trim().toLowerCase());
        if (existing != null) {
            LOG.warn("Teilnehmer konnte nicht erstellt werden: Email " + user.email + " bereits vergeben.");
            return null;
        }

        Veranstaltung v = Veranstaltung.findById(veranstaltungId);
        if (v == null) {
            throw new IllegalArgumentException("Veranstaltung nicht gefunden.");
        }

        user.addVeranstaltung(v);
        String tempPassword = UUID.randomUUID().toString();
        user.passwordHash = BcryptUtil.bcryptHash(tempPassword);

        user.persist();
        return user;
    }

    @Transactional
    public int importFromCsv(Path csvFilePath, Long veranstaltungId) throws Exception {
        Veranstaltung v = Veranstaltung.findById(veranstaltungId);
        if (v == null) {
            LOG.error("CSV-Import abgebrochen: Veranstaltung mit ID " + veranstaltungId + " nicht gefunden.");
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

            csvToBean.getCapturedExceptions().forEach(e ->
                    LOG.error("CSV-Parsing-Fehler in " + csvFilePath.getFileName() + " (Zeile " + e.getLineNumber() + "): " + e.getMessage())
            );

            for (TeilnehmerCsvDto dto : beans) {
                if (dto.email == null || dto.email.isBlank()) {
                    LOG.warn("Teilnehmer-Zeile übersprungen: Email fehlt.");
                    continue;
                }

                String email = dto.email.trim().toLowerCase();
                if (Nutzer.findByEmail(email) == null) {
                    Teilnehmer tn = new Teilnehmer();
                    tn.email = email;
                    tn.firstName = dto.firstName;
                    tn.lastName = dto.lastName;
                    tn.gruppe = dto.gruppe;
                    tn.addVeranstaltung(v);

                    String tempPassword = "start123"; // UUID.randomUUID().toString();
                    tn.passwordHash = BcryptUtil.bcryptHash(tempPassword);

                    tn.persist();
                    count++;
                } else {
                    LOG.warn("Teilnehmer übersprungen: Email " + email + " existiert bereits.");
                }
            }
        } catch (Exception e) {
            LOG.error("Kritischer Fehler beim Importieren der Teilnehmer aus CSV: " + csvFilePath, e);
            throw e;
        }
        LOG.info("CSV-Import abgeschlossen: " + count + " Teilnehmer erfolgreich importiert.");
        return count;
    }

    @Transactional
    public void deleteUser(Nutzer nutzer) {
        nutzer.delete();
    }

    @Transactional
    public void toggleActive(Nutzer nutzer) {
        nutzer.isActive = !nutzer.isActive;
        nutzer.persist();
    }

    @Transactional
    public Teilnehmer updateTeilnehmer(Long id, Teilnehmer teilnehmer, Long veranstaltungId) {
        Nutzer existing = Nutzer.findById(id);
        if (existing == null || !(existing instanceof Teilnehmer)) {
            return null;
        }

        Teilnehmer tn = (Teilnehmer) existing;
        tn.firstName = teilnehmer.firstName;
        tn.lastName = teilnehmer.lastName;
        tn.email = teilnehmer.email == null ? existing.email : teilnehmer.email.trim().toLowerCase();
        tn.gruppe = teilnehmer.gruppe;
        tn.isActive = teilnehmer.isActive;

        Veranstaltung veranstaltung = Veranstaltung.findById(veranstaltungId);
        if (null != veranstaltung) {
            tn.addVeranstaltung(veranstaltung);
        }

        return tn;
    }
}
