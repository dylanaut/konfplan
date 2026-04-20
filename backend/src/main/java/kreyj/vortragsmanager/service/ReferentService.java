package kreyj.vortragsmanager.service;

import com.opencsv.bean.CsvToBeanBuilder;
import io.quarkus.elytron.security.common.BcryptUtil;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import kreyj.vortragsmanager.dto.RefProfilDto;
import kreyj.vortragsmanager.dto.RefVortragDto;
import kreyj.vortragsmanager.dto.ReferentVeranstaltungDto;
import kreyj.vortragsmanager.dto.csv.ReferentCsvDto;
import kreyj.vortragsmanager.entity.*;
import org.jboss.logging.Logger;

import java.io.FileReader;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@ApplicationScoped
public class ReferentService {
    private static final Logger LOG = Logger.getLogger(ReferentService.class);

    @Inject
    MailService mailService;

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

    public List<ReferentVeranstaltungDto> getEventsForRegistration(String email) {
        Referent referent = Referent.find("email", email).firstResult();
        if (referent == null) return new ArrayList<>();

        Set<Veranstaltung> events = new HashSet<>(referent.veranstaltungen); // All events the user is associated with

        List<Vortrag> talks = Vortrag.find("referent", referent).list();
        talks.stream().map(t -> t.veranstaltung).forEach(events::add); // Add events from talks

        return events.stream().map(e -> {
            ReferentVeranstaltungDto dto = new ReferentVeranstaltungDto();
            dto.id = e.id;
            dto.name = e.name;
            dto.beginntAm = e.beginntAm;
            dto.endetAm = e.endetAm;
            dto.registeredTalkIds = talks.stream()
                    .filter(t -> t.veranstaltung.id.equals(e.id))
                    .map(t -> t.id)
                    .collect(Collectors.toList());
            return dto;
        }).sorted(Comparator.comparing(e -> e.beginntAm)).collect(Collectors.toList());
    }

    private RefVortragDto mapToDto(Vortrag v) {
        RefVortragDto dto = new RefVortragDto();
        dto.id = v.id;
        dto.version = v.version;
        dto.title = v.titel;
        dto.abstractText = v.inhalt;
        dto.veranstaltungId = v.veranstaltung.id;
        dto.veranstaltungName = v.veranstaltung.name;
        
        if (v instanceof Wahlvortrag wahlvortrag) {
            dto.wiederholbar = wahlvortrag.wiederholbar;
            dto.maxWiederholungen = wahlvortrag.maxWiederholungen;
            dto.availabilities = wahlvortrag.wahlSlots.stream()
                    .map(s -> s.id)
                    .collect(Collectors.toList());
        } else if (v instanceof Pflichtvortrag pflichtvortrag) {
            dto.pflichtgruppe = pflichtvortrag.pflichtgruppe;
            if (pflichtvortrag.pflichtslot != null) {
                dto.availabilities = List.of(pflichtvortrag.pflichtslot.id);
            }
        }

        return dto;
    }

    @Transactional
    public RefVortragDto createVortrag(String email, RefVortragDto dto) {
        Referent referent = Referent.find("email", email).firstResult();
        if (referent == null) return null;

        Veranstaltung veranstaltung = Veranstaltung.findById(dto.veranstaltungId); // Use DTO's event ID
        if (veranstaltung == null) throw new IllegalArgumentException("Veranstaltung nicht gefunden.");

        Wahlvortrag vortrag = new Wahlvortrag();
        vortrag.referent = referent;
        vortrag.veranstaltung = veranstaltung; // Assign the event
        updateVortragFromDto(vortrag, dto);
        vortrag.persist();

        if (vortrag.veranstaltung.beginntAm.isAfter(LocalDateTime.now())) {
            mailService.sendTalkRegistrationNotification(vortrag.veranstaltung, referent, vortrag, true);
        }

        return mapToDto(vortrag);
    }

    @Transactional
    public RefVortragDto updateVortrag(String email, Long vortragId, RefVortragDto dto) {
        Referent referent = Referent.find("email", email).firstResult();
        Vortrag vortrag = Vortrag.findById(vortragId);

        if (vortrag == null || !vortrag.referent.id.equals(referent.id)) return null;

        updateVortragFromDto(vortrag, dto);
        
        return mapToDto(vortrag);
    }

