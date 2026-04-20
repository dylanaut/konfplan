package kreyj.vortragsmanager.service;

import com.opencsv.bean.CsvToBean;
import com.opencsv.bean.CsvToBeanBuilder;
import io.quarkus.elytron.security.common.BcryptUtil;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import kreyj.vortragsmanager.dto.csv.TeilnehmerCsvDto;
import kreyj.vortragsmanager.entity.Teilnehmer;
import kreyj.vortragsmanager.entity.User;
import kreyj.vortragsmanager.entity.Veranstaltung;
import kreyj.vortragsmanager.util.SQLiteBackup;
import org.jboss.logging.Logger;

import java.io.FileReader;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class TeilnehmerService {

    private static final Logger LOG = Logger.getLogger(TeilnehmerService.class);

    @Inject
    SQLiteBackup backupService;

    public List<User> findAll(Long veranstaltungId) {
        return User.find("select u from User u join u.veranstaltungen v where u.role = 'TEILNEHMER' and v.id = ?1", veranstaltungId).list();
    }

    public User findById(Long id) {
        return User.findById(id);
    }

    @Transactional
    public Teilnehmer createTeilnehmer(Teilnehmer user, Long veranstaltungId) {
        if (user == null || user.email == null) return null;

        User existing = User.findByEmail(user.email.trim().toLowerCase());
        if (existing != null) {
            LOG.warn("Teilnehmer konnte nicht erstellt werden: Email " + user.email + " bereits vergeben.");
            return null;
        }

        Veranstaltung v = Veranstaltung.findById(veranstaltungId);
        if (v == null) throw new IllegalArgumentException("Veranstaltung nicht gefunden.");

        user.veranstaltungen.add(v);
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
                User existing = User.findByEmail(email);
                Teilnehmer nt;
                if (existing == null) {
                    nt = new Teilnehmer();
                    nt.email = email;
                    nt.firstName = dto.firstName;
                    nt.lastName = dto.lastName;
                    nt.gruppe = dto.gruppe;
                    String tempPassword = "start123";
                    nt.passwordHash = BcryptUtil.bcryptHash(tempPassword);
                    nt.persist();
                } else if (existing instanceof Teilnehmer) {
                    nt = (Teilnehmer) existing;
                } else {
                    LOG.warn("User " + email + " exists but is not a Teilnehmer. Skipping.");
                    continue;
                }

                nt.veranstaltungen.add(v);
                count++;
            }
        } catch (Exception e) {
            LOG.error("Kritischer Fehler beim Importieren der Teilnehmer aus CSV: " + csvFilePath, e);
            throw e;
        }
        LOG.info("CSV-Import abgeschlossen: " + count + " Teilnehmer erfolgreich importiert.");
        return count;
    }

    @Transactional
    public void deleteUser(User user) {
        user.delete();
    }

    @Transactional
    public void toggleActive(User user) {
        user.isActive = !user.isActive;
        user.persist();
    }

    @Transactional
    public Teilnehmer updateTeilnehmer(Long id, Teilnehmer teilnehmer, Long veranstaltungId) {
        User existing = User.findById(id);
        if (existing == null || !(existing instanceof Teilnehmer)) return null;

        Teilnehmer tn = (Teilnehmer) existing;
        tn.firstName = teilnehmer.firstName;
        tn.lastName = teilnehmer.lastName;
        tn.email = teilnehmer.email == null ? existing.email : teilnehmer.email.trim().toLowerCase();
        tn.gruppe = teilnehmer.gruppe;
        tn.isActive = teilnehmer.isActive;
        
        if (veranstaltungId != null) {
            Veranstaltung v = Veranstaltung.findById(veranstaltungId);
            if (v != null) tn.veranstaltungen.add(v);
        }

        return tn;
    }
}
