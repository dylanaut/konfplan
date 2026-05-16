package kreyj.konfplan.service;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import kreyj.konfplan.dto.RaumBelegungUebersichtDto;
import kreyj.konfplan.dto.SolverConfigDto;
import kreyj.konfplan.persistence.*;
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
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
public class OptimierungServiceIntegrationTest {
    private static final Logger LOG = Logger.getLogger(OptimierungServiceIntegrationTest.class);

    @Inject
    OptimierungService optimierungService;

    @Inject
    PlanService planService;

    private Gebaeude schule;

    @BeforeEach
    @Transactional
    public void setup() {
        // Clean up database before each test
        Prioritaet.deleteAll();
        Planungsergebnis.deleteAll();
        Wahlvortrag.deleteAll();
        Pflichtvortrag.deleteAll();
        Teilnehmer.deleteAll();
        Referent.deleteAll();
        EventSlot.deleteAll();
        Raum.deleteAll();
        Veranstaltung.deleteAll();
        Gebaeude.deleteAll();

        // 1. Schule (Gebäude) und Räume
        schule = new Gebaeude("Test Schule",
                "Testort",
                "Teststrasse",
                "4711",
                Gebaeude.Gebaeudetyp.SCHULE);
        schule.persistAndFlush();

        Raum raum1 = new Raum("Raum 1", 1, schule);
        raum1.persist();
        Raum raum2 = new Raum("Raum 2", 2, schule);
        raum2.persist();
    }

    @Transactional
    public Veranstaltung simpleSetup(boolean satisfiable) {
        // 2. Veranstaltung und Zeitslots
        Veranstaltung veranstaltung = new Veranstaltung();
        veranstaltung.name = (satisfiable ? "E" : "Une") + "rfüllbarer Testlauf";
        veranstaltung.beginntAm = LocalDateTime.now();
        veranstaltung.gebaeude.add(schule);
        veranstaltung.persistAndFlush();

        EventSlot slot1 = new EventSlot("Slot 1", LocalDateTime.now().plusHours(1), LocalDateTime.now().plusHours(2), veranstaltung);
        slot1.persistAndFlush();

        // 3. Referent und Vorträge
        Referent referent = new Referent();
        referent.email = "referent@test.com";
        referent.firstName = "Max";
        referent.lastName = "Mustermann";
        referent.veranstaltungen.add(veranstaltung);
        referent.persistAndFlush();

        Wahlvortrag wahlvortrag1 = new Wahlvortrag();
        wahlvortrag1.titel = "Wahlvortrag 1";
        wahlvortrag1.referent = referent;
        wahlvortrag1.veranstaltung = veranstaltung;
        wahlvortrag1.persistAndFlush();

        // 4. Teilnehmer und Prioritäten
        Teilnehmer teilnehmer1 = new Teilnehmer();
        teilnehmer1.email = "tn1@test.com";
        teilnehmer1.firstName = "Peter";
        teilnehmer1.lastName = "Pan";
        teilnehmer1.gruppe = "A";
        teilnehmer1.veranstaltungen.add(veranstaltung);
        teilnehmer1.persistAndFlush();

        // Prioritäten für Teilnehmer 1
        new Prioritaet(teilnehmer1, wahlvortrag1, 1).persistAndFlush();

        return veranstaltung;
    }

