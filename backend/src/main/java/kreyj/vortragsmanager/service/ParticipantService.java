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
}