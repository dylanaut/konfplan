package kreyj.vortragsmanager.service;

import com.opencsv.bean.CsvToBeanBuilder;
import io.quarkus.elytron.security.common.BcryptUtil;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import kreyj.vortragsmanager.dto.RefProfilDto;
import kreyj.vortragsmanager.dto.RefVortragDto;
import kreyj.vortragsmanager.dto.csv.ReferentCsvDto;
import kreyj.vortragsmanager.entity.*;

import java.io.FileReader;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class ReferentService {

    public Referent getProfile(String email) {
        User user = User.findByEmail(email);
        if (user instanceof Referent) {
            return (Referent) user;
        }
        return null;
    }

    @Transactional
    public void updateProfile(String email, RefProfilDto dto) {
        User user = User.findByEmail(email);
        if (user instanceof Referent) {
            Referent referent = (Referent) user;
            referent.biography = dto.biography;
            referent.jobRole = dto.jobRole;
            referent.organisation = dto.organisation;
            referent.slogan = dto.slogan;
            referent.firstName = dto.firstName;
            referent.lastName = dto.lastName;
            referent.email = dto.email;
        }
    }

    public Vortrag getVortrag(String email) {
        Referent referent = Referent.find("email", email).firstResult();
        if (referent != null) {
            return Vortrag.find("referent", referent).firstResult();
        }
        return null;
    }

    @Transactional
    public void updateVortrag(String email, RefVortragDto dto) {
        Referent referent = Referent.find("email", email).firstResult();
        Vortrag vortrag = Vortrag.find("referent", referent).firstResult();
        if (vortrag != null) {
            vortrag.titel = dto.titel;
            vortrag.inhalt = dto.inhalt;

            if (vortrag instanceof Wahlvortrag wahlvortrag) {
                wahlvortrag.wiederholbar = dto.wiederholbar;
                wahlvortrag.maxWiederholungen = dto.maxWiederholungen;
                // TBD check wahlSlots
            } else if (vortrag instanceof Pflichtvortrag pflichtvortrag) {
                pflichtvortrag.pflichtgruppe = dto.pflichtgruppe;
                // TBD check pflichtSlot, pflichtRaum
            }
        }
    }

    @Transactional
    public int importFromCsv(Path csvFilePath, Long veranstaltungId) throws Exception {
        int count = 0;
        Veranstaltung veranstaltung = Veranstaltung.findById(veranstaltungId);
        if (veranstaltung == null) throw new IllegalArgumentException("Veranstaltung nicht gefunden.");

        try (FileReader reader = new FileReader(csvFilePath.toFile())) {
            List<ReferentCsvDto> beans = new CsvToBeanBuilder<ReferentCsvDto>(reader)
                    .withType(ReferentCsvDto.class)
                    .withSeparator(';')
                    .withIgnoreLeadingWhiteSpace(true)
                    .build()
                    .parse();

            for (ReferentCsvDto dto : beans) {
                if (User.findByEmail(dto.email) == null) {
                    Referent nr = new Referent();
                    nr.email = dto.email.trim().toLowerCase();
                    nr.firstName = dto.firstName;
                    nr.lastName = dto.lastName;
                    nr.jobRole = dto.jobRole;
                    nr.organisation = dto.organisation;
                    nr.slogan = dto.slogan;
                    nr.biography = dto.biography;
                    nr.veranstaltung = veranstaltung;

                    String tempPassword = "start123"; // UUID.randomUUID().toString();
                    nr.passwordHash = BcryptUtil.bcryptHash(tempPassword);

                    nr.persist();
                    count++;
                }
            }
        }
        return count;
    }

    @Transactional
    public void toggleSlot(String email, Long slotId, boolean available) {
        Referent referent = Referent.find("email", email).firstResult();
        EventSlot slot = EventSlot.findById(slotId);

        Verfuegbarkeit verfuegbarkeit = Verfuegbarkeit
                .find("referent = ?1 and slot = ?2", referent, slot).firstResult();

        if (verfuegbarkeit == null) {
            verfuegbarkeit = new Verfuegbarkeit();
            verfuegbarkeit.referent = referent;
            verfuegbarkeit.slot = slot;
        }
        verfuegbarkeit.isAvailable = available;
        verfuegbarkeit.persist();
    }

    @Transactional
    public void toggleEntireDay(String email, LocalDate date, boolean available) {
        List<EventSlot> dailySlots = EventSlot.list("date(startTime) = ?1", date);
        for (EventSlot slot : dailySlots) {
            toggleSlot(email, slot.id, available);
        }
    }
}
