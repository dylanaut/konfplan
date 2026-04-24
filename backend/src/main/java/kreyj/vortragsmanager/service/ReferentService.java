package kreyj.vortragsmanager.service;

import com.opencsv.bean.CsvToBeanBuilder;
import io.quarkus.elytron.security.common.BcryptUtil;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import kreyj.vortragsmanager.dto.RefProfilDto;
import kreyj.vortragsmanager.dto.RefVortragDto;
import kreyj.vortragsmanager.dto.ReferentVeranstaltungDto;
import kreyj.vortragsmanager.dto.csv.ReferentCsvDto;
import kreyj.vortragsmanager.entity.*;
import org.jboss.logging.Logger;

import java.io.FileReader;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@ApplicationScoped
public class ReferentService {
    private static final Logger LOG = Logger.getLogger(ReferentService.class);

    @Inject
    MailService mailService;

    public Referent getProfile(String email) {
        Nutzer nutzer = Nutzer.findByEmail(email);
        if (nutzer instanceof Referent) {
            return (Referent) nutzer;
        }
        return null;
    }

    @Transactional
    public void updateProfile(String email, RefProfilDto dto) {
        Nutzer nutzer = Nutzer.findByEmail(email);
        if (nutzer instanceof Referent) {
            Referent referent = (Referent) nutzer;
            referent.biography = dto.biography;
            referent.jobRole = dto.jobRole;
            referent.organisation = dto.organisation;
            referent.slogan = dto.slogan;
            referent.firstName = dto.firstName;
            referent.lastName = dto.lastName;
            referent.email = dto.email;
        }
    }

    public List<RefVortragDto> getReferentVortraege(String email) {
        Referent referent = Referent.find("email", email).firstResult();
        if (referent == null) return new ArrayList<>();

        List<Vortrag> vortraege = Vortrag.find("referent", referent).list();
        return vortraege.stream().map(this::mapToDto).collect(Collectors.toList());
    }