    @Transactional
    public Veranstaltung complexSetup() {
        // 2. Veranstaltung und Zeitslots
        Veranstaltung veranstaltung = new Veranstaltung();
        veranstaltung.name = "Komplexer Testlauf";
        veranstaltung.beginntAm = LocalDateTime.now();
        veranstaltung.gebaeude.add(schule);
        veranstaltung.persistAndFlush();

        EventSlot slot1 = new EventSlot("Slot 1", LocalDateTime.now().plusHours(1), LocalDateTime.now().plusHours(2), veranstaltung);
        slot1.persistAndFlush();
        EventSlot slot2 = new EventSlot("Slot 2", LocalDateTime.now().plusHours(2), LocalDateTime.now().plusHours(3), veranstaltung);
        slot2.persistAndFlush();
        EventSlot slot3 = new EventSlot("Slot 3", LocalDateTime.now().plusHours(3), LocalDateTime.now().plusHours(4), veranstaltung);
        slot3.persistAndFlush();

        // 3. Referent und Vorträge
        Referent referent = new Referent();
        referent.email = "referent@test.com";
        referent.firstName = "Max";
        referent.lastName = "Mustermann";
        referent.veranstaltungen.add(veranstaltung);
        referent.persistAndFlush();

        Wahlvortrag wahlvortrag1 = new Wahlvortrag();
        wahlvortrag1.titel = "Wahlvortrag 1";
        wahlvortrag1.referent = referent;
        wahlvortrag1.veranstaltung = veranstaltung;
        wahlvortrag1.persistAndFlush();

        Wahlvortrag wahlvortrag2 = new Wahlvortrag();
        wahlvortrag2.titel = "Wahlvortrag 2";
        wahlvortrag2.referent = referent;
        wahlvortrag2.veranstaltung = veranstaltung;
        wahlvortrag2.persistAndFlush();

        Pflichtvortrag pflichtvortrag = new Pflichtvortrag();
        pflichtvortrag.titel = "Pflichtvortrag";
        pflichtvortrag.referent = referent;
        pflichtvortrag.veranstaltung = veranstaltung;
        pflichtvortrag.pflichtslot = slot3;
        pflichtvortrag.pflichtraum = schule.raeume.getFirst();
        pflichtvortrag.pflichtgruppe = "A";
        pflichtvortrag.persistAndFlush();

        // 4. Teilnehmer und Prioritäten
        Teilnehmer teilnehmer1 = new Teilnehmer();
        teilnehmer1.email = "tn1@test.com";
        teilnehmer1.firstName = "Peter";
        teilnehmer1.lastName = "Pan";
        teilnehmer1.gruppe = "A";
        teilnehmer1.veranstaltungen.add(veranstaltung);
        teilnehmer1.persistAndFlush();

        Teilnehmer teilnehmer2 = new Teilnehmer();
        teilnehmer2.email = "tn2@test.com";
        teilnehmer2.firstName = "Wendy";
        teilnehmer2.lastName = "Darling";
        teilnehmer2.gruppe = "A";
        teilnehmer2.veranstaltungen.add(veranstaltung);
        teilnehmer2.persistAndFlush();

        // Prioritäten für TN 1
        new Prioritaet(teilnehmer1, wahlvortrag1, 1).persistAndFlush();
        new Prioritaet(teilnehmer1, wahlvortrag2, 2).persistAndFlush();

        // Priorität für TN 2
        new Prioritaet(teilnehmer2, wahlvortrag2, 1).persistAndFlush();

        return veranstaltung;
    }

    @Test
    @Transactional
    public void testOptimierungslauf_withMinimalSetup() throws Exception {
        Veranstaltung veranstaltung = simpleSetup(true);

        // 1. Optimierung durchführen
        SolverConfigDto config = new SolverConfigDto("cp-sat", 10, 4, 1);
        optimierungService.starteOptimierung(veranstaltung.id, config);

        // 2. Ergebnis prüfen
        Planungsergebnis ergebnis = Planungsergebnis.find("veranstaltung", veranstaltung).firstResult();
        assertNotNull(ergebnis, "Planungsergebnis sollte nach der Optimierung vorhanden sein.");
        assertNotNull(ergebnis.jsonErgebnis, "Das JSON-Ergebnis im Planungsergebnis darf nicht null sein.");
        assertTrue(ergebnis.jsonErgebnis.contains("instanz_slot"), "Das JSON-Ergebnis sollte den Schlüssel 'instanz_slot' enthalten.");

        // 3. Belegungsplan abrufen und prüfen
        List<RaumBelegungUebersichtDto> belegungsplan = planService.getDetaillierterPlan(veranstaltung.id);
        LOG.info("# Belegungsplan:\n  " + belegungsplan.stream().map(Object::toString).collect(joining("\n  ")));

        assertNotNull(belegungsplan, "Der Belegungsplan darf nicht null sein.");
        assertFalse(belegungsplan.isEmpty(), "Der Belegungsplan darf nicht leer sein.");

        // Konkrete Zuweisungen prüfen
        // Teilnehmer 1 sollte Wahlvortrag 1 bekommen (Prio 1)
        boolean tn1InWahlvortrag1 = belegungsplan.stream()
                .anyMatch(b -> "Wahlvortrag 1".equals(b.getVortragTitel()) && b.getTeilnehmerNamen().contains("Peter Pan"));
        assertTrue(tn1InWahlvortrag1, "Teilnehmer 1 sollte dem Wahlvortrag 1 zugewiesen sein.");
    }

