package kreyj.konfplan.domain.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import kreyj.konfplan.adapter.in.web.dto.PlanExportMetadata;
import kreyj.konfplan.domain.exception.BusinessException;
import kreyj.konfplan.persistence.Gebaeude;
import kreyj.konfplan.persistence.Gebaeudetyp;
import kreyj.konfplan.persistence.NutzerVerfuegbarkeit;
import kreyj.konfplan.persistence.Pflichtvortrag;
import kreyj.konfplan.persistence.Planungsergebnis;
import kreyj.konfplan.persistence.Prioritaet;
import kreyj.konfplan.persistence.Raum;
import kreyj.konfplan.persistence.Referent;
import kreyj.konfplan.persistence.Slot;
import kreyj.konfplan.persistence.Teilnehmer;
import kreyj.konfplan.persistence.Veranstaltung;
import kreyj.konfplan.persistence.Wahlvortrag;
import kreyj.konfplan.adapter.in.web.DatabaseCleaner;
import kreyj.konfplan.adapter.in.web.dto.RaumBelegungUebersicht;
import kreyj.konfplan.adapter.in.web.dto.SolverConfig;
import org.jboss.logging.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static java.util.stream.Collectors.joining;
import static kreyj.konfplan.adapter.in.web.dto.RaumBelegungUebersicht.VORTRAG_TITEL_FREI;
import static kreyj.konfplan.adapter.in.web.dto.RaumBelegungUebersicht.VORTRAG_TYP_FREI;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

@QuarkusTest
public class PlanErstellungServiceIntegrationTest extends DatabaseCleaner {
    private static final Logger LOG = Logger.getLogger(PlanErstellungServiceIntegrationTest.class);

    @Inject
    PlanErstellungService planErstellungService;

    @Inject
    PlanService planService;

    @Inject
    ObjectMapper objectMapper;

    private Gebaeude schule;

    @BeforeEach
    @Transactional
    public void setup() {
        // 1. Schule (Gebäude) und Räume
        schule = new Gebaeude("Test Schule",
                "Testort",
                "Teststrasse",
                "4711",
                Gebaeudetyp.SCHULE);
        schule.persist();

        Raum raum1 = new Raum("Raum 1", 1);
        raum1.persist();
        Raum raum2 = new Raum("Raum 2", 2);
        raum2.persist();
        schule.addRaum(raum1);
        schule.addRaum(raum2);
    }

    @Transactional
    public Veranstaltung simpleSetup(boolean satisfiable) {
        // 2. Veranstaltung und Zeitslots
        Veranstaltung veranstaltung = new Veranstaltung();
        veranstaltung.setName((satisfiable ? "E" : "Une") + "rfüllbarer Testlauf");
        veranstaltung.setBeginntAm(LocalDateTime.now());
        veranstaltung.addGebaeude(Gebaeude.findById(schule.getId()));
        veranstaltung.persist();

        Slot slot1 = new Slot("Slot 1", LocalDateTime.now().plusHours(1),
                LocalDateTime.now().plusHours(2), veranstaltung);
        slot1.persist();
        veranstaltung.addSlot(slot1);

        // 3. Referent und Vorträge
        Referent referent = new Referent();
        referent.assignLoginName("referent@test.com");
        referent.setEmail("referent@test.com");
        referent.setFirstName("Max");
        referent.setLastName("Mustermann");
        referent.persist();
        referent.addVeranstaltung(veranstaltung);

        Wahlvortrag wahlvortrag1 = new Wahlvortrag();
        wahlvortrag1.setTitel("Wahlvortrag 1");
        wahlvortrag1.setReferent(referent);
        wahlvortrag1.setVeranstaltung(veranstaltung);
        wahlvortrag1.persist();

        // 4. Teilnehmer und Prioritäten
        Teilnehmer teilnehmer1 = new Teilnehmer();
        teilnehmer1.assignLoginName("tn1@test.com");
        teilnehmer1.setEmail("tn1@test.com");
        teilnehmer1.setFirstName("Peter");
        teilnehmer1.setLastName("Pan");
        teilnehmer1.addGruppe("A");
        teilnehmer1.persist();
        teilnehmer1.addVeranstaltung(veranstaltung);

        // Prioritäten für Teilnehmer 1
        new Prioritaet(teilnehmer1, wahlvortrag1, 1)
                .persist();

        return veranstaltung;
    }

