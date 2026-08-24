package kreyj.konfplan.domain.service;

import com.opencsv.bean.CsvToBeanBuilder;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.OptimisticLockException;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import kreyj.konfplan.adapter.in.web.dto.NutzerDto;
import kreyj.konfplan.adapter.in.web.dto.OrganisatorDto;
import kreyj.konfplan.adapter.in.web.dto.ReferentVeranstaltungDto;
import kreyj.konfplan.adapter.in.web.dto.VortragDto;
import kreyj.konfplan.adapter.in.web.dto.csv.ReferentCsvDto;
import kreyj.konfplan.application.port.in.ReferentServiceInterface;
import kreyj.konfplan.persistence.Admin;
import kreyj.konfplan.persistence.IdEntity;
import kreyj.konfplan.persistence.Nutzer;
import kreyj.konfplan.persistence.Pflichtvortrag;
import kreyj.konfplan.persistence.Planungsergebnis;
import kreyj.konfplan.persistence.ProtokollKategorie;
import kreyj.konfplan.persistence.Raum;
import kreyj.konfplan.persistence.Referent;
import kreyj.konfplan.persistence.Slot;
import kreyj.konfplan.persistence.Veranstaltung;
import kreyj.konfplan.persistence.Vortrag;
import kreyj.konfplan.persistence.VortragVerfuegbarkeit;
import kreyj.konfplan.persistence.Wahlvortrag;
import kreyj.konfplan.util.CsvHelper;
import org.apache.commons.lang3.StringUtils;
import org.jboss.logging.Logger;

import java.io.Reader;
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
public class ReferentService implements ReferentServiceInterface {
    private static final Logger LOG = Logger.getLogger(ReferentService.class);

    private final MailService mailService;

    private final ProtokollService protokollService;
    private final KeycloakUserProvisioningService keycloakUserProvisioningService;


    public ReferentService(MailService mailService, ProtokollService protokollService,
                           KeycloakUserProvisioningService keycloakUserProvisioningService) {
        this.mailService = mailService;
        this.protokollService = protokollService;
        this.keycloakUserProvisioningService = keycloakUserProvisioningService;
    }


    @Transactional
    @Override
    public Referent findByLoginName(String loginName) {
        Nutzer nutzer = Nutzer.findByLoginName(loginName);
        if (nutzer instanceof Referent) {
            return (Referent) nutzer;
        }
        return null;
    }


    @Transactional
    @Override
    public void updateProfile(String loginName, NutzerDto dto) {
        if (null == dto) {
            return;
        }

        Nutzer nutzer = Nutzer.findByLoginName(loginName);

        if (!Objects.equals(nutzer.getVersion(), dto.version)) {
            throw new OptimisticLockException("Der Nutzer wurde zwischenzeitlich von Dritten geändert. Bitte aktualisieren Sie die Daten und versuchen Sie es erneut.");
        }
        if (nutzer instanceof Referent referent) {
            referent.setJobRole(dto.jobRole);
            referent.setOrganisation(dto.organisation);
            referent.setFirstName(dto.firstName);
            referent.setLastName(dto.lastName);
            referent.setEmail(dto.email);
            keycloakUserProvisioningService.updateUser(referent);
            protokollService.log(ProtokollKategorie.NUTZER, "Profil aktualisiert", "Referenten-Profil '" + loginName + "' aktualisiert.", referent.getId());
        }
    }


    @Override
    public List<VortragDto> getReferentVortraege(String loginName) {
        Referent referent = Referent.find("loginName", loginName).firstResult();
        if (null == referent) {
            return new ArrayList<>();
        }

        List<Vortrag> vortraege = Vortrag.find("referent", referent).list();
        return vortraege.stream().map(VortragDto::from).toList();
    }


