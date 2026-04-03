package kreyj.vortragsmanager.service;

import com.opencsv.bean.CsvToBeanBuilder;
import io.quarkus.elytron.security.common.BcryptUtil;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import kreyj.vortragsmanager.dto.ParticipantCsvDto;
import kreyj.vortragsmanager.entity.Teilnehmer;
import kreyj.vortragsmanager.entity.User;
import kreyj.vortragsmanager.resource.TeilnehmerResource;
import org.hibernate.dialect.FunctionalDependencyAnalysisSupport;

import java.io.FileReader;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class TeilnehmerService {

    @Transactional
    public List<User> findAll() {
        return User.list("role", "TEILNEHMER");
    }

    @Transactional
    public User findById(Long id) {
        return User.findById(id);
    }

    @Transactional
    public Teilnehmer createTeilnehmer(User user) {
        if (user == null || user.email == null) return null;

        User existing = User.findByEmail(user.email.trim().toLowerCase());
        if (existing != null) return null;

        String tempPassword = UUID.randomUUID().toString();
        user.passwordHash = BcryptUtil.bcryptHash(tempPassword);
        // role wird über @DiscriminatorValue automatisch gesetzt

        user.persist();
        return (Teilnehmer) user;
    }

    @Transactional
    public int importFromCsv(Path csvFilePath) throws Exception {
        int count = 0;
        try (FileReader reader = new FileReader(csvFilePath.toFile())) {
            List<ParticipantCsvDto> beans = new CsvToBeanBuilder<ParticipantCsvDto>(reader)
                    .withType(ParticipantCsvDto.class)
                    .withIgnoreLeadingWhiteSpace(true)
                    .withSeparator(';')
                    .build()
                    .parse();

            for (ParticipantCsvDto dto : beans) {
                if (User.findByEmail(dto.email) == null) {
                    Teilnehmer nt = new Teilnehmer();
                    nt.email = dto.email.trim().toLowerCase();
                    nt.firstName = dto.firstName;
                    nt.lastName = dto.lastName;
                    nt.organization = dto.organization;
                    nt.jobRole = dto.jobRole;

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

    public Teilnehmer updateTeilnehmer(Long id, Teilnehmer teilnehmer) {
        User existing = User.findById(id);
        if (existing == null || teilnehmer == null) return null;

        Teilnehmer tn = (Teilnehmer) existing;
        tn.firstName = teilnehmer.firstName;
        tn.lastName = teilnehmer.lastName;
        tn.email = teilnehmer.email == null ? existing.email : teilnehmer.email.trim().toLowerCase();
        tn.organization = teilnehmer.organization;
        tn.jobRole = teilnehmer.jobRole;
        tn.isActive = teilnehmer.isActive;
        tn.persist();
        return tn;
    }
}