    @Transactional
    public void registerTalkForEvent(String email, Long talkId, Long eventId) {
        Referent referent = Referent.find("email", email).firstResult();
        Vortrag sourceTalk = Vortrag.findById(talkId);
        Veranstaltung targetEvent = Veranstaltung.findById(eventId);

        if (referent == null || sourceTalk == null || targetEvent == null) return;
        if (!sourceTalk.referent.id.equals(referent.id)) return;

        boolean exists = Vortrag.find("referent = ?1 and veranstaltung = ?2 and titel = ?3", referent, targetEvent, sourceTalk.titel).count() > 0;
        if (exists) return;

        Vortrag newTalk;
        if (sourceTalk instanceof Wahlvortrag sw) {
            Wahlvortrag nw = new Wahlvortrag();
            nw.wiederholbar = sw.wiederholbar;
            nw.maxWiederholungen = sw.maxWiederholungen;
            nw.wahlSlots = new ArrayList<>(sw.wahlSlots);
            newTalk = nw;
        } else {
            Pflichtvortrag np = new Pflichtvortrag();
            np.pflichtgruppe = ((Pflichtvortrag) sourceTalk).pflichtgruppe;
            np.pflichtslot = ((Pflichtvortrag) sourceTalk).pflichtslot;
            np.pflichtraum = ((Pflichtvortrag) sourceTalk).pflichtraum;
            newTalk = np;
        }

        newTalk.titel = sourceTalk.titel;
        newTalk.inhalt = sourceTalk.inhalt;
        newTalk.referent = referent;
        newTalk.veranstaltung = targetEvent;
        newTalk.persist();

        if (targetEvent.beginntAm.isAfter(LocalDateTime.now())) {
            mailService.sendTalkRegistrationNotification(targetEvent, referent, newTalk, true);
        }
    }

    @Transactional
    public void deregisterTalkFromEvent(String email, Long talkId, Long eventId) {
        Referent referent = Referent.find("email", email).firstResult();
        Vortrag talk = Vortrag.findById(talkId);
        Veranstaltung event = Veranstaltung.findById(eventId);

        if (referent == null || talk == null || event == null) return;
        if (!talk.referent.id.equals(referent.id) || !talk.veranstaltung.id.equals(event.id)) return;

        talk.delete();

        if (event.beginntAm.isAfter(LocalDateTime.now())) {
            mailService.sendTalkRegistrationNotification(event, referent, talk, false);
        }
    }

    private void updateVortragFromDto(Vortrag vortrag, RefVortragDto dto) {
        vortrag.titel = dto.title;
        vortrag.inhalt = dto.abstractText;

        if (vortrag instanceof Wahlvortrag wahlvortrag) {
            wahlvortrag.wiederholbar = dto.wiederholbar;
            if (dto.maxWiederholungen > 0) {
                wahlvortrag.maxWiederholungen = dto.maxWiederholungen;
            }
            if (dto.availabilities != null) {
                wahlvortrag.wahlSlots.clear();
                for (Long sid : dto.availabilities) {
                    EventSlot slot = EventSlot.findById(sid);
                    if (slot != null) wahlvortrag.wahlSlots.add(slot);
                }
            }
        } else if (vortrag instanceof Pflichtvortrag pflichtvortrag) {
            pflichtvortrag.pflichtgruppe = dto.pflichtgruppe;
            if (dto.availabilities != null && !dto.availabilities.isEmpty()) {
                pflichtvortrag.pflichtslot = EventSlot.findById(dto.availabilities.get(0));
            }
        }
    }

    @Transactional
    public boolean deleteVortrag(String email, Long vortragId) {
        Referent referent = Referent.find("email", email).firstResult();
        Vortrag vortrag = Vortrag.findById(vortragId);

        if (vortrag == null || !vortrag.referent.id.equals(referent.id)) return false;

        Veranstaltung event = vortrag.veranstaltung;
        vortrag.delete();

        if (event.beginntAm.isAfter(LocalDateTime.now())) {
            mailService.sendTalkRegistrationNotification(event, referent, vortrag, false);
        }

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
                User existingUser = User.findByEmail(dto.email);
                Referent referent;
                if (existingUser == null) {
                    referent = new Referent();
                    referent.email = dto.email.trim().toLowerCase();
                    String tempPassword = "start123";
                    referent.passwordHash = BcryptUtil.bcryptHash(tempPassword);
                    referent.persist(); // Persist first to get an ID
                } else if (existingUser instanceof Referent) {
                    referent = (Referent) existingUser;
                } else {
                    LOG.warn("User with email " + dto.email + " already exists but is not a Referent. Skipping.");
                    continue;
                }

                referent.firstName = dto.firstName;
                referent.lastName = dto.lastName;
                referent.jobRole = dto.jobRole;
                referent.organisation = dto.organisation;
                referent.slogan = dto.slogan;
                referent.biography = dto.biography;
                referent.addVeranstaltung(veranstaltung);

                count++;
            }
        }
        return count;
    }

    @Transactional
    public void toggleSlot(String email, Long slotId, boolean available) {
        Referent referent = Referent.find("email", email).firstResult();
        EventSlot slot = EventSlot.findById(slotId);

        Verfuegbarkeit verfuegbarkeit = Verfuegbarkeit
                .find("user = ?1 and slot = ?2", referent, slot).firstResult();

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
