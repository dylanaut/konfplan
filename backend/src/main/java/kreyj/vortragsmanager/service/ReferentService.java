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
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

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

    public List<RefVortragDto> getMeineVortraege(String email) {
        Referent referent = (Referent) User.findByEmail(email);
        if (referent == null) return new ArrayList<>();

        List<Vortrag> vortraege = Vortrag.find("referent", referent).list();
        return vortraege.stream().map(this::mapToDto).collect(Collectors.toList());
    }

    private RefVortragDto mapToDto(Vortrag v) {
        RefVortragDto dto = new RefVortragDto();
        dto.id = v.id;
        dto.version = v.version;
        dto.title = v.titel;
        dto.abstractText = v.inhalt;
        
        if (v instanceof Wahlvortrag wahlvortrag) {
            dto.wiederholbar = wahlvortrag.wiederholbar;
            dto.maxWiederholungen = wahlvortrag.maxWiederholungen;
        } else if (v instanceof Pflichtvortrag pflichtvortrag) {
            dto.pflichtgruppe = pflichtvortrag.pflichtgruppe;
        }

        // Availabilities (aus der Verfuegbarkeit-Tabelle für den Referenten)
        List<Verfuegbarkeit> availabilities = Verfuegbarkeit.find("user", v.referent).list();
        dto.availabilities = availabilities.stream()
                .filter(a -> a.isAvailable)
                .map(a -> a.slot.id)
                .collect(Collectors.toList());

        return dto;
    }

    @Transactional
    public RefVortragDto createVortrag(String email, RefVortragDto dto) {
        Referent referent = (Referent) User.findByEmail(email);
        if (referent == null) return null;

        Wahlvortrag vortrag = new Wahlvortrag();
        vortrag.referent = referent;
        
        // Da ein Referent nun in mehreren Veranstaltungen sein kann,
        // müssen wir die Veranstaltung für den Vortrag explizit festlegen.
        // Für den Übergang nehmen wir die erste verfügbare oder eine aus dem DTO.
        if (!referent.veranstaltungen.isEmpty()) {
            vortrag.veranstaltung = referent.veranstaltungen.iterator().next();
        }

        updateVortragFromDto(vortrag, dto);
        vortrag.persist();

        updateAvailabilities(referent, dto.availabilities);

        return mapToDto(vortrag);
    }

    @Transactional
    public RefVortragDto updateVortrag(String email, Long vortragId, RefVortragDto dto) {
        Referent referent = (Referent) User.findByEmail(email);
        Vortrag vortrag = Vortrag.findById(vortragId);

        if (vortrag == null || !vortrag.referent.id.equals(referent.id)) return null;

        updateVortragFromDto(vortrag, dto);
        updateAvailabilities(referent, dto.availabilities);

        return mapToDto(vortrag);
    }

    private void updateVortragFromDto(Vortrag vortrag, RefVortragDto dto) {
        vortrag.titel = dto.title;
        vortrag.inhalt = dto.abstractText;

        if (vortrag instanceof Wahlvortrag wahlvortrag) {
            wahlvortrag.wiederholbar = dto.wiederholbar;
            if (dto.maxWiederholungen > 0) {
                wahlvortrag.maxWiederholungen = dto.maxWiederholungen;
            }
        } else if (vortrag instanceof Pflichtvortrag pflichtvortrag) {
            pflichtvortrag.pflichtgruppe = dto.pflichtgruppe;
        }
    }

    private void updateAvailabilities(Referent referent, List<Long> slotIds) {
        if (slotIds == null) return;

        Verfuegbarkeit.update("isAvailable = false where user = ?1", referent);

        for (Long slotId : slotIds) {
            EventSlot slot = EventSlot.findById(slotId);
            if (slot == null) continue;

            Verfuegbarkeit v = Verfuegbarkeit.find("user = ?1 and slot = ?2", referent, slot).firstResult();
            if (v == null) {
                v = new Verfuegbarkeit();
                v.user = referent;
                v.slot = slot;
            }
            v.isAvailable = true;
            v.persist();
        }
    }

    @Transactional
    public boolean deleteVortrag(String email, Long vortragId) {
        Referent referent = (Referent) User.findByEmail(email);
        Vortrag vortrag = Vortrag.findById(vortragId);

        if (vortrag == null || !vortrag.referent.id.equals(referent.id)) return false;

        vortrag.delete();
        return true;
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
                User existing = User.findByEmail(dto.email);
                Referent ref;
                if (existing == null) {
                    ref = new Referent();
                    ref.email = dto.email.trim().toLowerCase();
                    ref.passwordHash = BcryptUtil.bcryptHash("start123");
                    ref.persist();
                } else if (existing instanceof Referent) {
                    ref = (Referent) existing;
                } else continue;

                ref.firstName = dto.firstName;
                ref.lastName = dto.lastName;
                ref.jobRole = dto.jobRole;
                ref.organisation = dto.organisation;
                ref.slogan = dto.slogan;
                ref.biography = dto.biography;
                ref.addVeranstaltung(veranstaltung);

                count++;
            }
        }
        return count;
    }
}
