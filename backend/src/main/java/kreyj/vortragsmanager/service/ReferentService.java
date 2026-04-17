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
        Referent referent = Referent.find("email", email).firstResult();
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
            dto.willingToRepeat = wahlvortrag.wiederholbar;
            dto.maxWiederholungen = wahlvortrag.maxWiederholungen;
            // TODO: Target Audience if entity supports it
        } else if (v instanceof Pflichtvortrag pflichtvortrag) {
            dto.pflichtgruppe = pflichtvortrag.pflichtgruppe;
        }

        // Availabilities (aus der Verfuegbarkeit-Tabelle für den Referenten)
        // Hier müsste die Logik ggf. verfeinert werden, wenn Verfügbarkeiten pro Vortrag gespeichert werden sollen.
        // Aktuell scheint es global pro Referent zu sein.
        List<Verfuegbarkeit> availabilities = Verfuegbarkeit.find("referent", v.referent).list();
        dto.availabilities = availabilities.stream()
                .filter(a -> a.isAvailable)
                .map(a -> a.slot.id)
                .collect(Collectors.toList());

        return dto;
    }

    @Transactional
    public RefVortragDto createVortrag(String email, RefVortragDto dto) {
        Referent referent = Referent.find("email", email).firstResult();
        if (referent == null) return null;

        // Standardmäßig als Wahlvortrag erstellen (kann je nach Anforderung angepasst werden)
        Wahlvortrag vortrag = new Wahlvortrag();
        vortrag.referent = referent;
        vortrag.veranstaltung = referent.veranstaltung;
        updateVortragFromDto(vortrag, dto);
        vortrag.persist();

        // Verfügbarkeiten speichern
        updateAvailabilities(referent, dto.availabilities);

        return mapToDto(vortrag);
    }

    @Transactional
    public RefVortragDto updateVortrag(String email, Long vortragId, RefVortragDto dto) {
        Referent referent = Referent.find("email", email).firstResult();
        Vortrag vortrag = Vortrag.findById(vortragId);

        if (vortrag == null || !vortrag.referent.id.equals(referent.id)) return null;

        updateVortragFromDto(vortrag, dto);
        
        // Verfügbarkeiten speichern
        updateAvailabilities(referent, dto.availabilities);

        return mapToDto(vortrag);
    }

    private void updateVortragFromDto(Vortrag vortrag, RefVortragDto dto) {
        vortrag.titel = dto.title;
        vortrag.inhalt = dto.abstractText;

        if (vortrag instanceof Wahlvortrag wahlvortrag) {
            wahlvortrag.wiederholbar = dto.willingToRepeat;
            if (dto.maxWiederholungen > 0) {
                wahlvortrag.maxWiederholungen = dto.maxWiederholungen;
            }
        } else if (vortrag instanceof Pflichtvortrag pflichtvortrag) {
            pflichtvortrag.pflichtgruppe = dto.pflichtgruppe;
        }
    }

    private void updateAvailabilities(Referent referent, List<Long> slotIds) {
        if (slotIds == null) return;

        // Zuerst alle bestehenden Verfügbarkeiten auf false setzen (oder löschen)
        Verfuegbarkeit.update("isAvailable = false where referent = ?1", referent);

        // Dann die übergebenen auf true setzen oder neu anlegen
        for (Long slotId : slotIds) {
            EventSlot slot = EventSlot.findById(slotId);
            if (slot == null) continue;

            Verfuegbarkeit v = Verfuegbarkeit.find("referent = ?1 and slot = ?2", referent, slot).firstResult();
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
        Referent referent = Referent.find("email", email).firstResult();
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

                    String tempPassword = "start123";
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
            verfuegbarkeit.user = referent;
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
