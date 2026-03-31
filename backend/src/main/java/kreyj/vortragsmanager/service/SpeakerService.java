package kreyj.vortragsmanager.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import kreyj.vortragsmanager.dto.SpeakerProfileDto;
import kreyj.vortragsmanager.dto.SpeakerTalkDto;
import kreyj.vortragsmanager.entity.EventSlot;
import kreyj.vortragsmanager.entity.SpeakerAvailability;
import kreyj.vortragsmanager.entity.Talk;
import kreyj.vortragsmanager.entity.User;

import java.time.LocalDate;
import java.util.List;

@ApplicationScoped
public class SpeakerService {

    @Transactional
    public void updateProfile(String email, SpeakerProfileDto dto) {
        User speaker = User.findByEmail(email);
        speaker.organization = dto.organization;
        speaker.jobRole = dto.jobRole;
        // Email-Änderung ist kritisch wegen Login, hier ggf. Validierung einbauen
        speaker.email = dto.email; 
    }

    @Transactional
    public void updateTalk(String email, SpeakerTalkDto dto) {
        User speaker = User.findByEmail(email);
        Talk talk = Talk.find("speaker", speaker).firstResult();
        if (talk != null) {
            talk.title = dto.title;
            talk.abstractText = dto.abstractText;
            talk.targetAudience = dto.targetAudience;
            talk.readyToRepeat = dto.readyToRepeat;
            talk.maxRepetitions = dto.maxRepetitions;
        }
    }

    @Transactional
    public void toggleSlot(String email, Long slotId, boolean available) {
        User speaker = User.findByEmail(email);
        EventSlot slot = EventSlot.findById(slotId);
        
        SpeakerAvailability availability = SpeakerAvailability
            .find("speaker = ?1 and slot = ?2", speaker, slot).firstResult();

        if (availability == null) {
            availability = new SpeakerAvailability();
            availability.speaker = speaker;
            availability.slot = slot;
        }
        availability.isAvailable = available;
        availability.persist();
    }

    @Transactional
    public void toggleEntireDay(String email, LocalDate date, boolean available) {
        User speaker = User.findByEmail(email);
        // Alle Slots finden, die an diesem Tag liegen
        List<EventSlot> dailySlots = EventSlot.list("cast(startTime as date) = ?1", date);
        
        for (EventSlot slot : dailySlots) {
            toggleSlot(email, slot.id, available);
        }
    }
}