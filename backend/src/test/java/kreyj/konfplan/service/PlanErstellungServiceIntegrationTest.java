package kreyj.konfplan.service;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import kreyj.konfplan.application.service.MinizincException;
import kreyj.konfplan.application.service.PlanErstellungService;
import kreyj.konfplan.application.service.PlanService;
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
import kreyj.konfplan.presentation.DatabaseCleaner;
import kreyj.konfplan.presentation.dto.RaumBelegungUebersichtDto;
import kreyj.konfplan.presentation.dto.SolverConfigDto;
import org.jboss.logging.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.FileNotFoundException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;

import static java.util.stream.Collectors.joining;
import static kreyj.konfplan.presentation.dto.RaumBelegungUebersichtDto.VORTRAG_TITEL_FREI;
import static kreyj.konfplan.presentation.dto.RaumBelegungUebersichtDto.VORTRAG_TYP_FREI;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

@QuarkusTest
public class PlanErstellungServiceIntegrationTest extends DatabaseCleaner {
    private static final Logger LOG = Logger.getLogger(PlanErstellungServiceIntegrationTest.class);

    @Inject
    PlanErstellungService planErstellungService;

    @Inject
    PlanService planService;

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
        schule.persistAndFlush();

        Raum raum1 = new Raum("Raum 1", 1);
        raum1.persistAndFlush();
        Raum raum2 = new Raum("Raum 2", 2);
        raum2.persistAndFlush();
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
        veranstaltung.persistAndFlush();

        Slot slot1 = new Slot("Slot 1", LocalDateTime.now().plusHours(1),
                LocalDateTime.now().plusHours(2), veranstaltung);
        slot1.persistAndFlush();
        veranstaltung.addSlot(slot1);

        // 3. Referent und Vorträge
        Referent referent = new Referent();
        referent.setEmail("referent@test.com");
        referent.setFirstName("Max");
        referent.setLastName("Mustermann");
        referent.persistAndFlush();
        referent.addVeranstaltung(veranstaltung);

        Wahlvortrag wahlvortrag1 = new Wahlvortrag();
        wahlvortrag1.setTitel("Wahlvortrag 1");
        wahlvortrag1.setReferent(referent);
        wahlvortrag1.setVeranstaltung(veranstaltung);
        wahlvortrag1.persistAndFlush();

        // 4. Teilnehmer und Prioritäten
        Teilnehmer teilnehmer1 = new Teilnehmer();
        teilnehmer1.setEmail("tn1@test.com");
        teilnehmer1.setFirstName("Peter");
        teilnehmer1.setLastName("Pan");
        teilnehmer1.addGruppe("A");
        teilnehmer1.persistAndFlush();
        teilnehmer1.addVeranstaltung(veranstaltung);

        // Prioritäten für Teilnehmer 1
        new Prioritaet(teilnehmer1, wahlvortrag1, 1)
                .persistAndFlush();