    @Transactional
    public Veranstaltung complexSetup() {
        // 2. Veranstaltung und Zeitslots
        Veranstaltung veranstaltung = new Veranstaltung();
        veranstaltung.setName("Komplexer Testlauf");
        veranstaltung.setBeginntAm(LocalDateTime.now());
        veranstaltung.addGebaeude(Gebaeude.findById(schule.getId()));
        veranstaltung.persist();

        Slot slot1 = new Slot("Slot 1", LocalDateTime.now().plusHours(1),
                LocalDateTime.now().plusHours(2), veranstaltung);
        slot1.persist();
        veranstaltung.addSlot(slot1);

        Slot slot2 = new Slot("Slot 2", LocalDateTime.now().plusHours(2),
                LocalDateTime.now().plusHours(3), veranstaltung);
        slot2.persist();
        veranstaltung.addSlot(slot2);

        Slot slot3 = new Slot("Slot 3", LocalDateTime.now().plusHours(3),
                LocalDateTime.now().plusHours(4), veranstaltung);
        slot3.persist();
        veranstaltung.addSlot(slot3);

        // 3. Referent und Vorträge
        Referent referent = new Referent();
        referent.assignLoginName("referent@test.com");
        referent.setEmail("referent@test.com");
        referent.setFirstName("Max");
        referent.setLastName("Mustermann");
        referent.persist();
        referent.addVeranstaltung(veranstaltung);

        Wahlvortrag wahlvortrag1 = Wahlvortrag.create("Wahlvortrag 1", "Inhalt", referent,
                true, 1, veranstaltung);
        wahlvortrag1.persist();
        veranstaltung.addVortrag(wahlvortrag1);

        Wahlvortrag wahlvortrag2 = Wahlvortrag.create("Wahlvortrag 2", "Inhalt", referent,
                true, 1, veranstaltung);
        wahlvortrag2.persist();
        veranstaltung.addVortrag(wahlvortrag2);

        // 4. Teilnehmer und Prioritäten
        Teilnehmer teilnehmer1 = new Teilnehmer();
        teilnehmer1.assignLoginName("tn1@test.com");
        teilnehmer1.setEmail("tn1@test.com");
        teilnehmer1.setFirstName("Peter");
        teilnehmer1.setLastName("Pan");
        teilnehmer1.persist();

        teilnehmer1.addGruppe("A");
        teilnehmer1.addVeranstaltung(veranstaltung);

        Teilnehmer teilnehmer2 = new Teilnehmer();
        teilnehmer2.assignLoginName("tn2@test.com");
        teilnehmer2.setEmail("tn2@test.com");
        teilnehmer2.setFirstName("Wendy");
        teilnehmer2.setLastName("Darling");
        teilnehmer2.persist();

        teilnehmer2.addGruppe("A");
        teilnehmer2.addVeranstaltung(veranstaltung);

        // Pflichtvortrag erst anlegen, nachdem die Teilnehmer in Gruppe "A" und der Veranstaltung sind,
        // damit Pflichtvortrag.create() den Pflichtslot konsistent aus ihren Verfügbarkeiten entfernt.
        Pflichtvortrag pflichtvortrag = Pflichtvortrag.create("Pflichtvortrag", "Inhalt", referent,
                "A", schule.getRaeume().iterator().next(), slot3, veranstaltung);
        pflichtvortrag.persist();
        veranstaltung.addVortrag(pflichtvortrag);

        // Prioritäten für TN 1: Wahlvortrag 1 ist die höhere (bessere) Priorität.
        new Prioritaet(teilnehmer1, wahlvortrag1, 2)
                .persist();
        new Prioritaet(teilnehmer1, wahlvortrag2, 1)
                .persist();

        // Priorität für TN 2 (einzige Priorität)
        new Prioritaet(teilnehmer2, wahlvortrag2, 1)
                .persist();

        return veranstaltung;
    }

