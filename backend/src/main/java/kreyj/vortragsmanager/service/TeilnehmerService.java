package kreyj.vortragsmanager.service;

import com.opencsv.bean.CsvToBeanBuilder;
import io.quarkus.elytron.security.common.BcryptUtil;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import kreyj.vortragsmanager.dto.TeilnehmerCsvDto;
import kreyj.vortragsmanager.entity.Teilnehmer;
import kreyj.vortragsmanager.entity.User;
import kreyj.vortragsmanager.entity.Veranstaltung;

import java.io.FileReader;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class TeilnehmerService {

    public List<User> findAll(Long veranstaltungId) {
        return User.find("role = 'TEILNEHMER' and veranstaltung.id = ?1", veranstaltungId).list();
    }

    public User findById(Long id) {
        return User.findById(id);
    }

    @Transactional
    public Teilnehmer createTeilnehmer(Teilnehmer user, Long veranstaltungId) {
        if (user == null || user.email == null) return null;

        User existing = User.findByEmail(user.email.trim().toLowerCase());
        if (existing != null) return null;

        Veranstaltung v = Veranstaltung.findById(veranstaltungId);
        if (v == null) throw new IllegalArgumentException("Veranstaltung nicht gefunden.");

        user.veranstaltung = v;
        String tempPassword = UUID.randomUUID().toString();
        user.passwordHash = BcryptUtil.bcryptHash(tempPassword);
        
        user.persist();
        return user;
    }

    @Transactional
    public int importFromCsv(Path csvFilePath, Long veranstaltungId) throws Exception {
        Veranstaltung v = Veranstaltung.findById(veranstaltungId);
        if (v == null) throw new IllegalArgumentException("Veranstaltung nicht gefunden.");

        int count = 0;
        try (FileReader reader = new FileReader(csvFilePath.toFile())) {
            List<TeilnehmerCsvDto> beans = new CsvToBeanBuilder<TeilnehmerCsvDto>(reader)
                    .withType(TeilnehmerCsvDto.class)
                    .withIgnoreLeadingWhiteSpace(true)
                    .withSeparator(';')
                    .build()
                    .parse();

            for (TeilnehmerCsvDto dto : beans) {
                if (User.findByEmail(dto.email) == null) {
                    Teilnehmer nt = new Teilnehmer();
                    nt.email = dto.email.trim().toLowerCase();
                    nt.firstName = dto.firstName;
                    nt.lastName = dto.lastName;
                    nt.gruppe = dto.gruppe;
                    nt.veranstaltung = v;

                    String tempPassword = UUID.randomUUID().toString();
                    nt.passwordHash = BcryptUtil.bcryptHash(tempPassword);

                    nt.persist();
                    count++;
                }
            }
        }
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
        tn.veranstaltung = Veranstaltung.findById(veranstaltungId);
        return tn;
    }
}