    public List<ReferentVeranstaltungDto> getReferentVeranstaltungen(String email) {
        Referent referent = Referent.find("email", email).firstResult();
        if (referent == null) return new ArrayList<>();

        // Alle Veranstaltungen, bei denen der Referent gelistet ist
        Set<Veranstaltung> events = new HashSet<>(referent.veranstaltungen);

        // Und alle Veranstaltungen, für die er bereits einen Vortrag hat
        List<Vortrag> vortraege = Vortrag.find("referent", referent).list();
        vortraege.stream().map(t -> t.veranstaltung).forEach(events::add);

        return events.stream().map(e -> {
            ReferentVeranstaltungDto dto = new ReferentVeranstaltungDto();
            dto.id = e.id;
            dto.name = e.name;
            dto.beginntAm = e.beginntAm;
            dto.endetAm = e.endetAm;
            dto.deadlineReferenten = e.deadlineReferenten;
            dto.registeredTalkIds = vortraege.stream()
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
        dto.titel = v.titel;
        dto.abstractText = v.inhalt;
        dto.veranstaltungId = v.veranstaltung.id;
        dto.veranstaltungName = v.veranstaltung.name;

        if (v instanceof Wahlvortrag wahlvortrag) {
            dto.wiederholbar = wahlvortrag.wiederholbar;
            dto.maxWiederholungen = wahlvortrag.maxWiederholungen;
            dto.verfuegIds = wahlvortrag.wahlSlots.stream()
                    .map(s -> s.id)
                    .collect(Collectors.toList());
        } else if (v instanceof Pflichtvortrag pflichtvortrag) {
            dto.pflichtgruppe = pflichtvortrag.pflichtgruppe;
            if (pflichtvortrag.pflichtslot != null) {
                dto.verfuegIds = List.of(pflichtvortrag.pflichtslot.id);
            }
        }

        return dto;
    }

    @Transactional
    public RefVortragDto createVortrag(String email, RefVortragDto dto) {
        Referent referent = Referent.find("email", email).firstResult();
        if (referent == null) return null;

        Veranstaltung veranstaltung = Veranstaltung.findById(dto.veranstaltungId);
        if (veranstaltung == null) throw new IllegalArgumentException("Veranstaltung nicht gefunden.");

        checkDeadline(veranstaltung);

        Wahlvortrag vortrag = new Wahlvortrag();
        vortrag.referent = referent;
        vortrag.veranstaltung = veranstaltung;
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

        checkDeadline(vortrag.veranstaltung);

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

        checkDeadline(targetEvent);

        // Prüfen, ob bereits ein Vortrag mit diesem Titel in der Zielveranstaltung existiert
        boolean exists = Vortrag.find("referent = ?1 and veranstaltung = ?2 and titel = ?3", referent, targetEvent, sourceTalk.titel).count() > 0;
        if (exists) return;

        Vortrag newTalk;
        if (sourceTalk instanceof Wahlvortrag sw) {
            Wahlvortrag nw = new Wahlvortrag();
            nw.wiederholbar = sw.wiederholbar;
            nw.maxWiederholungen = sw.maxWiederholungen;
            // Wir übernehmen keine Slots, da diese veranstaltungsspezifisch sind!
            newTalk = nw;
        } else {
            Pflichtvortrag np = new Pflichtvortrag();
            np.pflichtgruppe = ((Pflichtvortrag) sourceTalk).pflichtgruppe;
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

        checkDeadline(event);

        talk.delete();

        if (event.beginntAm.isAfter(LocalDateTime.now())) {
            mailService.sendTalkRegistrationNotification(event, referent, talk, false);
        }
    }

    private void updateVortragFromDto(Vortrag vortrag, RefVortragDto dto) {
        vortrag.titel = dto.titel;
        vortrag.inhalt = dto.abstractText;

        if (vortrag instanceof Wahlvortrag wahlvortrag) {
            wahlvortrag.wiederholbar = dto.wiederholbar;
            if (dto.maxWiederholungen > 0) {
                wahlvortrag.maxWiederholungen = dto.maxWiederholungen;
            }
            if (dto.verfuegIds != null) {
                wahlvortrag.wahlSlots.clear();
                for (Long sid : dto.verfuegIds) {
                    EventSlot slot = EventSlot.findById(sid);
                    // Validierung: Slot muss zur Veranstaltung des Vortrags gehören
                    if (slot != null && slot.veranstaltung.id.equals(vortrag.veranstaltung.id)) {
                        wahlvortrag.wahlSlots.add(slot);
                    }
                }
            }
        } else if (vortrag instanceof Pflichtvortrag pflichtvortrag) {
            pflichtvortrag.pflichtgruppe = dto.pflichtgruppe;
            if (dto.verfuegIds != null && !dto.verfuegIds.isEmpty()) {
                EventSlot slot = EventSlot.findById(dto.verfuegIds.get(0));
                if (slot != null && slot.veranstaltung.id.equals(vortrag.veranstaltung.id)) {
                    pflichtvortrag.pflichtslot = slot;
                }
            }
        }
    }

    @Transactional
    public boolean deleteVortrag(String email, Long vortragId) {
        Referent referent = Referent.find("email", email).firstResult();
        Vortrag vortrag = Vortrag.findById(vortragId);

        if (vortrag == null || !vortrag.referent.id.equals(referent.id)) return false;

        checkDeadline(vortrag.veranstaltung);

        Veranstaltung event = vortrag.veranstaltung;
        vortrag.delete();

        if (event.beginntAm.isAfter(LocalDateTime.now())) {
            mailService.sendTalkRegistrationNotification(event, referent, vortrag, false);
        }

        return true;
    }

    private void checkDeadline(Veranstaltung v) {
        if (v.deadlineReferenten != null && v.deadlineReferenten.isBefore(LocalDateTime.now())) {
            throw new WebApplicationException("Die Deadline für Referenten für diese Veranstaltung ist bereits abgelaufen.",
                    Response.Status.FORBIDDEN);
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
                Nutzer existingNutzer = Nutzer.findByEmail(dto.email);
                Referent ref;
                if (existingNutzer == null) {
                    ref = new Referent();
                    ref.email = dto.email.trim().toLowerCase();
                    String tempPassword = "start123";
                    ref.passwordHash = BcryptUtil.bcryptHash(tempPassword);
                    ref.persist();
                } else if (existingNutzer instanceof Referent) {
                    ref = (Referent) existingNutzer;
                } else {
                    LOG.warn("Nutzer mit Email " + dto.email + " existiert bereits, ist aber kein Referent. Überspringe.");
                    continue;
                }

                ref.firstName = dto.firstName;
                ref.lastName = dto.lastName;
                ref.jobRole = dto.jobRole;
                ref.organisation = dto.organisation;
                ref.slogan = dto.slogan;
                ref.biography = dto.biography;
                if (!ref.veranstaltungen.contains(veranstaltung)) {
                    ref.veranstaltungen.add(veranstaltung);
                }

                count++;
            }
        }
        return count;
    }
}