    @Transactional
    public Veranstaltung fillUpSetup() {
        // Veranstaltung mit einem Wahlvortrag, einem Teilnehmer mit Priorität und einem
        // freien (nicht priorisierten, aber verfügbaren) Teilnehmer für die Auffüllungs-Tests.
        Veranstaltung veranstaltung = new Veranstaltung();
        veranstaltung.setName("Auffuellungs-Testlauf");
        veranstaltung.setBeginntAm(LocalDateTime.now());
        veranstaltung.addGebaeude(Gebaeude.findById(schule.getId()));
        veranstaltung.persist();

        Slot slot1 = new Slot("Slot 1", LocalDateTime.now().plusHours(1),
                LocalDateTime.now().plusHours(2), veranstaltung);
        slot1.persist();
        veranstaltung.addSlot(slot1);

        Referent referent = new Referent();
        referent.assignLoginName("referent@test.com");
        referent.setEmail("referent@test.com");
        referent.setFirstName("Max");
        referent.setLastName("Mustermann");
        referent.persist();
        referent.addVeranstaltung(veranstaltung);

        Wahlvortrag wahlvortrag1 = new Wahlvortrag();
        wahlvortrag1.setTitel("Wahlvortrag 1");
        wahlvortrag1.setReferent(referent);
        wahlvortrag1.setVeranstaltung(veranstaltung);
        wahlvortrag1.persist();

        Teilnehmer teilnehmerMitPrio = new Teilnehmer();
        teilnehmerMitPrio.assignLoginName("tn1@test.com");
        teilnehmerMitPrio.setEmail("tn1@test.com");
        teilnehmerMitPrio.setFirstName("Peter");
        teilnehmerMitPrio.setLastName("Pan");
        teilnehmerMitPrio.addGruppe("A");
        teilnehmerMitPrio.persist();
        teilnehmerMitPrio.addVeranstaltung(veranstaltung);

        Teilnehmer teilnehmerFrei = new Teilnehmer();
        teilnehmerFrei.assignLoginName("tn2@test.com");
        teilnehmerFrei.setEmail("tn2@test.com");
        teilnehmerFrei.setFirstName("Wendy");
        teilnehmerFrei.setLastName("Darling");
        teilnehmerFrei.addGruppe("A");
        teilnehmerFrei.persist();
        teilnehmerFrei.addVeranstaltung(veranstaltung);

        new Prioritaet(teilnehmerMitPrio, wahlvortrag1, 1)
                .persist();

        return veranstaltung;
    }

    @Transactional
    public Veranstaltung referentDoppelbuchungSetup() {
        // Ein Referent hält einen Pflichtvortrag in Slot 3 UND ist Referent eines Wahlvortrags,
        // der ohne den Fix ebenfalls in Slot 3 landen könnte. Eine separate, nicht überlappende
        // Teilnehmergruppe für den Pflichtvortrag verhindert eine Vermischung mit der
        // Teilnehmer-Verfügbarkeits-Constraint.
        Veranstaltung veranstaltung = new Veranstaltung();
        veranstaltung.setName("Referenten-Doppelbuchung-Testlauf");
        veranstaltung.setBeginntAm(LocalDateTime.now());
        veranstaltung.addGebaeude(Gebaeude.findById(schule.getId()));
        veranstaltung.persist();

        Slot slot1 = new Slot("Slot 1", LocalDateTime.now().plusHours(1),
                LocalDateTime.now().plusHours(2), veranstaltung);
        slot1.persist();
        veranstaltung.addSlot(slot1);

        Slot slot2 = new Slot("Slot 2", LocalDateTime.now().plusHours(2),
                LocalDateTime.now().plusHours(3), veranstaltung);
        slot2.persist();
        veranstaltung.addSlot(slot2);

        Slot slot3 = new Slot("Slot 3", LocalDateTime.now().plusHours(3),
                LocalDateTime.now().plusHours(4), veranstaltung);
        slot3.persist();
        veranstaltung.addSlot(slot3);

        Referent referent = new Referent();
        referent.assignLoginName("referent@test.com");
        referent.setEmail("referent@test.com");
        referent.setFirstName("Max");
        referent.setLastName("Mustermann");
        referent.persist();
        referent.addVeranstaltung(veranstaltung);

        Wahlvortrag wahlvortrag1 = new Wahlvortrag();
        wahlvortrag1.setTitel("Wahlvortrag 1");
        wahlvortrag1.setReferent(referent);
        wahlvortrag1.setVeranstaltung(veranstaltung);
        wahlvortrag1.persist();

        Teilnehmer teilnehmerPflicht = new Teilnehmer();
        teilnehmerPflicht.assignLoginName("tnP@test.com");
        teilnehmerPflicht.setEmail("tnP@test.com");
        teilnehmerPflicht.setFirstName("Petra");
        teilnehmerPflicht.setLastName("Pflicht");
        teilnehmerPflicht.addGruppe("P");
        teilnehmerPflicht.persist();
        teilnehmerPflicht.addVeranstaltung(veranstaltung);

        Teilnehmer teilnehmerWahl = new Teilnehmer();
        teilnehmerWahl.assignLoginName("tnW@test.com");
        teilnehmerWahl.setEmail("tnW@test.com");
        teilnehmerWahl.setFirstName("Wanda");
        teilnehmerWahl.setLastName("Wahl");
        teilnehmerWahl.persist();
        teilnehmerWahl.addVeranstaltung(veranstaltung);

        new Prioritaet(teilnehmerWahl, wahlvortrag1, 1)
                .persist();

        // Pflichtvortrag NACH addVeranstaltung anlegen, damit initReferentVerfuegbarkeit() greift.
        Pflichtvortrag.create("Pflichtvortrag", "Inhalt", referent, "P",
                schule.getRaeume().iterator().next(), slot3, veranstaltung);

        return veranstaltung;
    }

