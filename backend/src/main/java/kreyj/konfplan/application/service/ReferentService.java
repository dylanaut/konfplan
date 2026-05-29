package kreyj.konfplan.application.service;

import com.opencsv.bean.CsvToBeanBuilder;
import io.quarkus.elytron.security.common.BcryptUtil;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.OptimisticLockException;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import kreyj.konfplan.persistence.IdEntity;
import kreyj.konfplan.persistence.Nutzer;
import kreyj.konfplan.persistence.Pflichtvortrag;
import kreyj.konfplan.persistence.ProtokollKategorie;
import kreyj.konfplan.persistence.Raum;
import kreyj.konfplan.persistence.Referent;
import kreyj.konfplan.persistence.Slot;
import kreyj.konfplan.persistence.Veranstaltung;
import kreyj.konfplan.persistence.Vortrag;
import kreyj.konfplan.persistence.VortragVerfuegbarkeit;
import kreyj.konfplan.persistence.Wahlvortrag;
import kreyj.konfplan.presentation.ReferentResource;
import kreyj.konfplan.presentation.dto.NutzerDto;
import kreyj.konfplan.presentation.dto.ReferentVeranstaltungDto;
import kreyj.konfplan.presentation.dto.VortragDto;
import kreyj.konfplan.presentation.dto.csv.ReferentCsvDto;
import org.jboss.logging.Logger;

import java.io.FileReader;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static kreyj.konfplan.persistence.VortragVerfuegbarkeitId.vvIdL;

@ApplicationScoped
public class ReferentService {
    private static final Logger LOG = Logger.getLogger(ReferentService.class);

    private final MailService mailService;

    private final ProtokollService protokollService;

    public ReferentService(MailService mailService, ProtokollService protokollService) {
        this.mailService = mailService;
        this.protokollService = protokollService;
    }

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

