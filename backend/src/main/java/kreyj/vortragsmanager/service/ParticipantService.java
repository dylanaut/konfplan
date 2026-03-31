package kreyj.vortragsmanager.service;

import com.opencsv.bean.CsvToBeanBuilder;
import io.quarkus.elytron.security.common.BcryptUtil;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import kreyj.vortragsmanager.dto.ParticipantCsvDto;
import kreyj.vortragsmanager.entity.User;

import java.io.FileReader;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class ParticipantService {

    @Transactional
    public List<User> findAll() {
        return User.list("role", "PARTICIPANT");
    }

    @Transactional
    public User findById(Long id) {
        return User.findById(id);
    }


    @Transactional
    public User createParticipant(User user) {
        if (user == null || user.email == null) {
            return null;
        }

        User existing = User.findByEmail(user.email.trim().toLowerCase());
        if (existing != null) {
            return null;
        }

        User newUser = new User();
        newUser.firstName = user.firstName;
        newUser.lastName = user.lastName;
        newUser.email = user.email.trim().toLowerCase();
        newUser.organization = user.organization;
        newUser.jobRole = user.jobRole;
        newUser.role = "PARTICIPANT";
        newUser.isActive = user.isActive;

        String tempPassword = UUID.randomUUID().toString();
        newUser.passwordHash = BcryptUtil.bcryptHash(tempPassword);

        newUser.persist();
        return newUser;
    }

    @Transactional
    public User updateParticipant(Long id, User user) {
        User existing = User.findById(id);
        if (existing == null || user == null) {
            return null;
        }

        existing.firstName = user.firstName;
        existing.lastName = user.lastName;
        existing.email = user.email == null ? existing.email : user.email.trim().toLowerCase();
        existing.organization = user.organization;
        existing.jobRole = user.jobRole;
        existing.isActive = user.isActive;
        existing.persist();

        return existing;
    }

    @Transactional
    public void deleteUser(User user) {
        user.delete();
    }

    @Transactional
    public int importFromCsv(Path csvFilePath) throws Exception {
        int count = 0;

        try (FileReader reader = new FileReader(csvFilePath.toFile())) {
            // OpenCSV Mapping
            List<ParticipantCsvDto> beans = new CsvToBeanBuilder<ParticipantCsvDto>(reader)
                    .withType(ParticipantCsvDto.class)
                    .withIgnoreLeadingWhiteSpace(true)
                    .withSeparator(';') // Falls Ihre CSV Semikolons nutzt
                    .build()
                    .parse();

            for (ParticipantCsvDto dto : beans) {
                // Dubletten-Check: Existiert die Email schon?
                if (User.findByEmail(dto.email) == null) {
                    User newUser = new User();
                    newUser.email = dto.email.trim().toLowerCase();
                    newUser.firstName = dto.firstName;
                    newUser.lastName = dto.lastName;
                    newUser.organization = dto.organization;
                    newUser.jobRole = dto.jobRole;
                    newUser.role = "PARTICIPANT";
                    newUser.isActive = true;

                    // Sicheres Zufallspasswort generieren
                    // Der User wird dieses später über "Passwort vergessen" ändern
                    String tempPassword = UUID.randomUUID().toString();
                    newUser.passwordHash = BcryptUtil.bcryptHash(tempPassword);

                    newUser.persist();
                    count++;
                }
            }
        }
        return count;
    }

    @Transactional
    public void toggleActive(User user) {
        user.isActive = !user.isActive;
        user.persist();
    }
}