    @Test
    public void testPlanerstellung_referentNichtDoppeltGebucht() throws Exception {
        Veranstaltung veranstaltung = referentDoppelbuchungSetup();
        Slot pflichtslot = veranstaltung.getSlots().stream()
                .sorted(Comparator.comparing(Slot::getStartTime))
                .toList()
                .get(2); // Slot 3 (per Setup mit dem Pflichtvortrag belegt)

        SolverConfig config = new SolverConfig(120, 4, 1);
        erstellePlanUndPubliziere(veranstaltung, config);

        List<RaumBelegungUebersicht> belegungsplan = planService.getDetaillierterPlan(veranstaltung);
        List<RaumBelegungUebersicht> wahlvortrag1Eintraege = belegungsplan.stream()
                .filter(b -> "Wahlvortrag 1".equals(b.vortragTitel))
                .toList();

        assertThat(wahlvortrag1Eintraege)
                .describedAs("Wahlvortrag 1 sollte trotz Referenten-Bindung an Slot 3 irgendwo eingeplant werden")
                .isNotEmpty();
        assertThat(wahlvortrag1Eintraege)
                .describedAs("Wahlvortrag 1 darf niemals im Pflicht-Slot des Referenten (Slot 3) liegen")
                .noneMatch(b -> pflichtslot.getId().equals(b.slotId));
    }

    @Test
    public void testAuffuellung_freierTeilnehmerWirdEingeplant() throws Exception {
        Veranstaltung veranstaltung = fillUpSetup();

        // auffuellen=true (Default des 3-Parameter-Konstruktors)
        SolverConfig config = new SolverConfig(60, 4, 1);
        erstellePlanUndPubliziere(veranstaltung, config);

        List<RaumBelegungUebersicht> belegungsplan = planService.getDetaillierterPlan(veranstaltung);

        boolean beideInWahlvortrag1 = belegungsplan.stream()
                .filter(b -> "Wahlvortrag 1".equals(b.getVortragTitel()))
                .anyMatch(b -> b.getTeilnehmerNamen().contains("Pan, Peter")
                        && b.getTeilnehmerNamen().contains("Darling, Wendy"));
        assertThat(beideInWahlvortrag1)
                .describedAs("Der freie Teilnehmer Wendy Darling sollte per Auffüllung ebenfalls in Wahlvortrag 1 eingeplant werden.")
                .isTrue();
    }

    @Test
    public void testAuffuellung_deaktiviert_freierTeilnehmerBleibtUnbesetzt() throws Exception {
        Veranstaltung veranstaltung = fillUpSetup();

        SolverConfig config = new SolverConfig(60, 4, 1, false);
        erstellePlanUndPubliziere(veranstaltung, config);

        List<RaumBelegungUebersicht> belegungsplan = planService.getDetaillierterPlan(veranstaltung);

        boolean wendyEingeplant = belegungsplan.stream()
                .anyMatch(b -> b.getTeilnehmerNamen().contains("Darling, Wendy"));
        assertThat(wendyEingeplant)
                .describedAs("Ohne Auffüllung darf der freie Teilnehmer Wendy Darling in keinem Vortrag auftauchen.")
                .isFalse();
    }

    @Test
    public void testPlanerstellungSmallSetup() throws Exception {
        Veranstaltung veranstaltung = simpleSetup(true);

        // 1. PlanErstellung durchführen
        SolverConfig config = new SolverConfig(10, 4, 1);
        erstellePlanUndPubliziere(veranstaltung, config);

        // 2. Ergebnis prüfen
        Planungsergebnis ergebnis = Planungsergebnis.find("veranstaltung", veranstaltung).firstResult();
        assertThat(ergebnis).describedAs("Planungsergebnis sollte nach der PlanErstellung vorhanden sein.").isNotNull();
        assertThat(ergebnis.getJsonErgebnis()).describedAs("Das JSON-Ergebnis im Planungsergebnis darf nicht null sein.").isNotNull();
        assertThat(ergebnis.getJsonErgebnis().contains("instanz_slot")).describedAs("Das JSON-Ergebnis sollte den Schlüssel 'instanz_slot' enthalten.").isTrue();

        // 3. Belegungsplan abrufen und prüfen
        List<RaumBelegungUebersicht> belegungsplan = planService.getDetaillierterPlan(veranstaltung);

        assertThat(belegungsplan).describedAs("Der Belegungsplan darf nicht null sein.").isNotNull();
        assertThat(belegungsplan).describedAs("Der Belegungsplan darf nicht leer sein.").isNotEmpty();

        // Konkrete Zuweisungen prüfen
        // Teilnehmer 1 sollte Wahlvortrag 1 bekommen (einzige Priorität)
        boolean tn1InWahlvortrag1 = belegungsplan.stream()
                .anyMatch(b -> "Wahlvortrag 1".equals(b.getVortragTitel()) && b.getTeilnehmerNamen().contains("Pan, Peter"));
        assertThat(tn1InWahlvortrag1).describedAs("Titel ist WV1 und teilnehmer enthalten").isTrue();
    }

