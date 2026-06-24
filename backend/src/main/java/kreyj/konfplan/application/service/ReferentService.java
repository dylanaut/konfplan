package kreyj.konfplan.application.service;

import com.opencsv.bean.CsvToBeanBuilder;
import io.quarkus.elytron.security.common.BcryptUtil;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.OptimisticLockException;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import kreyj.konfplan.adapter.in.web.dto.NutzerDto;
import kreyj.konfplan.adapter.in.web.dto.ReferentVeranstaltungDto;
import kreyj.konfplan.adapter.in.web.dto.VortragDto;
import kreyj.konfplan.adapter.in.web.dto.csv.ReferentCsvDto;
import kreyj.konfplan.application.port.in.ReferentServiceInterface;
import kreyj.konfplan.persistence.*;
import org.jboss.logging.Logger;

import java.io.FileReader;
import java.nio.file.Path;
import java.sql.Ref;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static kreyj.konfplan.persistence.VortragVerfuegbarkeitId.vvId;
import static kreyj.konfplan.persistence.VortragVerfuegbarkeitId.vvIdL;

@ApplicationScoped
public class ReferentService implements ReferentServiceInterface {
    private static final Logger LOG = Logger.getLogger(ReferentService.class);

    private final MailService mailService;

    private final ProtokollService protokollService;

    public ReferentService(MailService mailService, ProtokollService protokollService) {
        this.mailService = mailService;
        this.protokollService = protokollService;
    }


    @Transactional
    @Override
    public Referent findByEmail(String email) {
        Nutzer nutzer = Nutzer.findByEmail(email);
        if (nutzer instanceof Referent) {
            return (Referent) nutzer;
        }
        return null;
    }

    @Transactional
    @Override
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

    @Override
    public List<VortragDto> getReferentVortraege(String email) {
        Referent referent = Referent.find("email", email).firstResult();
        if (referent == null) {
            return new ArrayList<>();
        }

        List<Vortrag> vortraege = Vortrag.find("referent", referent).list();
        return vortraege.stream().map(ReferentService::mapVortragToDto).toList();
    }

    @Override
    public List<ReferentVeranstaltungDto> getReferentVeranstaltungen(Referent referent) {
        if (referent == null) {
            return new ArrayList<>();
        }

        // Alle Veranstaltungen, bei denen der Referent gelistet ist
        Set<Veranstaltung> veranstaltungen = new HashSet<>(referent.getVeranstaltungen());

        // Und alle Veranstaltungen, für die er bereits einen Vortrag hat
        List<Vortrag> vortraege = Vortrag.find("referent", referent).list();
        vortraege.stream().map(Vortrag::getVeranstaltung).forEach(veranstaltungen::add);

        return veranstaltungen.stream().map(e -> {
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
            dto.planErstellt = Planungsergebnis.count("veranstaltung", e) > 0; // Prüfen, ob ein Ergebnis existiert
            return dto;
        }).sorted(Comparator.comparing(e -> e.beginntAm)).toList();
    }


    @Transactional
    @Override
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
        vortrag.persistAndFlush();

        if (vortrag.getVeranstaltung().getBeginntAm().isAfter(LocalDateTime.now())) {
            mailService.sendVortragsRegistrierung(vortrag.getVeranstaltung(), referent, vortrag, true);
        }