    @Test
    @Transactional
    public void testOptimierungslauf_withNoVisits() throws Exception {
        Veranstaltung veranstaltung = simpleSetup(false);
        Teilnehmer tn = Teilnehmer.<Teilnehmer>listAll().getFirst();
        // tn ist nicht verfügbar für Wahlvorträge
        tn.verfuegbareSlots.clear();
        tn.persistAndFlush();

        // 1. Optimierung durchführen
        SolverConfigDto config = new SolverConfigDto("cp-sat", 60, 4, 1);
        optimierungService.starteOptimierung(veranstaltung.id, config);

        // 2. Ergebnis prüfen
        Planungsergebnis ergebnis = Planungsergebnis.find("veranstaltung", veranstaltung).firstResult();
        assertNotNull(ergebnis, "Planungsergebnis sollte nach der Optimierung vorhanden sein.");
        assertNotNull(ergebnis.jsonErgebnis, "Das JSON-Ergebnis im Planungsergebnis darf nicht null sein.");
        assertTrue(ergebnis.jsonErgebnis.contains("instanz_slot"), "Das JSON-Ergebnis sollte den Schlüssel 'instanz_slot' enthalten.");

        // 3. Belegungsplan abrufen und prüfen
        List<RaumBelegungUebersichtDto> belegungsplan = planService.getDetaillierterPlan(veranstaltung.id);
        LOG.info("belegungsplan: " + belegungsplan);

        assertNotNull(belegungsplan, "Der Belegungsplan darf nicht null sein.");
        assertFalse(belegungsplan.isEmpty(), "Der Belegungsplan darf nicht leer sein.");

        // Konkrete Zuweisungen prüfen
        // Teilnehmer 1 sollte Wahlvortrag 1 bekommen (Prio 1)
        boolean tn1InWahlvortrag1 = belegungsplan.stream()
                .anyMatch(b -> "Wahlvortrag 1".equals(b.getVortragTitel()) && b.getTeilnehmerNamen().contains("Peter Pan"));
        assertTrue(tn1InWahlvortrag1, "Teilnehmer 1 sollte dem Wahlvortrag 1 zugewiesen sein.");

        // Teilnehmer 2 sollte Wahlvortrag 2 bekommen (Prio 1)
        boolean tn2InWahlvortrag2 = belegungsplan.stream()
                .anyMatch(b -> "Wahlvortrag 2".equals(b.getVortragTitel()) && b.getTeilnehmerNamen().contains("Wendy Darling"));
        assertTrue(tn2InWahlvortrag2, "Teilnehmer 2 sollte dem Wahlvortrag 2 zugewiesen sein.");

        // Beide Teilnehmer sollten im Pflichtvortrag sein
        long anzahltnImPflichtvortrag = belegungsplan.stream()
                .filter(b -> "Pflichtvortrag".equals(b.getVortragTitel()))
                .map(RaumBelegungUebersichtDto::getTeilnehmerNamen)
                .flatMap(List::stream)
                .distinct()
                .count();
        assertEquals(2, anzahltnImPflichtvortrag, "Beide Teilnehmer sollten dem Pflichtvortrag zugewiesen sein.");
    }