    @Test
    public void testGeneriereDznVorschau_liefertDznContentOhneSolverAufruf() {
        Veranstaltung veranstaltung = simpleSetup(true);

        SolverConfig config = new SolverConfig(10, 4, 1);
        String dznContent = planErstellungService.generiereDznVorschau(veranstaltung.getId(), config, "username");

        assertThat(dznContent).describedAs("DZN-Content darf nicht null sein.").isNotNull();
        assertThat(dznContent).contains("n_slots = " + veranstaltung.getSlots().size());
        assertThat(dznContent).contains("max_instanzen = 1;");
        // Kein Planungsergebnis darf durch die reine Vorschau entstehen.
        Planungsergebnis ergebnis = Planungsergebnis.find("veranstaltung", veranstaltung).firstResult();
        assertThat(ergebnis).isNull();
    }

    @Test
    public void testGeneriereDznVorschau_wirftBeiFehlendenVoraussetzungen() {
        Veranstaltung veranstaltung = new Veranstaltung();
        veranstaltung.setName("Leere Veranstaltung");
        veranstaltung.setBeginntAm(LocalDateTime.now());
        QuarkusTransaction.requiringNew().run(veranstaltung::persist);

        SolverConfig config = new SolverConfig(10, 4, 1);

        assertThatExceptionOfType(kreyj.konfplan.domain.exception.BusinessException.class)
                .isThrownBy(() -> planErstellungService.generiereDznVorschau(veranstaltung.getId(), config, "username"));
    }

    @Test
    public void testPlanerstellung_noAvailabilities() throws Exception {
        Veranstaltung veranstaltung = simpleSetup(false);
        Teilnehmer tn = Teilnehmer.<Teilnehmer>listAll().getFirst();

        // tn ist nicht verfügbar in allen V-Slots
        QuarkusTransaction.requiringNew().run(() -> {
            veranstaltung.getSlots().forEach(slot -> tn.updateVerfuegbarkeit(slot, veranstaltung, false));
        });

        NutzerVerfuegbarkeit nv = tn.getVerfuegbarkeit(veranstaltung);
        assertThat(nv).isNotNull();
        assertThat(nv.getVerfuegbareSlotIds()).isEmpty();

        // 1. PlanErstellung durchführen
        SolverConfig config = new SolverConfig(60, 4, 1);
        erstellePlanUndPubliziere(veranstaltung, config);

        // 2. Ergebnis prüfen
        Planungsergebnis ergebnis = Planungsergebnis.find("veranstaltung", veranstaltung).firstResult();
        assertThat(ergebnis).describedAs("Planungsergebnis sollte nach der PlanErstellung vorhanden sein.").isNotNull();
        assertThat(ergebnis.getJsonErgebnis()).describedAs("Das JSON-Ergebnis im Planungsergebnis darf nicht null sein.").isNotNull();
        assertThat(ergebnis.getJsonErgebnis().contains("instanz_slot")).describedAs("Das JSON-Ergebnis sollte den Schlüssel 'instanz_slot' enthalten.").isTrue();

        List<RaumBelegungUebersicht> belegungsplan = planService.getDetaillierterPlan(veranstaltung);
        assertThat(belegungsplan).describedAs("Der Belegungsplan darf nicht leer sein.").isNotEmpty();
        assertThat(belegungsplan).hasSize(veranstaltung.getSlots().size()
                * veranstaltung.getGebaeude().stream().mapToInt(g -> g.getRaeume().size()).sum());
        assertThat(belegungsplan)
                .allMatch(b ->
                        VORTRAG_TITEL_FREI.equals(b.vortragTitel)
                                && VORTRAG_TYP_FREI.equals(b.vortragTyp));
    }