        return veranstaltung;
    }

    @Transactional
    public Veranstaltung complexSetup() {
        // 2. Veranstaltung und Zeitslots
        Veranstaltung veranstaltung = new Veranstaltung();
        veranstaltung.setName("Komplexer Testlauf");
        veranstaltung.setBeginntAm(LocalDateTime.now());
        veranstaltung.addGebaeude(Gebaeude.findById(schule.getId()));
        veranstaltung.persistAndFlush();

        Slot slot1 = new Slot("Slot 1", LocalDateTime.now().plusHours(1),
                LocalDateTime.now().plusHours(2), veranstaltung);
        slot1.persistAndFlush();
        veranstaltung.addSlot(slot1);

        Slot slot2 = new Slot("Slot 2", LocalDateTime.now().plusHours(2),
                LocalDateTime.now().plusHours(3), veranstaltung);
        slot2.persistAndFlush();
        veranstaltung.addSlot(slot2);

        Slot slot3 = new Slot("Slot 3", LocalDateTime.now().plusHours(3),
                LocalDateTime.now().plusHours(4), veranstaltung);
        slot3.persistAndFlush();
        veranstaltung.addSlot(slot3);

        // 3. Referent und Vorträge
        Referent referent = new Referent();
        referent.setEmail("referent@test.com");
        referent.setFirstName("Max");
        referent.setLastName("Mustermann");
        referent.persistAndFlush();
        referent.addVeranstaltung(veranstaltung);

        Wahlvortrag wahlvortrag1 = new Wahlvortrag("Wahlvortrag 1", "Inhalt", referent,
                true, 1, veranstaltung);
        wahlvortrag1.persistAndFlush();
        veranstaltung.addVortrag(wahlvortrag1);

        Wahlvortrag wahlvortrag2 = new Wahlvortrag("Wahlvortrag 2", "Inhalt", referent,
                true, 1, veranstaltung);
        wahlvortrag2.persistAndFlush();
        veranstaltung.addVortrag(wahlvortrag2);

        Pflichtvortrag pflichtvortrag = new Pflichtvortrag("Pflichtvortrag", referent, veranstaltung,
                "A", schule.getRaeume().iterator().next(), slot3);
        pflichtvortrag.persistAndFlush();
        veranstaltung.addVortrag(pflichtvortrag);

        // 4. Teilnehmer und Prioritäten
        Teilnehmer teilnehmer1 = new Teilnehmer();
        teilnehmer1.setEmail("tn1@test.com");
        teilnehmer1.setFirstName("Peter");
        teilnehmer1.setLastName("Pan");
        teilnehmer1.persistAndFlush();

        teilnehmer1.addGruppe("A");
        teilnehmer1.addVeranstaltung(veranstaltung);

        Teilnehmer teilnehmer2 = new Teilnehmer();
        teilnehmer2.setEmail("tn2@test.com");
        teilnehmer2.setFirstName("Wendy");
        teilnehmer2.setLastName("Darling");
        teilnehmer2.persistAndFlush();

        teilnehmer2.addGruppe("A");
        teilnehmer2.addVeranstaltung(veranstaltung);

        // Prioritäten für TN 1
        new Prioritaet(teilnehmer1, wahlvortrag1, 1)
                .persistAndFlush();
        new Prioritaet(teilnehmer1, wahlvortrag2, 2)
                .persistAndFlush();

        // Priorität für TN 2
        new Prioritaet(teilnehmer2, wahlvortrag2, 1)
                .persistAndFlush();

        return veranstaltung;
    }

    @Test
    public void testPlanerstellung_withMinimalSetup() throws Exception {
        Veranstaltung veranstaltung = simpleSetup(true);

        // 1. PlanErstellung durchführen
        SolverConfigDto config = new SolverConfigDto("cp-sat", 10, 4, 1);
        planErstellungService.erstellePlan(veranstaltung.getId(), config);

        // 2. Ergebnis prüfen
        Planungsergebnis ergebnis = Planungsergebnis.find("veranstaltung", veranstaltung).firstResult();
        assertThat(ergebnis).describedAs("Planungsergebnis sollte nach der PlanErstellung vorhanden sein.").isNotNull();
        assertThat(ergebnis.getJsonErgebnis()).describedAs("Das JSON-Ergebnis im Planungsergebnis darf nicht null sein.").isNotNull();
        assertThat(ergebnis.getJsonErgebnis().contains("instanz_slot")).describedAs("Das JSON-Ergebnis sollte den Schlüssel 'instanz_slot' enthalten.").isTrue();

        // 3. Belegungsplan abrufen und prüfen
        List<RaumBelegungUebersichtDto> belegungsplan = planService.getDetaillierterPlan(veranstaltung);
        LOG.info("# Belegungsplan:\n  " + belegungsplan.stream().map(Object::toString).collect(joining("\n  ")));

        assertThat(belegungsplan).describedAs("Der Belegungsplan darf nicht null sein.").isNotNull();
        assertThat(belegungsplan).describedAs("Der Belegungsplan darf nicht leer sein.").isNotEmpty();

        // Konkrete Zuweisungen prüfen
        // Teilnehmer 1 sollte Wahlvortrag 1 bekommen (Prio 1)
        boolean tn1InWahlvortrag1 = belegungsplan.stream()
                .anyMatch(b -> "Wahlvortrag 1".equals(b.getVortragTitel()) && b.getTeilnehmerNamen().contains("Peter Pan"));
        assertThat(tn1InWahlvortrag1).describedAs("Teilnehmer 1 sollte dem Wahlvortrag 1 zugewiesen sein.").isTrue();
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
        SolverConfigDto config = new SolverConfigDto("cp-sat", 60, 4, 1);
        planErstellungService.erstellePlan(veranstaltung.getId(), config);

        // 2. Ergebnis prüfen
        Planungsergebnis ergebnis = Planungsergebnis.find("veranstaltung", veranstaltung).firstResult();
        assertThat(ergebnis).describedAs("Planungsergebnis sollte nach der PlanErstellung vorhanden sein.").isNotNull();
        assertThat(ergebnis.getJsonErgebnis()).describedAs("Das JSON-Ergebnis im Planungsergebnis darf nicht null sein.").isNotNull();
        assertThat(ergebnis.getJsonErgebnis().contains("instanz_slot")).describedAs("Das JSON-Ergebnis sollte den Schlüssel 'instanz_slot' enthalten.").isTrue();

        List<RaumBelegungUebersichtDto> belegungsplan = planService.getDetaillierterPlan(veranstaltung);
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
        SolverConfigDto config = new SolverConfigDto("cp-sat", 60, 4, 1);
        planErstellungService.erstellePlan(veranstaltung.getId(), config);

        // 2. Ergebnis prüfen
        Planungsergebnis ergebnis = Planungsergebnis.find("veranstaltung", veranstaltung).firstResult();
        assertThat(ergebnis).describedAs("Planungsergebnis sollte nach der PlanErstellung vorhanden sein.").isNotNull();
        assertThat(ergebnis.getJsonErgebnis()).describedAs("Das JSON-Ergebnis im Planungsergebnis darf nicht null sein.").isNotNull();
        assertThat(ergebnis.getJsonErgebnis().contains("instanz_slot")).describedAs("Das JSON-Ergebnis sollte den Schlüssel 'instanz_slot' enthalten.").isTrue();

        // 3. Belegungsplan abrufen und prüfen
        List<RaumBelegungUebersichtDto> belegungsplan = planService.getDetaillierterPlan(veranstaltung);

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
        SolverConfigDto config = new SolverConfigDto("cp-sat", 120, 4, 2);
        planErstellungService.erstellePlan(veranstaltung.getId(), config);

        // 2. Ergebnis prüfen
        Planungsergebnis ergebnis = Planungsergebnis.find("veranstaltung", veranstaltung).firstResult();
        assertThat(ergebnis).describedAs("Planungsergebnis sollte nach der PlanErstellung vorhanden sein.").isNotNull();
        assertThat(ergebnis.getJsonErgebnis()).describedAs("Das JSON-Ergebnis im Planungsergebnis darf nicht null sein.").isNotNull();
        assertThat(ergebnis.getJsonErgebnis().contains("instanz_slot")).describedAs("Das JSON-Ergebnis sollte den Schlüssel 'instanz_slot' enthalten.").isTrue();
        LOG.info("####### jsonErgebnis: " + ergebnis.getJsonErgebnis());

        // 3. Belegungsplan abrufen und prüfen
        List<RaumBelegungUebersichtDto> belegungsplan = planService.getDetaillierterPlan(veranstaltung);
        LOG.info("$$$$$$$ belegungsplan: " + belegungsplan);

        assertThat(belegungsplan).describedAs("Der Belegungsplan darf nicht null sein.").isNotNull();
        assertThat(belegungsplan.isEmpty()).describedAs("Der Belegungsplan darf nicht leer sein.").isFalse();

        // Konkrete Zuweisungen prüfen
        // Teilnehmer 1 sollte Wahlvortrag 1 bekommen (Prio 1)
        boolean tn1InWahlvortrag1 = belegungsplan.stream()
                .anyMatch(b -> "Wahlvortrag 1".equals(b.getVortragTitel()) && b.getTeilnehmerNamen().contains("Peter Pan"));
        assertThat(tn1InWahlvortrag1).describedAs("Teilnehmer 1 sollte dem Wahlvortrag 1 zugewiesen sein.").isTrue();

        // Teilnehmer 2 sollte Wahlvortrag 2 bekommen (Prio 1)
        boolean tn2InWahlvortrag2 = belegungsplan.stream()
                .anyMatch(b -> "Wahlvortrag 2".equals(b.getVortragTitel()) && b.getTeilnehmerNamen().contains("Wendy Darling"));
        assertThat(tn2InWahlvortrag2).describedAs("Teilnehmer 2 sollte dem Wahlvortrag 2 zugewiesen sein.").isTrue();

        // Beide Teilnehmer sollten im Pflichtvortrag sein
        long anzahlTnImPflichtvortrag = belegungsplan.stream()
                .filter(b -> "Pflichtvortrag".equals(b.getVortragTitel()))
                .map(RaumBelegungUebersichtDto::getTeilnehmerNamen)
                .flatMap(List::stream)
                .distinct()
                .count();
        assertThat(anzahlTnImPflichtvortrag)
                .describedAs("Beide Teilnehmer sollten dem Pflichtvortrag zugewiesen sein.")
                .isEqualTo(2);
    }

    @Test
    public void testPlanErstellung_withUnsatisfiableModel() {
        SolverConfigDto config = new SolverConfigDto("cp-sat", 5, 1, 1);

        assertThatExceptionOfType(MinizincException.class)
                .isThrownBy(() -> starteTestPlanErstellung(config, "unsatisfiable.mzn"));
    }

    @Test
    public void testPlanErstellung_withIntermediateResult() throws Exception {
        // Kurzer Timeout, um sicher eine Zwischenlösung zu erhalten
        SolverConfigDto config = new SolverConfigDto("cp-sat", 1, 1, 1);

        String resultJson = starteTestPlanErstellung(config, "intermediate.mzn");

        assertThat(resultJson).describedAs("Sollte ein Ergebnis (letzte Zwischenlösung) zurückgeben.").isNotNull();
        assertThat(resultJson.isEmpty()).describedAs("Das Ergebnis-JSON sollte nicht leer sein.").isFalse();
        assertThat(PlanErstellungService.isValidJson(resultJson)).describedAs("Das Ergebnis sollte valides JSON sein.").isTrue();
        assertThat(resultJson.contains("total_value")).describedAs("Das Ergebnis-JSON sollte 'total_value' enthalten.").isTrue();
    }

    @Test
    public void testPlanErstellung_withNoSolutionInTime() {
        // Sehr kurzer Timeout, damit garantiert keine Lösung gefunden wird
        SolverConfigDto config = new SolverConfigDto("cp-sat", 1, 1, 1);

        assertThatExceptionOfType(MinizincException.class)
                .isThrownBy(() -> starteTestPlanErstellung(config, "no-solution-in-time.mzn"));
    }

    // -------------------------------------------------------------------
    // Helper-Methoden für Test-Setups
    // -------------------------------------------------------------------

    public String starteTestPlanErstellung(SolverConfigDto config, String modelName) throws Exception {
        URL modelUrl = getClass().getClassLoader().getResource("minizinc/" + modelName);
        if (modelUrl == null) {
            throw new FileNotFoundException("MiniZinc model not found: " + modelName);
        }

        Path tempDzn = Files.createTempFile("planung_", ".dzn");
        Files.writeString(tempDzn, "%no data", StandardCharsets.UTF_8);

        try {
            return planErstellungService.rufeMiniZincAuf(Paths.get(modelUrl.toURI()),
                    tempDzn, config.solver, config.timeout, config.numThreads);

        } finally {
            Files.deleteIfExists(tempDzn);
        }
    }
}