    @Test
    @Transactional
    public void testOptimierungslauf_withoutResult() throws Exception {
        Veranstaltung veranstaltung = simpleSetup(false);
        Teilnehmer tn = Teilnehmer.<Teilnehmer>listAll().getFirst();
        // tn ist nicht verfügbar für Wahlvorträge
        tn.verfuegbareSlots.clear();
        tn.persistAndFlush();

        // 1. Optimierung durchführen
        SolverConfigDto config = new SolverConfigDto("cp-sat", 60, 4, 1);
        optimierungService.starteOptimierung(veranstaltung.id, config);

        // 2. Ergebnis prüfen
        Planungsergebnis ergebnis = Planungsergebnis.find("veranstaltung", veranstaltung).firstResult();
        assertNotNull(ergebnis, "Planungsergebnis sollte nach der Optimierung vorhanden sein.");
        assertNotNull(ergebnis.jsonErgebnis, "Das JSON-Ergebnis im Planungsergebnis darf nicht null sein.");
        assertTrue(ergebnis.jsonErgebnis.contains("instanz_slot"), "Das JSON-Ergebnis sollte den Schlüssel 'instanz_slot' enthalten.");

        // 3. Belegungsplan abrufen und prüfen
        List<RaumBelegungUebersichtDto> belegungsplan = planService.getDetaillierterPlan(veranstaltung.id);
        LOG.info("belegungsplan: " + belegungsplan);

        assertNotNull(belegungsplan, "Der Belegungsplan darf nicht null sein.");
        assertFalse(belegungsplan.isEmpty(), "Der Belegungsplan darf nicht leer sein.");

        // Konkrete Zuweisungen prüfen
        // Teilnehmer 1 sollte Wahlvortrag 1 bekommen (Prio 1)
        boolean tn1InWahlvortrag1 = belegungsplan.stream()
                .anyMatch(b -> "Wahlvortrag 1".equals(b.getVortragTitel()) && b.getTeilnehmerNamen().contains("Peter Pan"));
        assertTrue(tn1InWahlvortrag1, "Teilnehmer 1 sollte dem Wahlvortrag 1 zugewiesen sein.");

        // Teilnehmer 2 sollte Wahlvortrag 2 bekommen (Prio 1)
        boolean tn2InWahlvortrag2 = belegungsplan.stream()
                .anyMatch(b -> "Wahlvortrag 2".equals(b.getVortragTitel()) && b.getTeilnehmerNamen().contains("Wendy Darling"));
        assertTrue(tn2InWahlvortrag2, "Teilnehmer 2 sollte dem Wahlvortrag 2 zugewiesen sein.");

        // Beide Teilnehmer sollten im Pflichtvortrag sein
        long anzahltnImPflichtvortrag = belegungsplan.stream()
                .filter(b -> "Pflichtvortrag".equals(b.getVortragTitel()))
                .map(RaumBelegungUebersichtDto::getTeilnehmerNamen)
                .flatMap(List::stream)
                .distinct()
                .count();
        assertEquals(2, anzahltnImPflichtvortrag, "Beide Teilnehmer sollten dem Pflichtvortrag zugewiesen sein.");
    }