    @Test
    public void testPlanerstellung_withoutResult() throws Exception {
        Veranstaltung veranstaltung = simpleSetup(false);
        Teilnehmer tn = Teilnehmer.<Teilnehmer>listAll().getFirst();

        // tn ist nicht verfügbar in allen V-Slots
        QuarkusTransaction.requiringNew().run(() -> {
            veranstaltung.getSlots().forEach(slot -> tn.updateVerfuegbarkeit(slot, veranstaltung, false));
        });

        NutzerVerfuegbarkeit nv = tn.getVerfuegbarkeit(veranstaltung);
        assertThat(nv).isNotNull();

        // 1. PlanErstellung durchführen
        SolverConfig config = new SolverConfig(60, 4, 1);
        erstellePlanUndPubliziere(veranstaltung, config);

        // 2. Ergebnis prüfen
        Planungsergebnis ergebnis = Planungsergebnis.find("veranstaltung", veranstaltung).firstResult();
        assertThat(ergebnis).describedAs("Planungsergebnis sollte nach der PlanErstellung vorhanden sein.").isNotNull();
        assertThat(ergebnis.getJsonErgebnis()).describedAs("Das JSON-Ergebnis im Planungsergebnis darf nicht null sein.").isNotNull();
        assertThat(ergebnis.getJsonErgebnis().contains("instanz_slot")).describedAs("Das JSON-Ergebnis sollte den Schlüssel 'instanz_slot' enthalten.").isTrue();

        // 3. Belegungsplan abrufen und prüfen
        List<RaumBelegungUebersicht> belegungsplan = planService.getDetaillierterPlan(veranstaltung);

        assertThat(belegungsplan).describedAs("Der Belegungsplan darf nicht leer sein.").isNotEmpty();
        assertThat(belegungsplan).hasSize(veranstaltung.getSlots().size()
                * veranstaltung.getGebaeude().stream().mapToInt(g -> g.getRaeume().size()).sum());
        assertThat(belegungsplan)
                .allMatch(b ->
                        VORTRAG_TITEL_FREI.equals(b.vortragTitel)
                                && VORTRAG_TYP_FREI.equals(b.vortragTyp));
    }

    @Test
    public void testPlanerstellung_withComplexSetup() throws Exception {
        Veranstaltung veranstaltung = complexSetup();
        // 1. PlanErstellung durchführen
        SolverConfig config = new SolverConfig(120, 4, 2);
        erstellePlanUndPubliziere(veranstaltung, config);

        // 2. Ergebnis prüfen
        Planungsergebnis ergebnis = Planungsergebnis.find("veranstaltung", veranstaltung).firstResult();
        assertThat(ergebnis).describedAs("Planungsergebnis sollte nach der PlanErstellung vorhanden sein.").isNotNull();
        assertThat(ergebnis.getJsonErgebnis()).describedAs("Das JSON-Ergebnis im Planungsergebnis darf nicht null sein.").isNotNull();
        assertThat(ergebnis.getJsonErgebnis().contains("instanz_slot")).describedAs("Das JSON-Ergebnis sollte den Schlüssel 'instanz_slot' enthalten.").isTrue();

        // 3. Belegungsplan abrufen und prüfen
        List<RaumBelegungUebersicht> belegungsplan = planService.getDetaillierterPlan(veranstaltung);

        assertThat(belegungsplan).describedAs("Der Belegungsplan darf nicht null sein.").isNotNull();
        assertThat(belegungsplan.isEmpty()).describedAs("Der Belegungsplan darf nicht leer sein.").isFalse();

        // Konkrete Zuweisungen prüfen
        // Teilnehmer 1 sollte Wahlvortrag 1 bekommen (höhere Priorität: 2 vs. 1)
        boolean tn1InWahlvortrag1 = belegungsplan.stream()
                .anyMatch(b -> "Wahlvortrag 1".equals(b.getVortragTitel()) && b.getTeilnehmerNamen().contains("Pan, Peter"));
        assertThat(tn1InWahlvortrag1).describedAs("Teilnehmer 1 sollte dem Wahlvortrag 1 zugewiesen sein.").isTrue();

        // Teilnehmer 2 sollte Wahlvortrag 2 bekommen (einzige Priorität)
        boolean tn2InWahlvortrag2 = belegungsplan.stream()
                .anyMatch(b -> "Wahlvortrag 2".equals(b.getVortragTitel()) && b.getTeilnehmerNamen().contains("Darling, Wendy"));
        assertThat(tn2InWahlvortrag2).describedAs("Teilnehmer 2 sollte dem Wahlvortrag 2 zugewiesen sein.").isTrue();

        // Beide Teilnehmer sollten im Pflichtvortrag sein
        long anzahlTnImPflichtvortrag = belegungsplan.stream()
                .filter(b -> "Pflichtvortrag".equals(b.getVortragTitel()))
                .map(RaumBelegungUebersicht::getTeilnehmerNamen)
                .flatMap(List::stream)
                .distinct()
                .count();
        assertThat(anzahlTnImPflichtvortrag)
                .describedAs("Beide Teilnehmer sollten dem Pflichtvortrag zugewiesen sein.")
                .isEqualTo(2);
    }

    @Test
    public void testPlanErstellung_withUnsatisfiableModel() {
        SolverConfig config = new SolverConfig(5, 1, 1);

        assertThatExceptionOfType(MinizincException.class)
                .isThrownBy(() -> starteTestPlanErstellung(config, "unsatisfiable.mzn"));
    }

