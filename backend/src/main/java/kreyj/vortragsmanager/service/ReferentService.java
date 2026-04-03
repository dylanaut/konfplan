package kreyj.vortragsmanager.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import kreyj.vortragsmanager.dto.RefProfilDto;
import kreyj.vortragsmanager.dto.RefVortragDto;
import kreyj.vortragsmanager.entity.EventSlot;
import kreyj.vortragsmanager.entity.Verfuegbarkeit;
import kreyj.vortragsmanager.entity.Vortrag;
import kreyj.vortragsmanager.entity.User;
import kreyj.vortragsmanager.entity.Referent;

import java.time.LocalDate;
import java.util.List;

@ApplicationScoped
public class ReferentService {

    @Transactional
    public void updateProfile(String email, RefProfilDto dto) {
        User referent = User.findByEmail(email);
        if (referent instanceof Referent) {
            // Referent spezifische Felder aktualisieren
            ((Referent) referent).biography = dto.biography;
        }
        referent.firstName = dto.firstName;
        referent.lastName = dto.lastName;
        referent.email = dto.email; 
    }

    @Transactional
    public void updateVortrag(String email, RefVortragDto dto) {
        Referent referent = Referent.find("email", email).firstResult();
        Vortrag vortrag = Vortrag.find("referent", referent).firstResult();
        if (vortrag != null) {
            vortrag.title = dto.title;
            vortrag.abstractText = dto.abstractText;
            vortrag.targetAudience = dto.targetAudience;
            vortrag.readyToRepeat = dto.readyToRepeat;
            vortrag.maxRepetitions = dto.maxRepetitions;
        }
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
        // Alle Slots finden, die an diesem Tag liegen
        List<EventSlot> dailySlots = EventSlot.list("date(startTime) = ?1", date);
        
        for (EventSlot slot : dailySlots) {
            toggleSlot(email, slot.id, available);
        }
    }
}