    @Override
    public List<ReferentVeranstaltungDto> getReferentVeranstaltungen(Referent referent) {
        if (null == referent) {
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
            dto.logo = e.getLogo();
            dto.logo_link = e.getLogo_link();
            dto.organisatorNamen = e.organisatoren().stream().map(Admin::getFullName).toList();
            dto.organisatoren = e.organisatoren().stream().map(OrganisatorDto::from).toList();
            return dto;
        }).sorted(Comparator.comparing(e -> e.beginntAm)).toList();
    }


    @Transactional
    @Override
    public VortragDto createVortrag(String loginName, VortragDto dto) {
        Referent referent = Referent.find("loginName", loginName).firstResult();
        if (null == referent) {
            return null;
        }

        Veranstaltung veranstaltung = Veranstaltung.findById(dto.veranstaltungId);
        if (null == veranstaltung) {
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

        protokollService.log(ProtokollKategorie.VORTRAEGE, "Vortrag durch Referent erstellt", "Referent '" + loginName + "' hat Vortrag '" + vortrag.getTitel() + "' für Event '" + veranstaltung.getName() + "' erstellt.", vortrag.getId(), veranstaltung.getId());
        return VortragDto.from(vortrag);
    }


    @Transactional
    @Override
    public VortragDto updateVortrag(String loginName, Long vortragId, VortragDto dto) {
        Referent referent = Referent.find("loginName", loginName).firstResult();
        Vortrag vortrag = Vortrag.findById(vortragId);

        if (null == vortrag || !vortrag.getReferent().getId().equals(referent.getId())) {
            return null;
        }

        if (!Objects.equals(vortrag.getVersion(), dto.version)) {
            throw new OptimisticLockException("Der Vortrag wurde zwischenzeitlich von Dritten geändert. Bitte aktualisieren Sie die Daten und versuchen Sie es erneut.");
        }

        checkDeadline(vortrag.getVeranstaltung());

        updateVortragFromDto(vortrag, dto);
        protokollService.log(ProtokollKategorie.VORTRAEGE, "Vortrag durch Referent aktualisiert", "Referent '" + loginName + "' hat Vortrag '" + vortrag.getTitel() + "' aktualisiert.", vortrag.getId(), vortrag.getVeranstaltung().getId());
        return VortragDto.from(vortrag);
    }


    @Transactional
    @Override
    public void meldeVortragFuerVeranstaltungAn(String loginName, Long vortragId, Long veranstaltungId) {
        Referent referent = Referent.find("loginName", loginName).firstResult();
        Vortrag vortrag = Vortrag.findById(vortragId);
        Veranstaltung veranstaltung = Veranstaltung.findById(veranstaltungId);

        if (null == referent || null == vortrag || null == veranstaltung) {
            return;
        }
        if (!vortrag.getReferent().getId().equals(referent.getId())) {
            return;
        }

        checkDeadline(veranstaltung);

        // Prüfen, ob bereits ein Vortrag mit diesem Titel in der Zielveranstaltung existiert
        boolean exists = Vortrag.find("referent = ?1 and veranstaltung = ?2 and titel = ?3",
            referent, veranstaltung, vortrag.getTitel())
            .count() > 0;
        if (exists) {
            return;
        }

        if (vortrag instanceof Wahlvortrag sw) {
            Wahlvortrag nw = new Wahlvortrag();
            nw.setWiederholbar(sw.isWiederholbar());
            nw.setMaxWiederholungen(sw.getMaxWiederholungen());
            nw.setNeigungen(sw.getNeigungen());
            // Wir übernehmen keine Slots, da diese veranstaltungsspezifisch sind!
            vortrag = nw;
        } else {
            Pflichtvortrag np = new Pflichtvortrag();
            np.updatePflichtgruppe(((Pflichtvortrag) vortrag).getPflichtgruppe());
            vortrag = np;
        }

        vortrag.setTitel(vortrag.getTitel());
        vortrag.setInhalt(vortrag.getInhalt());
        vortrag.setAusstattung(vortrag.getAusstattung());
        vortrag.setAbschluss(vortrag.getAbschluss());
        vortrag.setReferent(referent);
        vortrag.setVeranstaltung(veranstaltung);
        vortrag.persistAndFlush();

        if (veranstaltung.getBeginntAm().isAfter(LocalDateTime.now())) {
            mailService.sendVortragsRegistrierung(veranstaltung, referent, vortrag, true);
        }
        protokollService.log(ProtokollKategorie.VORTRAEGE, "Vortrag für weiteres Event registriert", "Referent '" + loginName + "' hat Vortrag '" + vortrag.getTitel() + "' für Event '" + veranstaltung.getName() + "' registriert.", vortrag.getId(), veranstaltung.getId());
    }


    @Transactional
    @Override
    public VortragDto uebernimmVortragInVeranstaltung(String loginName, Long sourceVortragId, Long veranstaltungId) {
        Referent referent = Referent.find("loginName", loginName).firstResult();
        if (null == referent) {
            throw new WebApplicationException("Referent nicht gefunden.", Response.Status.NOT_FOUND);
        }

        Vortrag quellVortrag = Vortrag.findById(sourceVortragId);
        if (null == quellVortrag) {
            throw new WebApplicationException("Quell-Vortrag nicht gefunden.", Response.Status.NOT_FOUND);
        }

        Veranstaltung veranstaltung = Veranstaltung.findById(veranstaltungId);
        if (null == veranstaltung) {
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
            nw.setNeigungen(sw.getNeigungen());
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
        zielVortrag.setAbschluss(quellVortrag.getAbschluss());
        zielVortrag.setReferent(referent);
        zielVortrag.setVeranstaltung(veranstaltung);
        zielVortrag.persistAndFlush();

        // Benachrichtigung senden, wenn die Veranstaltung in der Zukunft liegt
        if (veranstaltung.getBeginntAm().isAfter(LocalDateTime.now())) {
            mailService.sendVortragsRegistrierung(veranstaltung, referent, zielVortrag, true);
        }

        protokollService.log(ProtokollKategorie.VORTRAEGE, "Vortrag geklont", "Referent '" + loginName + "' hat Vortrag '" + zielVortrag.getTitel() + "' von Event '" + quellVortrag.getVeranstaltung().getName() + "' nach Event '" + veranstaltung.getName() + "' geklont.", zielVortrag.getId(), veranstaltung.getId());

        return VortragDto.from(zielVortrag);
    }


    @Transactional
    @Override
    public void meldeVortragFuerVeranstaltungAb(String loginName, Long vortragId, Long veranstaltungId) {
        Referent referent = Referent.find("loginName", loginName).firstResult();
        Vortrag vortrag = Vortrag.findById(vortragId);
        Veranstaltung veranstaltung = Veranstaltung.findById(veranstaltungId);

        if (null == referent || null == vortrag || null == veranstaltung) {
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
        protokollService.log(ProtokollKategorie.VORTRAEGE, "Vortrag von Event abgemeldet", "Referent '" + loginName + "' hat Vortrag '" + titel + "' von Event '" + veranstaltung.getName() + "' abgemeldet.", vortragId, veranstaltung.getId());
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
        vortrag.setAbschluss(dto.abschluss);

        if (vortrag instanceof Wahlvortrag wahlvortrag) {
            wahlvortrag.setWiederholbar(dto.wiederholbar);
            wahlvortrag.setNeigungen(dto.neigungen);
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
    public boolean deleteVortrag(String loginName, Long vortragId) {
        Referent referent = Referent.find("loginName", loginName).firstResult();
        Vortrag vortrag = Vortrag.findById(vortragId);

        if (null == vortrag || !vortrag.getReferent().getId().equals(referent.getId())) {
            return false;
        }

        checkDeadline(vortrag.getVeranstaltung());

        Veranstaltung veranstaltung = vortrag.getVeranstaltung();
        String titel = vortrag.getTitel();
        vortrag.delete();

        if (veranstaltung.getBeginntAm().isAfter(LocalDateTime.now())) {
            mailService.sendVortragsRegistrierung(veranstaltung, referent, vortrag, false);
        }
        protokollService.log(ProtokollKategorie.VORTRAEGE, "Vortrag durch Referent gelöscht", "Referent '" + loginName + "' hat Vortrag '" + titel + "' gelöscht.", vortragId, veranstaltung.getId());

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
        if (null == veranstaltung) {
            throw new IllegalArgumentException("Veranstaltung nicht gefunden.");
        }

        try (Reader reader = CsvHelper.openCsvReader(csvFilePath)) {
            List<ReferentCsvDto> beans = new CsvToBeanBuilder<ReferentCsvDto>(reader)
                .withType(ReferentCsvDto.class)
                .withSeparator(';')
                .withFilter(line -> line.length > 0 && !line[0].startsWith("#"))
                .withIgnoreEmptyLine(true)
                .withIgnoreLeadingWhiteSpace(true)
                .build()
                .parse();

            for (ReferentCsvDto dto : beans) {
                if (StringUtils.isBlank(dto.loginName)) {
                    LOG.warn("Referent-Zeile übersprungen: loginName fehlt.");
                    continue;
                }
                String loginName = dto.loginName.trim().toLowerCase();

                Nutzer existingNutzer = Nutzer.findByLoginNameOrEmail(loginName, dto.email);
                Referent ref;
                if (null == existingNutzer) {
                    ref = new Referent();
                    ref.assignLoginName(loginName);
                    if (StringUtils.isNotBlank(dto.email)) {
                        ref.setEmail(dto.email.trim().toLowerCase());
                    }
                } else if (existingNutzer instanceof Referent) {
                    ref = (Referent) existingNutzer;
                } else {
                    LOG.warn("Nutzer mit loginName " + loginName + " oder gleicher E-Mail existiert bereits (loginName: '"
                        + existingNutzer.getLoginName() + "'), ist aber kein Referent. Überspringe.");
                    continue;
                }

                ref.setFirstName(dto.vorname);
                ref.setLastName(dto.nachname);
                ref.setJobRole(dto.position);
                ref.setOrganisation(dto.organisation);

                if (null == existingNutzer) {
                    keycloakUserProvisioningService.createUser(ref);
                }
                ref.persistAndFlush();
                ref.addVeranstaltung(veranstaltung);

                count++;
                protokollService.log(ProtokollKategorie.NUTZER, "Referent importiert", "Referent '" + ref.getLoginName() + "' via CSV importiert und Event '" + veranstaltung.getName() + "' zugewiesen.", ref.getId(), veranstaltung.getId());
            }
        }
        return count;
    }
}