    @Test
    public void testPlanErstellung_withIntermediateResult() throws Exception {
        // Limit als Obergrenze (5s, ~5x über der früheren 1s-Flake-Schwelle): cp-sat gibt
        // für das große Rucksackproblem zuverlässig mindestens eine (Zwischen-)Lösung aus,
        // sodass auch Solver-Start unter Testlast das Ergebnis nie "leer" lässt.
        SolverConfig config = new SolverConfig(5, 1, 1);

        String resultJson = starteTestPlanErstellung(config, "intermediate.mzn");

        assertThat(resultJson).describedAs("Sollte ein Ergebnis (letzte Zwischenlösung) zurückgeben.").isNotNull();
        assertThat(resultJson.isEmpty()).describedAs("Das Ergebnis-JSON sollte nicht leer sein.").isFalse();
        assertThat(PlanErstellungService.isValidJson(resultJson)).describedAs("Das Ergebnis sollte valides JSON sein.").isTrue();
        assertThat(resultJson.contains("total_value")).describedAs("Das Ergebnis-JSON sollte 'total_value' enthalten.").isTrue();
    }

    @Test
    public void testPlanErstellung_withNoSolutionInTime() {
        // Erfüllbares, aber im Zeitlimit unauffindbares Modell -> cp-sat liefert "UNKNOWN".
        // Erwartet: TIMEOUT-Exception mit Hinweis auf das konfigurierte Zeitlimit (vs. beweisbar
        // UNSATISFIABLE, siehe testPlanErstellung_withUnsatisfiableModel), damit die UI dem
        // Nutzer mitteilen kann, dass in der vorgegebenen Zeit kein Ergebnis berechnet werden konnte.
        SolverConfig config = new SolverConfig(1, 1, 1);

        assertThatExceptionOfType(MinizincException.class)
                .isThrownBy(() -> starteTestPlanErstellung(config, "no-solution-in-time.mzn"))
                .satisfies(e -> {
                    assertThat(e.getExceptionType()).isEqualTo(MinizincException.MZ_Exception.TIMEOUT);
                    assertThat(e.getMessage()).contains("1 Sek.");
                });
    }

    @Test
    public void testExportUndImportBundle_ergibtGueltigesErgebnis() throws Exception {
        Veranstaltung veranstaltung = simpleSetup(true);
        SolverConfig config = new SolverConfig(10, 4, 1);

        byte[] exportZip = planErstellungService.erstelleExportBundle(veranstaltung.getId(), config, "username");
        String dznContent = new String(extractZipEntry(exportZip, "veranstaltung.dzn"), StandardCharsets.UTF_8);
        assertThat(dznContent).describedAs("Export-Bundle sollte eine .dzn-Datei enthalten.").isNotEmpty();

        // Die Berechnung, die auf dem externen (Hochleistungs-)Rechner passieren würde: echte
        // minizinc-CLI direkt gegen die exportierte .dzn-Datei aufrufen.
        URL modelUrl = getClass().getClassLoader().getResource("minizinc/konfplan.mzn");
        Path tempDzn = Files.createTempFile("planung_export_", ".dzn");
        Files.writeString(tempDzn, dznContent, StandardCharsets.UTF_8);
        String rohErgebnis;
        try {
            rohErgebnis = planErstellungService.rufeMiniZincAuf(Paths.get(modelUrl.toURI()), tempDzn, config);
        } finally {
            Files.deleteIfExists(tempDzn);
        }

        // Import-Paket bauen: unveränderte metadata.json aus dem Export + die rohe Solver-Ausgabe.
        Path importZip = Files.createTempFile("ergebnis_import_", ".zip");
        try {
            try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(importZip))) {
                zos.putNextEntry(new ZipEntry("metadata.json"));
                zos.write(extractZipEntry(exportZip, "metadata.json"));
                zos.closeEntry();
                zos.putNextEntry(new ZipEntry("ergebnis.json"));
                zos.write(rohErgebnis.getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            }

            planErstellungService.importErgebnisBundle(veranstaltung.getId(), importZip, "username");
        } finally {
            Files.deleteIfExists(importZip);
        }

        Planungsergebnis ergebnis = Planungsergebnis.find("veranstaltung", veranstaltung).firstResult();
        assertThat(ergebnis).describedAs("Planungsergebnis sollte nach dem Import vorhanden sein.").isNotNull();
        assertThat(ergebnis.getJsonErgebnis()).contains("instanz_slot");
        // Seit Issue #461 startet auch ein importiertes Ergebnis unpubliziert - explizit
        // veröffentlichen, damit getDetaillierterPlan (liest nur das veröffentlichte Ergebnis) es sieht.
        planService.publiziereErgebnis(veranstaltung, ergebnis.getId());

