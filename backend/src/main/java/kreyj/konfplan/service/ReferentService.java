package kreyj.konfplan.service;

import com.opencsv.bean.CsvToBeanBuilder;
import io.quarkus.elytron.security.common.BcryptUtil;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.OptimisticLockException;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import kreyj.konfplan.dto.NutzerDto;
import kreyj.konfplan.dto.ReferentVeranstaltungDto;
import kreyj.konfplan.dto.VortragDto;
import kreyj.konfplan.dto.csv.ReferentCsvDto;
import kreyj.konfplan.persistence.*;
import kreyj.konfplan.resource.ReferentResource;
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

    @Inject
    ProtokollService protokollService;

    public Referent getProfile(String email) {
        Nutzer nutzer = Nutzer.findByEmail(email);
        if (nutzer instanceof Referent) {
            return (Referent) nutzer;
        }
        return null;
    }

    @Transactional
    public void updateProfile(String email, NutzerDto dto) {
        if (null == dto) {
            return;
        }

        Nutzer nutzer = Nutzer.findByEmail(email);

        if (!Objects.equals(nutzer.version, dto.version)) {
            throw new OptimisticLockException("Der Nutzer wurde zwischenzeitlich von Dritten geändert. Bitte aktualisieren Sie die Daten und versuchen Sie es erneut.");
        }
        if (nutzer instanceof Referent referent) {
            referent.biography = dto.biography;
            referent.jobRole = dto.jobRole;
            referent.organisation = dto.organisation;
            referent.slogan = dto.slogan;
            referent.firstName = dto.firstName;
            referent.lastName = dto.lastName;
            referent.email = dto.email;
            protokollService.log(ProtokollKategorie.NUTZER, "Profil aktualisiert", "Referenten-Profil '" + email + "' aktualisiert.", referent.id);
        }
    }

    public List<VortragDto> getReferentVortraege(String email) {
        Referent referent = Referent.find("email", email).firstResult();
        if (referent == null) {
            return new ArrayList<>();
        }

        List<Vortrag> vortraege = Vortrag.find("referent", referent).list();
        return vortraege.stream().map(ReferentResource::mapVortragToDto).collect(Collectors.toList());
    }

    public List<ReferentVeranstaltungDto> getReferentVeranstaltungen(String email) {
        Referent referent = Referent.find("email", email).firstResult();
        if (referent == null) {
            return new ArrayList<>();
        }

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


    @Transactional
    public VortragDto createVortrag(String email, VortragDto dto) {
        Referent referent = Referent.find("email", email).firstResult();
        if (referent == null) {
            return null;
        }

        Veranstaltung veranstaltung = Veranstaltung.findById(dto.veranstaltungId);
        if (veranstaltung == null) {
            throw new IllegalArgumentException("Veranstaltung nicht gefunden.");
        }

        checkDeadline(veranstaltung);

        Wahlvortrag vortrag = new Wahlvortrag();
        vortrag.referent = referent;
        vortrag.veranstaltung = veranstaltung;
        updateVortragFromDto(vortrag, dto);
        vortrag.persist();

        if (vortrag.veranstaltung.beginntAm.isAfter(LocalDateTime.now())) {
            mailService.sendVortragsRegistrierung(vortrag.veranstaltung, referent, vortrag, true);
        }

        protokollService.log(ProtokollKategorie.VORTRAEGE, "Vortrag durch Referent erstellt", "Referent '" + email + "' hat Vortrag '" + vortrag.titel + "' für Event '" + veranstaltung.name + "' erstellt.", vortrag.id);
        return ReferentResource.mapVortragToDto(vortrag);
    }

    @Transactional
    public VortragDto updateVortrag(String email, Long vortragId, VortragDto dto) {
        Referent referent = Referent.find("email", email).firstResult();
        Vortrag vortrag = Vortrag.findById(vortragId);

        if (vortrag == null || !vortrag.referent.id.equals(referent.id)) {
            return null;
        }

        if (!Objects.equals(vortrag.version, dto.version)) {
            throw new OptimisticLockException("Der Vortrag wurde zwischenzeitlich von Dritten geändert. Bitte aktualisieren Sie die Daten und versuchen Sie es erneut.");
        }

        checkDeadline(vortrag.veranstaltung);

        updateVortragFromDto(vortrag, dto);
        protokollService.log(ProtokollKategorie.VORTRAEGE, "Vortrag durch Referent aktualisiert", "Referent '" + email + "' hat Vortrag '" + vortrag.titel + "' aktualisiert.", vortrag.id);
        return ReferentResource.mapVortragToDto(vortrag);
    }

    @Transactional
    public void registerTalkForEvent(String email, Long talkId, Long eventId) {
        Referent referent = Referent.find("email", email).firstResult();
        Vortrag sourceTalk = Vortrag.findById(talkId);
        Veranstaltung targetEvent = Veranstaltung.findById(eventId);

        if (referent == null || sourceTalk == null || targetEvent == null) {
            return;
        }
        if (!sourceTalk.referent.id.equals(referent.id)) {
            return;
        }

        checkDeadline(targetEvent);

        // Prüfen, ob bereits ein Vortrag mit diesem Titel in der Zielveranstaltung existiert
        boolean exists = Vortrag.find("referent = ?1 and veranstaltung = ?2 and titel = ?3", referent, targetEvent, sourceTalk.titel).count() > 0;
        if (exists) {
            return;
        }

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
            mailService.sendVortragsRegistrierung(targetEvent, referent, newTalk, true);
        }
        protokollService.log(ProtokollKategorie.VORTRAEGE, "Vortrag für weiteres Event registriert", "Referent '" + email + "' hat Vortrag '" + newTalk.titel + "' für Event '" + targetEvent.name + "' registriert.", newTalk.id);
    }

    @Transactional
    public VortragDto cloneTalkForEvent(String email, Long sourceTalkId, Long targetEventId) {
        Referent referent = Referent.find("email", email).firstResult();
        if (referent == null) {
            throw new WebApplicationException("Referent nicht gefunden.", Response.Status.NOT_FOUND);
        }

        Vortrag sourceTalk = Vortrag.findById(sourceTalkId);
        if (sourceTalk == null) {
            throw new WebApplicationException("Quell-Vortrag nicht gefunden.", Response.Status.NOT_FOUND);
        }

        Veranstaltung targetEvent = Veranstaltung.findById(targetEventId);
        if (targetEvent == null) {
            throw new WebApplicationException("Ziel-Veranstaltung nicht gefunden.", Response.Status.NOT_FOUND);
        }

        // Validierung: Gehört der Quell-Vortrag dem Referenten?
        if (!sourceTalk.referent.id.equals(referent.id)) {
            throw new WebApplicationException("Referent ist nicht der Eigentümer des Quell-Vortrags.", Response.Status.FORBIDDEN);
        }

        // Deadline für die Ziel-Veranstaltung prüfen
        checkDeadline(targetEvent);

        // Prüfen, ob bereits ein Vortrag mit demselben Titel in der Zielveranstaltung existiert
        boolean exists = Vortrag.find("referent = ?1 and veranstaltung = ?2 and titel = ?3", referent, targetEvent, sourceTalk.titel).count() > 0;
        if (exists) {
            throw new WebApplicationException("Ein Vortrag mit demselben Titel existiert bereits für diesen Referenten in der Ziel-Veranstaltung.", Response.Status.CONFLICT);
        }

        Vortrag newTalk;
        if (sourceTalk instanceof Wahlvortrag sw) {
            Wahlvortrag nw = new Wahlvortrag();
            nw.wiederholbar = sw.wiederholbar;
            nw.maxWiederholungen = sw.maxWiederholungen;
            // Wahl-Slots werden nicht kopiert, da sie veranstaltungsspezifisch sind.
            newTalk = nw;
        } else if (sourceTalk instanceof Pflichtvortrag) {
            // Pflichtvorträge können nicht von Referenten geklont werden, da sie eine Admin-Konfiguration erfordern (Slots, Räume, Gruppen).
            throw new WebApplicationException("Pflichtvorträge können nicht von Referenten geklont werden.", Response.Status.BAD_REQUEST);
        } else {
            throw new WebApplicationException("Unbekannter Vortragstyp.", Response.Status.INTERNAL_SERVER_ERROR);
        }

        newTalk.titel = sourceTalk.titel;
        newTalk.inhalt = sourceTalk.inhalt; // AbstractText wird kopiert und kann angepasst werden
        newTalk.referent = referent;
        newTalk.veranstaltung = targetEvent;
        newTalk.persist();

        // Benachrichtigung senden, wenn die Veranstaltung in der Zukunft liegt
        if (targetEvent.beginntAm.isAfter(LocalDateTime.now())) {
            mailService.sendVortragsRegistrierung(targetEvent, referent, newTalk, true);
        }

        protokollService.log(ProtokollKategorie.VORTRAEGE, "Vortrag geklont", "Referent '" + email + "' hat Vortrag '" + newTalk.titel + "' von Event '" + sourceTalk.veranstaltung.name + "' nach Event '" + targetEvent.name + "' geklont.", newTalk.id);

        return ReferentResource.mapVortragToDto(newTalk);
    }

    @Transactional
    public void deregisterTalkFromEvent(String email, Long talkId, Long eventId) {
        Referent referent = Referent.find("email", email).firstResult();
        Vortrag talk = Vortrag.findById(talkId);
        Veranstaltung event = Veranstaltung.findById(eventId);

        if (referent == null || talk == null || event == null) {
            return;
        }
        if (!talk.referent.id.equals(referent.id) || !talk.veranstaltung.id.equals(event.id)) {
            return;
        }

        checkDeadline(event);

        String titel = talk.titel;
        talk.delete();

        if (event.beginntAm.isAfter(LocalDateTime.now())) {
            mailService.sendVortragsRegistrierung(event, referent, talk, false);
        }
        protokollService.log(ProtokollKategorie.VORTRAEGE, "Vortrag von Event abgemeldet", "Referent '" + email + "' hat Vortrag '" + titel + "' von Event '" + event.name + "' abgemeldet.", talkId);
    }

    private void updateVortragFromDto(Vortrag vortrag, VortragDto dto) {
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

        if (vortrag == null || !vortrag.referent.id.equals(referent.id)) {
            return false;
        }

        checkDeadline(vortrag.veranstaltung);

        Veranstaltung event = vortrag.veranstaltung;
        String titel = vortrag.titel;
        vortrag.delete();

        if (event.beginntAm.isAfter(LocalDateTime.now())) {
            mailService.sendVortragsRegistrierung(event, referent, vortrag, false);
        }
        protokollService.log(ProtokollKategorie.VORTRAEGE, "Vortrag durch Referent gelöscht", "Referent '" + email + "' hat Vortrag '" + titel + "' gelöscht.", vortragId);

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
        if (veranstaltung == null) {
            throw new IllegalArgumentException("Veranstaltung nicht gefunden.");
        }

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

                ref.persist();
                ref.addVeranstaltung(veranstaltung);

                count++;
                protokollService.log(ProtokollKategorie.NUTZER, "Referent importiert", "Referent '" + ref.email + "' via CSV importiert und Event '" + veranstaltung.name + "' zugewiesen.", ref.id);
            }
        }
        return count;
    }
}