        protokollService.log(ProtokollKategorie.VORTRAEGE, "Vortrag durch Referent erstellt", "Referent '" + email + "' hat Vortrag '" + vortrag.getTitel() + "' für Event '" + veranstaltung.getName() + "' erstellt.", vortrag.getId());
        return mapVortragToDto(vortrag);
    }

    @Transactional
    @Override
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
        return mapVortragToDto(vortrag);
    }

    @Transactional
    @Override
    public void meldeVortragFuerVeranstaltungAn(String email, Long vortragId, Long veranstaltungId) {
        Referent referent = Referent.find("email", email).firstResult();
        Vortrag sourceTalk = Vortrag.findById(vortragId);
        Veranstaltung veranstaltung = Veranstaltung.findById(veranstaltungId);

        if (referent == null || sourceTalk == null || veranstaltung == null) {
            return;
        }
        if (!sourceTalk.getReferent().getId().equals(referent.getId())) {
            return;
        }

        checkDeadline(veranstaltung);

        // Prüfen, ob bereits ein Vortrag mit diesem Titel in der Zielveranstaltung existiert
        boolean exists = Vortrag.find("referent = ?1 and veranstaltung = ?2 and titel = ?3", referent, veranstaltung, sourceTalk.getTitel()).count() > 0;
        if (exists) {
            return;
        }

        Vortrag vortrag;
        if (sourceTalk instanceof Wahlvortrag sw) {
            Wahlvortrag nw = new Wahlvortrag();
            nw.setWiederholbar(sw.isWiederholbar());
            nw.setMaxWiederholungen(sw.getMaxWiederholungen());
            // Wir übernehmen keine Slots, da diese veranstaltungsspezifisch sind!
            vortrag = nw;
        } else {
            Pflichtvortrag np = new Pflichtvortrag();
            np.updatePflichtgruppe(((Pflichtvortrag) sourceTalk).getPflichtgruppe());
            vortrag = np;
        }

        vortrag.setTitel(sourceTalk.getTitel());
        vortrag.setInhalt(sourceTalk.getInhalt());
        vortrag.setAusstattung(sourceTalk.getAusstattung());
        vortrag.setBerufsfeld(sourceTalk.getBerufsfeld());
        vortrag.setReferent(referent);
        vortrag.setVeranstaltung(veranstaltung);
        vortrag.persistAndFlush();

        if (veranstaltung.getBeginntAm().isAfter(LocalDateTime.now())) {
            mailService.sendVortragsRegistrierung(veranstaltung, referent, vortrag, true);
        }
        protokollService.log(ProtokollKategorie.VORTRAEGE, "Vortrag für weiteres Event registriert", "Referent '" + email + "' hat Vortrag '" + vortrag.getTitel() + "' für Event '" + veranstaltung.getName() + "' registriert.", vortrag.getId());
    }

    @Transactional
    @Override
    public VortragDto uebernimmVortragInVeranstaltung(String email, Long sourceVortragId, Long veranstaltungId) {
        Referent referent = Referent.find("email", email).firstResult();
        if (referent == null) {
            throw new WebApplicationException("Referent nicht gefunden.", Response.Status.NOT_FOUND);
        }

        Vortrag quellVortrag = Vortrag.findById(sourceVortragId);
        if (quellVortrag == null) {
            throw new WebApplicationException("Quell-Vortrag nicht gefunden.", Response.Status.NOT_FOUND);
        }

        Veranstaltung veranstaltung = Veranstaltung.findById(veranstaltungId);
        if (veranstaltung == null) {
            throw new WebApplicationException("Ziel-Veranstaltung nicht gefunden.", Response.Status.NOT_FOUND);
        }

        // Validierung: Gehört der Quell-Vortrag dem Referenten?
        if (!quellVortrag.getReferent().getId().equals(referent.getId())) {
            throw new WebApplicationException("Referent ist nicht der Eigentümer des Quell-Vortrags.", Response.Status.FORBIDDEN);
        }

        // Deadline für die Ziel-Veranstaltung prüfen
        checkDeadline(veranstaltung);

        // Prüfen, ob bereits ein Vortrag mit demselben Titel in der Zielveranstaltung existiert
        boolean exists = Vortrag.find("referent = ?1 and veranstaltung = ?2 and titel = ?3", referent, veranstaltung, quellVortrag.getTitel()).count() > 0;
        if (exists) {
            throw new WebApplicationException("Ein Vortrag mit demselben Titel existiert bereits für diesen Referenten in der Ziel-Veranstaltung.", Response.Status.CONFLICT);
        }

        Vortrag zielVortrag;
        if (quellVortrag instanceof Wahlvortrag sw) {
            Wahlvortrag nw = new Wahlvortrag();
            nw.setWiederholbar(sw.isWiederholbar());
            nw.setMaxWiederholungen(sw.getMaxWiederholungen());
            // Wahl-Slots werden nicht kopiert, da sie veranstaltungsspezifisch sind.
            zielVortrag = nw;
        } else if (quellVortrag instanceof Pflichtvortrag) {
            // Pflichtvorträge können nicht von Referenten geklont werden, da sie eine Admin-Konfiguration erfordern (Slots, Räume, Gruppen).
            throw new WebApplicationException("Pflichtvorträge können nicht von Referenten geklont werden.", Response.Status.BAD_REQUEST);
        } else {
            throw new WebApplicationException("Unbekannter Vortragstyp.", Response.Status.INTERNAL_SERVER_ERROR);
        }

        zielVortrag.setTitel(quellVortrag.getTitel());
        zielVortrag.setInhalt(quellVortrag.getInhalt()); // AbstractText wird kopiert und kann angepasst werden
        zielVortrag.setAusstattung(quellVortrag.getAusstattung());
        zielVortrag.setBerufsfeld(quellVortrag.getBerufsfeld());
        zielVortrag.setReferent(referent);
        zielVortrag.setVeranstaltung(veranstaltung);
        zielVortrag.persistAndFlush();

        // Benachrichtigung senden, wenn die Veranstaltung in der Zukunft liegt
        if (veranstaltung.getBeginntAm().isAfter(LocalDateTime.now())) {
            mailService.sendVortragsRegistrierung(veranstaltung, referent, zielVortrag, true);
        }

        protokollService.log(ProtokollKategorie.VORTRAEGE, "Vortrag geklont", "Referent '" + email + "' hat Vortrag '" + zielVortrag.getTitel() + "' von Event '" + quellVortrag.getVeranstaltung().getName() + "' nach Event '" + veranstaltung.getName() + "' geklont.", zielVortrag.getId());

        return mapVortragToDto(zielVortrag);
    }

    @Transactional
    @Override
    public void meldeVortragFuerVeranstaltungAb(String email, Long vortragId, Long veranstaltungId) {
        Referent referent = Referent.find("email", email).firstResult();
        Vortrag vortrag = Vortrag.findById(vortragId);
        Veranstaltung veranstaltung = Veranstaltung.findById(veranstaltungId);

        if (referent == null || vortrag == null || veranstaltung == null) {
            return;
        }
        if (!vortrag.getReferent().getId().equals(referent.getId()) || !vortrag.getVeranstaltung().getId().equals(veranstaltung.getId())) {
            return;
        }

        checkDeadline(veranstaltung);

        String titel = vortrag.getTitel();
        vortrag.delete();

        if (veranstaltung.getBeginntAm().isAfter(LocalDateTime.now())) {
            mailService.sendVortragsRegistrierung(veranstaltung, referent, vortrag, false);
        }
        protokollService.log(ProtokollKategorie.VORTRAEGE, "Vortrag von Event abgemeldet", "Referent '" + email + "' hat Vortrag '" + titel + "' von Event '" + veranstaltung.getName() + "' abgemeldet.", vortragId);
    }

    private void updateVortragFromDto(Vortrag vortrag, VortragDto dto) {
        if (vortrag.getId() != null && !Objects.equals(vortrag.getId(), dto.id)) {
            throw new IllegalArgumentException("Vortrag-Ids sind unterschiedlich");
        }
        if (vortrag.getVeranstaltung() != null && !Objects.equals(vortrag.getVeranstaltung().getId(), dto.veranstaltungId)) {
            throw new IllegalArgumentException("Veranstaltung-Ids sind unterschiedlich");
        }

        vortrag.setTitel(dto.titel);
        vortrag.setInhalt(dto.inhalt);
        vortrag.setAusstattung(dto.ausstattung);
        vortrag.setBerufsfeld(dto.berufsfeld);

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

                vv.persistAndFlush();
            }
        } else if (vortrag instanceof Pflichtvortrag pflichtvortrag) {
            pflichtvortrag.updatePflichtgruppe(dto.pflichtGruppe);
            pflichtvortrag.updatePflichtraum(Raum.findById(dto.pflichtRaumId));
            pflichtvortrag.updatePflichtslot(Slot.findById(dto.pflichtSlotId));
        }
    }

    @Transactional
    @Override
    public boolean deleteVortrag(String email, Long vortragId) {
        Referent referent = Referent.find("email", email).firstResult();
        Vortrag vortrag = Vortrag.findById(vortragId);

        if (vortrag == null || !vortrag.getReferent().getId().equals(referent.getId())) {
            return false;
        }

        checkDeadline(vortrag.getVeranstaltung());

        Veranstaltung veranstaltung = vortrag.getVeranstaltung();
        String titel = vortrag.getTitel();
        vortrag.delete();

        if (veranstaltung.getBeginntAm().isAfter(LocalDateTime.now())) {
            mailService.sendVortragsRegistrierung(veranstaltung, referent, vortrag, false);
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
    @Override
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
                    ref.persistAndFlush();
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

                ref.persistAndFlush();
                ref.addVeranstaltung(veranstaltung);

                count++;
                protokollService.log(ProtokollKategorie.NUTZER, "Referent importiert", "Referent '" + ref.getEmail() + "' via CSV importiert und Event '" + veranstaltung.getName() + "' zugewiesen.", ref.getId());
            }
        }
        return count;
    }

    // -------------------------------------------------------------------
    // Mapper methods
    // -------------------------------------------------------------------


    public static VortragDto mapVortragToDto(Vortrag v) {
        VortragDto dto = new VortragDto();
        dto.id = v.getId();
        dto.version = v.getVersion();
        dto.titel = v.getTitel();
        dto.inhalt = v.getInhalt();
        dto.ausstattung = v.getAusstattung();
        dto.berufsfeld = v.getBerufsfeld();
        dto.veranstaltungId = v.getVeranstaltung().getId();
        dto.veranstaltungName = v.getVeranstaltung().getName();
        dto.referentId = v.getReferent().getId();
        dto.referentName = v.getReferent().getLastName() + ", " + v.getReferent().getFirstName();
        dto.referentOrganisation = v.getReferent().getOrganisation();

        if (v instanceof Wahlvortrag wahlvortrag) {
            dto.wiederholbar = wahlvortrag.isWiederholbar();
            dto.maxWiederholungen = wahlvortrag.getMaxWiederholungen();
            VortragVerfuegbarkeit vv = VortragVerfuegbarkeit.findById(vvId(
                    wahlvortrag, wahlvortrag.getVeranstaltung()));
            if (null == vv) {
                dto.verfuegbareSlotIds = Slot.<Slot>find("veranstaltung", v.getVeranstaltung())
                        .stream()
                        .map(Slot::getId)
                        .collect(Collectors.toSet());
            } else {
                dto.verfuegbareSlotIds = vv.getVerfuegbareSlotIds();
            }
        } else if (v instanceof Pflichtvortrag pflichtvortrag) {
            dto.istPflicht = true;
            dto.pflichtGruppe = pflichtvortrag.getPflichtgruppe();
            dto.pflichtRaumId = pflichtvortrag.getPflichtraum().getId();

            Slot pflichtslot = pflichtvortrag.getPflichtslot();
            if (pflichtslot != null) {
                dto.pflichtSlotId = pflichtslot.getId();
                dto.verfuegbareSlotIds = Set.of(pflichtslot.getId());
            }
        }

        return dto;
    }

    public static Vortrag mapDtoToVortrag(VortragDto dto) {
        Vortrag vortrag = dto.istPflicht ? new Pflichtvortrag() : new Wahlvortrag();

        vortrag.setId(dto.id);
        vortrag.setVersion(dto.version);
        vortrag.setTitel(dto.titel);
        vortrag.setInhalt(dto.inhalt);
        vortrag.setAusstattung(dto.ausstattung);
        vortrag.setBerufsfeld(dto.berufsfeld);
        vortrag.setVeranstaltung(Veranstaltung.findById(dto.veranstaltungId));
        vortrag.setReferent(Referent.findById(dto.referentId));
        if (vortrag instanceof Wahlvortrag wahlvortrag) {
            wahlvortrag.setWiederholbar(dto.wiederholbar);
            wahlvortrag.setMaxWiederholungen(dto.maxWiederholungen);
        } else {
            Pflichtvortrag pflichtvortrag = (Pflichtvortrag) vortrag;
            pflichtvortrag.updatePflichtgruppe(dto.pflichtGruppe);
        }

        return vortrag;

    }
}