    @Test
    @Transactional
    public void testOptimierungslauf_withComplexSetup() throws Exception {
        Veranstaltung veranstaltung = complexSetup();
        // 1. Optimierung durchführen
        SolverConfigDto config = new SolverConfigDto("cp-sat", 60, 4, 2);
        optimierungService.starteOptimierung(veranstaltung.id, config);

        // 2. Ergebnis prüfen
        Planungsergebnis ergebnis = Planungsergebnis.find("veranstaltung", veranstaltung).firstResult();
        assertNotNull(ergebnis, "Planungsergebnis sollte nach der Optimierung vorhanden sein.");
        assertNotNull(ergebnis.jsonErgebnis, "Das JSON-Ergebnis im Planungsergebnis darf nicht null sein.");
        assertTrue(ergebnis.jsonErgebnis.contains("instanz_slot"), "Das JSON-Ergebnis sollte den Schlüssel 'instanz_slot' enthalten.");

        // 3. Belegungsplan abrufen und prüfen
        List<RaumBelegungUebersichtDto> belegungsplan = planService.getDetaillierterPlan(veranstaltung.id);
        LOG.info("belegungsplan: " + belegungsplan);

        assertNotNull(belegungsplan, "Der Belegungsplan darf nicht null sein.");
        assertFalse(belegungsplan.isEmpty(), "Der Belegungsplan darf nicht leer sein.");

        // Konkrete Zuweisungen prüfen
        // Teilnehmer 1 sollte Wahlvortrag 1 bekommen (Prio 1)
        boolean tn1InWahlvortrag1 = belegungsplan.stream()
                .anyMatch(b -> "Wahlvortrag 1".equals(b.getVortragTitel()) && b.getTeilnehmerNamen().contains("Peter Pan"));
        assertTrue(tn1InWahlvortrag1, "Teilnehmer 1 sollte dem Wahlvortrag 1 zugewiesen sein.");

        // Teilnehmer 2 sollte Wahlvortrag 2 bekommen (Prio 1)
        boolean tn2InWahlvortrag2 = belegungsplan.stream()
                .anyMatch(b -> "Wahlvortrag 2".equals(b.getVortragTitel()) && b.getTeilnehmerNamen().contains("Wendy Darling"));
        assertTrue(tn2InWahlvortrag2, "Teilnehmer 2 sollte dem Wahlvortrag 2 zugewiesen sein.");

        // Beide Teilnehmer sollten im Pflichtvortrag sein
        long anzahltnImPflichtvortrag = belegungsplan.stream()
                .filter(b -> "Pflichtvortrag".equals(b.getVortragTitel()))
                .map(RaumBelegungUebersichtDto::getTeilnehmerNamen)
                .flatMap(List::stream)
                .distinct()
                .count();
        assertEquals(2, anzahltnImPflichtvortrag, "Beide Teilnehmer sollten dem Pflichtvortrag zugewiesen sein.");
    }

    @Test
    public void testOptimierung_withUnsatisfiableModel() {
        SolverConfigDto config = new SolverConfigDto("cp-sat", 5, 1, 1);

        assertThatExceptionOfType(MinizincException.class)
                .isThrownBy(() -> starteTestOptimierung(config, "unsatisfiable.mzn"));
    }

    @Test
    public void testOptimierung_withIntermediateResult() throws Exception {
        // Kurzer Timeout, um sicher eine Zwischenlösung zu erhalten
        SolverConfigDto config = new SolverConfigDto("cp-sat", 1, 1, 1);

        String resultJson = starteTestOptimierung(config, "intermediate.mzn");

        assertNotNull(resultJson, "Sollte ein Ergebnis (letzte Zwischenlösung) zurückgeben.");
        assertFalse(resultJson.isEmpty(), "Das Ergebnis-JSON sollte nicht leer sein.");
        assertTrue(OptimierungService.isValidJson(resultJson), "Das Ergebnis sollte valides JSON sein.");
        assertTrue(resultJson.contains("total_value"), "Das Ergebnis-JSON sollte 'total_value' enthalten.");
    }

    @Test
    public void testOptimierung_withNoSolutionInTime() throws Exception {
        // Sehr kurzer Timeout, damit garantiert keine Lösung gefunden wird
        SolverConfigDto config = new SolverConfigDto("cp-sat", 1, 1, 1);

        assertThatExceptionOfType(MinizincException.class)
                .isThrownBy(() -> starteTestOptimierung(config, "no-solution-in-time.mzn"));
    }

    // -------------------------------------------------------------------
    // Helper-Methoden für Test-Setups
    // -------------------------------------------------------------------

    public String starteTestOptimierung(SolverConfigDto config, String modelName) throws Exception {
        URL modelUrl = getClass().getClassLoader().getResource("minizinc/" + modelName);
        if (modelUrl == null) {
            throw new FileNotFoundException("MiniZinc model not found: " + modelName);
        }

        Path tempDzn = Files.createTempFile("planung_", ".dzn");
        Files.writeString(tempDzn, "%no data", StandardCharsets.UTF_8);

        try {
            return optimierungService.rufeMiniZincAuf(Paths.get(modelUrl.toURI()),
                    tempDzn, config.solver, config.timeout, config.numThreads);

        } finally {
            Files.deleteIfExists(tempDzn);
        }
    }
}