        List<RaumBelegungUebersicht> belegungsplan = planService.getDetaillierterPlan(veranstaltung);
        boolean tn1InWahlvortrag1 = belegungsplan.stream()
                .anyMatch(b -> "Wahlvortrag 1".equals(b.getVortragTitel()) && b.getTeilnehmerNamen().contains("Pan, Peter"));
        assertThat(tn1InWahlvortrag1)
                .describedAs("Der importierte Plan sollte identisch zu einem lokal berechneten Plan sein.")
                .isTrue();
    }

    @Test
    public void testImportErgebnisBundle_wirftBeiVeraenderterVeranstaltung() throws Exception {
        Veranstaltung veranstaltung = simpleSetup(true);
        SolverConfig config = new SolverConfig(10, 4, 1);
        byte[] exportZip = planErstellungService.erstelleExportBundle(veranstaltung.getId(), config, "username");

        // Metadaten manipulieren: eine Teilnehmer-Oid faelschen, als wäre zwischen Export und
        // Import ein anderer Teilnehmer angelegt/gelöscht worden.
        PlanExportMetadata metadata = objectMapper.readValue(extractZipEntry(exportZip, "metadata.json"), PlanExportMetadata.class);
        metadata.setTeilnehmerOids(List.of(999999L));

        String dummyErgebnis = "{\"instanz_slot\":[0],\"instanz_raum\":[0],\"besucht\":[false],\"guete\":0,\"zuweisungen\":0,\"raumwechsel\":0}";

        Path importZip = Files.createTempFile("ergebnis_import_tampered_", ".zip");
        try {
            try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(importZip))) {
                zos.putNextEntry(new ZipEntry("metadata.json"));
                zos.write(objectMapper.writeValueAsBytes(metadata));
                zos.closeEntry();
                zos.putNextEntry(new ZipEntry("ergebnis.json"));
                zos.write(dummyErgebnis.getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            }

            assertThatExceptionOfType(BusinessException.class)
                    .isThrownBy(() -> planErstellungService.importErgebnisBundle(veranstaltung.getId(), importZip, "username"));
        } finally {
            Files.deleteIfExists(importZip);
        }

        Planungsergebnis ergebnis = Planungsergebnis.find("veranstaltung", veranstaltung).firstResult();
        assertThat(ergebnis)
                .describedAs("Bei fehlgeschlagenem Konsistenz-Check darf kein Planungsergebnis gespeichert werden.")
                .isNull();
    }

    private static byte[] extractZipEntry(byte[] zipBytes, String entryName) throws java.io.IOException {
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entryName.equals(entry.getName())) {
                    return zis.readAllBytes();
                }
            }
        }
        throw new AssertionError("Zip-Eintrag nicht gefunden: " + entryName);
    }

    // -------------------------------------------------------------------
    // Helper-Methoden für Test-Setups
    // -------------------------------------------------------------------

    /**
     * Ruft erstellePlan auf und veröffentlicht das neu erzeugte Ergebnis anschließend sofort -
     * seit Issue #461 startet ein neuer Planungslauf unpubliziert (siehe
     * PlanErstellungService#speicherePlanungsergebnis), die Report-/Belegungsplan-Methoden in
     * PlanService liefern aber nur noch das veröffentlichte Ergebnis. Diese Tests prüfen die
     * Solver-Korrektheit, nicht den Publish-Workflow, daher hier immer sofort veröffentlichen.
     */
    private void erstellePlanUndPubliziere(Veranstaltung veranstaltung, SolverConfig config) throws Exception {
        planErstellungService.erstellePlan(veranstaltung.getId(), config, "username");
        Planungsergebnis neuestesErgebnis = Planungsergebnis.<Planungsergebnis>find("veranstaltung = ?1 order by id desc", veranstaltung)
                .firstResult();
        planService.publiziereErgebnis(veranstaltung, neuestesErgebnis.getId());
    }


    public String starteTestPlanErstellung(SolverConfig config, String modelName) throws Exception {
        URL modelUrl = getClass().getClassLoader().getResource("minizinc/" + modelName);
        if (null == modelUrl) {
            throw new FileNotFoundException("MiniZinc model not found: " + modelName);
        }

        Path tempDzn = Files.createTempFile("planung_", ".dzn");
        Files.writeString(tempDzn, "%no data", StandardCharsets.UTF_8);

        try {
            return planErstellungService.rufeMiniZincAuf(Paths.get(modelUrl.toURI()),
                    tempDzn, config);

        } finally {
            Files.deleteIfExists(tempDzn);
        }
    }
}