        if (!Objects.equals(nutzer.getVersion(), dto.version)) {
            throw new OptimisticLockException("Der Nutzer wurde zwischenzeitlich von Dritten geändert. Bitte aktualisieren Sie die Daten und versuchen Sie es erneut.");
        }
        if (nutzer instanceof Referent referent) {
            referent.setBiography(dto.biography);
            referent.setJobRole(dto.jobRole);
            referent.setOrganisation(dto.organisation);
            referent.setSlogan(dto.slogan);
            referent.setFirstName(dto.firstName);
            referent.setLastName(dto.lastName);
            referent.setEmail(dto.email);
            protokollService.log(ProtokollKategorie.NUTZER, "Profil aktualisiert", "Referenten-Profil '" + email + "' aktualisiert.", referent.getId());
        }
    }

    public List<VortragDto> getReferentVortraege(String email) {
        Referent referent = Referent.find("email", email).firstResult();
        if (referent == null) {
            return new ArrayList<>();
        }

        List<Vortrag> vortraege = Vortrag.find("referent", referent).list();
        return vortraege.stream().map(ReferentResource::mapVortragToDto).toList();
    }

    public List<ReferentVeranstaltungDto> getReferentVeranstaltungen(String email) {
        Referent referent = Referent.find("email", email).firstResult();
        if (referent == null) {
            return new ArrayList<>();
        }

        // Alle Veranstaltungen, bei denen der Referent gelistet ist
        Set<Veranstaltung> events = new HashSet<>(referent.getVeranstaltungen());

        // Und alle Veranstaltungen, für die er bereits einen Vortrag hat
        List<Vortrag> vortraege = Vortrag.find("referent", referent).list();
        vortraege.stream().map(Vortrag::getVeranstaltung).forEach(events::add);

        return events.stream().map(e -> {
            ReferentVeranstaltungDto dto = new ReferentVeranstaltungDto();
            dto.id = e.getId();
            dto.name = e.getName();
            dto.beginntAm = e.getBeginntAm();
            dto.endetAm = e.getEndetAm();
            dto.deadlineReferenten = e.getDeadlineReferenten();
            dto.vortraegeIds = vortraege.stream()
                    .filter(t -> t.getVeranstaltung().getId().equals(e.getId()))
                    .map(IdEntity::getId)
                    .toList();
            return dto;
        }).sorted(Comparator.comparing(e -> e.beginntAm)).toList();
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
        vortrag.setReferent(referent);
        vortrag.setVeranstaltung(veranstaltung);
        updateVortragFromDto(vortrag, dto);
        vortrag.persist();

        if (vortrag.getVeranstaltung().getBeginntAm().isAfter(LocalDateTime.now())) {
            mailService.sendVortragsRegistrierung(vortrag.getVeranstaltung(), referent, vortrag, true);
        }

        protokollService.log(ProtokollKategorie.VORTRAEGE, "Vortrag durch Referent erstellt", "Referent '" + email + "' hat Vortrag '" + vortrag.getTitel() + "' für Event '" + veranstaltung.getName() + "' erstellt.", vortrag.getId());
        return ReferentResource.mapVortragToDto(vortrag);
    }

    @Transactional
    public VortragDto updateVortrag(String email, Long vortragId, VortragDto dto) {
        Referent referent = Referent.find("email", email).firstResult();
        Vortrag vortrag = Vortrag.findById(vortragId);

        if (vortrag == null || !vortrag.getReferent().getId().equals(referent.getId())) {
            return null;
        }

        if (!Objects.equals(vortrag.getVersion(), dto.version)) {
            throw new OptimisticLockException("Der Vortrag wurde zwischenzeitlich von Dritten geändert. Bitte aktualisieren Sie die Daten und versuchen Sie es erneut.");
        }

        checkDeadline(vortrag.getVeranstaltung());

        updateVortragFromDto(vortrag, dto);
        protokollService.log(ProtokollKategorie.VORTRAEGE, "Vortrag durch Referent aktualisiert", "Referent '" + email + "' hat Vortrag '" + vortrag.getTitel() + "' aktualisiert.", vortrag.getId());
        return ReferentResource.mapVortragToDto(vortrag);
    }

    @Transactional
    public void registerTalkForEvent(String email, Long vortragId, Long eventId) {
        Referent referent = Referent.find("email", email).firstResult();
        Vortrag sourceTalk = Vortrag.findById(vortragId);
        Veranstaltung targetEvent = Veranstaltung.findById(eventId);

        if (referent == null || sourceTalk == null || targetEvent == null) {
            return;
        }
        if (!sourceTalk.getReferent().getId().equals(referent.getId())) {
            return;
        }

        checkDeadline(targetEvent);

        // Prüfen, ob bereits ein Vortrag mit diesem Titel in der Zielveranstaltung existiert
        boolean exists = Vortrag.find("referent = ?1 and veranstaltung = ?2 and titel = ?3", referent, targetEvent, sourceTalk.getTitel()).count() > 0;
        if (exists) {
            return;
        }

        Vortrag newTalk;
        if (sourceTalk instanceof Wahlvortrag sw) {
            Wahlvortrag nw = new Wahlvortrag();
            nw.setWiederholbar(sw.isWiederholbar());
            nw.setMaxWiederholungen(sw.getMaxWiederholungen());
            // Wir übernehmen keine Slots, da diese veranstaltungsspezifisch sind!
            newTalk = nw;
        } else {
            Pflichtvortrag np = new Pflichtvortrag();
            np.setPflichtgruppe(((Pflichtvortrag) sourceTalk).getPflichtgruppe());
            newTalk = np;
        }

        newTalk.setTitel(sourceTalk.getTitel());
        newTalk.setInhalt(sourceTalk.getInhalt());
        newTalk.setReferent(referent);
        newTalk.setVeranstaltung(targetEvent);
        newTalk.persist();

        if (targetEvent.getBeginntAm().isAfter(LocalDateTime.now())) {
            mailService.sendVortragsRegistrierung(targetEvent, referent, newTalk, true);
        }
        protokollService.log(ProtokollKategorie.VORTRAEGE, "Vortrag für weiteres Event registriert", "Referent '" + email + "' hat Vortrag '" + newTalk.getTitel() + "' für Event '" + targetEvent.getName() + "' registriert.", newTalk.getId());
    }

    @Transactional
    public VortragDto cloneTalkForEvent(String email, Long sourceVortragId, Long targetEventId) {
        Referent referent = Referent.find("email", email).firstResult();
        if (referent == null) {
            throw new WebApplicationException("Referent nicht gefunden.", Response.Status.NOT_FOUND);
        }

        Vortrag sourceTalk = Vortrag.findById(sourceVortragId);
        if (sourceTalk == null) {
            throw new WebApplicationException("Quell-Vortrag nicht gefunden.", Response.Status.NOT_FOUND);
        }

        Veranstaltung targetEvent = Veranstaltung.findById(targetEventId);
        if (targetEvent == null) {
            throw new WebApplicationException("Ziel-Veranstaltung nicht gefunden.", Response.Status.NOT_FOUND);
        }

        // Validierung: Gehört der Quell-Vortrag dem Referenten?
        if (!sourceTalk.getReferent().getId().equals(referent.getId())) {
            throw new WebApplicationException("Referent ist nicht der Eigentümer des Quell-Vortrags.", Response.Status.FORBIDDEN);
        }

        // Deadline für die Ziel-Veranstaltung prüfen
        checkDeadline(targetEvent);

        // Prüfen, ob bereits ein Vortrag mit demselben Titel in der Zielveranstaltung existiert
        boolean exists = Vortrag.find("referent = ?1 and veranstaltung = ?2 and titel = ?3", referent, targetEvent, sourceTalk.getTitel()).count() > 0;
        if (exists) {
            throw new WebApplicationException("Ein Vortrag mit demselben Titel existiert bereits für diesen Referenten in der Ziel-Veranstaltung.", Response.Status.CONFLICT);
        }

        Vortrag newTalk;
        if (sourceTalk instanceof Wahlvortrag sw) {
            Wahlvortrag nw = new Wahlvortrag();
            nw.setWiederholbar(sw.isWiederholbar());
            nw.setMaxWiederholungen(sw.getMaxWiederholungen());
            // Wahl-Slots werden nicht kopiert, da sie veranstaltungsspezifisch sind.
            newTalk = nw;
        } else if (sourceTalk instanceof Pflichtvortrag) {
            // Pflichtvorträge können nicht von Referenten geklont werden, da sie eine Admin-Konfiguration erfordern (Slots, Räume, Gruppen).
            throw new WebApplicationException("Pflichtvorträge können nicht von Referenten geklont werden.", Response.Status.BAD_REQUEST);
        } else {
            throw new WebApplicationException("Unbekannter Vortragstyp.", Response.Status.INTERNAL_SERVER_ERROR);
        }

        newTalk.setTitel(sourceTalk.getTitel());
        newTalk.setInhalt(sourceTalk.getInhalt()); // AbstractText wird kopiert und kann angepasst werden
        newTalk.setReferent(referent);
        newTalk.setVeranstaltung(targetEvent);
        newTalk.persist();

        // Benachrichtigung senden, wenn die Veranstaltung in der Zukunft liegt
        if (targetEvent.getBeginntAm().isAfter(LocalDateTime.now())) {
            mailService.sendVortragsRegistrierung(targetEvent, referent, newTalk, true);
        }

        protokollService.log(ProtokollKategorie.VORTRAEGE, "Vortrag geklont", "Referent '" + email + "' hat Vortrag '" + newTalk.getTitel() + "' von Event '" + sourceTalk.getVeranstaltung().getName() + "' nach Event '" + targetEvent.getName() + "' geklont.", newTalk.getId());

        return ReferentResource.mapVortragToDto(newTalk);
    }

    @Transactional
    public void deregisterTalkFromEvent(String email, Long vortragId, Long eventId) {
        Referent referent = Referent.find("email", email).firstResult();
        Vortrag talk = Vortrag.findById(vortragId);
        Veranstaltung event = Veranstaltung.findById(eventId);

        if (referent == null || talk == null || event == null) {
            return;
        }
        if (!talk.getReferent().getId().equals(referent.getId()) || !talk.getVeranstaltung().getId().equals(event.getId())) {
            return;
        }

        checkDeadline(event);

        String titel = talk.getTitel();
        talk.delete();

        if (event.getBeginntAm().isAfter(LocalDateTime.now())) {
            mailService.sendVortragsRegistrierung(event, referent, talk, false);
        }
        protokollService.log(ProtokollKategorie.VORTRAEGE, "Vortrag von Event abgemeldet", "Referent '" + email + "' hat Vortrag '" + titel + "' von Event '" + event.getName() + "' abgemeldet.", vortragId);
    }

    private void updateVortragFromDto(Vortrag vortrag, VortragDto dto) {
        if (!Objects.equals(vortrag.getId(), dto.id)) {
            throw new IllegalArgumentException("Vortrag-Ids sind unterschiedlich");
        }
        if (!Objects.equals(vortrag.getVeranstaltung().getId(), dto.veranstaltungId)) {
            throw new IllegalArgumentException("Veranstaltung-Ids sind unterschiedlich");
        }

        vortrag.setTitel(dto.titel);
        vortrag.setInhalt(dto.inhalt);

        if (vortrag instanceof Wahlvortrag wahlvortrag) {
            wahlvortrag.setWiederholbar(dto.wiederholbar);
            if (dto.maxWiederholungen > 0) {
                wahlvortrag.setMaxWiederholungen(dto.maxWiederholungen);
            }
            if (dto.verfuegbareSlotIds != null) {
                VortragVerfuegbarkeit vv = VortragVerfuegbarkeit.findById(vvIdL(dto.id, dto.veranstaltungId));

                if (null == vv) {
                    vv = new VortragVerfuegbarkeit(dto.id, dto.veranstaltungId, dto.verfuegbareSlotIds);
                }

                vv.persist();
            }
        } else if (vortrag instanceof Pflichtvortrag pflichtvortrag) {
            pflichtvortrag.setPflichtgruppe(dto.pflichtGruppe);
            pflichtvortrag.setPflichtraum(Raum.findById(dto.pflichtRaumId));
            pflichtvortrag.setPflichtslot(Slot.findById(dto.pflichtRaumId));
        }
    }

    @Transactional
    public boolean deleteVortrag(String email, Long vortragId) {
        Referent referent = Referent.find("email", email).firstResult();
        Vortrag vortrag = Vortrag.findById(vortragId);

        if (vortrag == null || !vortrag.getReferent().getId().equals(referent.getId())) {
            return false;
        }

        checkDeadline(vortrag.getVeranstaltung());

        Veranstaltung event = vortrag.getVeranstaltung();
        String titel = vortrag.getTitel();
        vortrag.delete();

        if (event.getBeginntAm().isAfter(LocalDateTime.now())) {
            mailService.sendVortragsRegistrierung(event, referent, vortrag, false);
        }
        protokollService.log(ProtokollKategorie.VORTRAEGE, "Vortrag durch Referent gelöscht", "Referent '" + email + "' hat Vortrag '" + titel + "' gelöscht.", vortragId);

        return true;
    }

    private void checkDeadline(Veranstaltung v) {
        if (v.getDeadlineReferenten() != null && v.getDeadlineReferenten().isBefore(LocalDateTime.now())) {
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
                    ref.setEmail(dto.email.trim().toLowerCase());
                    String tempPassword = "start123";
                    ref.setPasswordHash(BcryptUtil.bcryptHash(tempPassword));
                    ref.persist();
                } else if (existingNutzer instanceof Referent) {
                    ref = (Referent) existingNutzer;
                } else {
                    LOG.warn("Nutzer mit Email " + dto.email + " existiert bereits, ist aber kein Referent. Überspringe.");
                    continue;
                }

                ref.setFirstName(dto.vorname);
                ref.setLastName(dto.nachname);
                ref.setJobRole(dto.position);
                ref.setOrganisation(dto.organisation);
                ref.setSlogan(dto.slogan);
                ref.setBiography(dto.biografie);

                ref.persist();
                ref.addVeranstaltung(veranstaltung);

                count++;
                protokollService.log(ProtokollKategorie.NUTZER, "Referent importiert", "Referent '" + ref.getEmail() + "' via CSV importiert und Event '" + veranstaltung.getName() + "' zugewiesen.", ref.getId());
            }
        }
        return count;
    }
}
