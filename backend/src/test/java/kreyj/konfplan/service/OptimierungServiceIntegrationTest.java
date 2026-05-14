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

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
public class OptimierungServiceIntegrationTest {
    private static final Logger LOG = Logger.getLogger(OptimierungServiceIntegrationTest.class);

    @Inject
    OptimierungService optimierungService;

    @Inject
    PlanService planService;

    private Veranstaltung veranstaltung;
    private Teilnehmer teilnehmer1, teilnehmer2;
    private Wahlvortrag wahlvortrag1, wahlvortrag2;

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
        Gebaeude schule = new Gebaeude("Test Schule",
                "Testort",
                "Teststrasse",
                "4711",
                Gebaeude.Gebaeudetyp.SCHULE);
        schule.persist();

        Raum raum1 = new Raum("Raum 1", 3, schule);
        raum1.persist();
        Raum raum2 = new Raum("Raum 2", 4, schule);
        raum2.persist();
        Raum raum3 = new Raum("Raum 3", 5, schule);
        raum3.persist();

        // 2. Veranstaltung und Zeitslots
        veranstaltung = new Veranstaltung();
        veranstaltung.name = "Komplexer Testlauf";
        veranstaltung.beginntAm = LocalDateTime.now();
        veranstaltung.gebaeude.add(schule);
        veranstaltung.persistAndFlush();

        EventSlot slot1 = new EventSlot("Slot 1", LocalDateTime.now().plusHours(1), LocalDateTime.now().plusHours(2), veranstaltung);
        slot1.persist();
        EventSlot slot2 = new EventSlot("Slot 2", LocalDateTime.now().plusHours(2), LocalDateTime.now().plusHours(3), veranstaltung);
        slot2.persist();
        EventSlot slot3 = new EventSlot("Slot 3", LocalDateTime.now().plusHours(3), LocalDateTime.now().plusHours(4), veranstaltung);
        slot3.persist();

        // 3. Referent und Vorträge
        Referent referent = new Referent();
        referent.email = "referent@test.com";
        referent.firstName = "Max";
        referent.lastName = "Mustermann";
        referent.veranstaltungen.add(veranstaltung);
        referent.persist();

        wahlvortrag1 = new Wahlvortrag();
        wahlvortrag1.titel = "Wahlvortrag 1";
        wahlvortrag1.referent = referent;
        wahlvortrag1.veranstaltung = veranstaltung;
        wahlvortrag1.persistAndFlush();

        wahlvortrag2 = new Wahlvortrag();
        wahlvortrag2.titel = "Wahlvortrag 2";
        wahlvortrag2.referent = referent;
        wahlvortrag2.veranstaltung = veranstaltung;
        wahlvortrag2.persistAndFlush();

        Pflichtvortrag pflichtvortrag = new Pflichtvortrag();
        pflichtvortrag.titel = "Pflichtvortrag";
        pflichtvortrag.referent = referent;
        pflichtvortrag.veranstaltung = veranstaltung;
        pflichtvortrag.pflichtslot = slot3;
        pflichtvortrag.pflichtraum = raum3;
        pflichtvortrag.pflichtgruppe = "A";
        pflichtvortrag.persist();

        // 4. Schüler (Teilnehmer) und Prioritäten
        teilnehmer1 = new Teilnehmer();
        teilnehmer1.email = "schueler1@test.com";
        teilnehmer1.firstName = "Peter";
        teilnehmer1.lastName = "Pan";
        teilnehmer1.gruppe = "A";
        teilnehmer1.veranstaltungen.add(veranstaltung);
        teilnehmer1.persistAndFlush();

        teilnehmer2 = new Teilnehmer();
        teilnehmer2.email = "schueler2@test.com";
        teilnehmer2.firstName = "Wendy";
        teilnehmer2.lastName = "Darling";
        teilnehmer2.gruppe = "A";
        teilnehmer2.veranstaltungen.add(veranstaltung);
        teilnehmer2.persistAndFlush();

        // Prioritäten für Schüler 1
        new Prioritaet(teilnehmer1, wahlvortrag1, 1).persist();
        new Prioritaet(teilnehmer1, wahlvortrag2, 2).persist();

        // Priorität für Schüler 2
        new Prioritaet(teilnehmer2, wahlvortrag2, 1).persist();
    }

    @Test
    @Transactional
    public void testOptimierungslauf_withComplexSetup() throws Exception {
        // 1. Optimierung durchführen
        SolverConfigDto config = new SolverConfigDto(OptimierungService.SOLVER_TYP.CP_SAT, 60, 4, 2);
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
        // Schüler 1 sollte Wahlvortrag 1 bekommen (Prio 1)
        boolean schueler1InWahlvortrag1 = belegungsplan.stream()
                .anyMatch(b -> "Wahlvortrag 1".equals(b.getVortragTitel()) && b.getTeilnehmerNamen().contains("Peter Pan"));
        assertTrue(schueler1InWahlvortrag1, "Schüler 1 sollte dem Wahlvortrag 1 zugewiesen sein.");

        // Schüler 2 sollte Wahlvortrag 2 bekommen (Prio 1)
        boolean schueler2InWahlvortrag2 = belegungsplan.stream()
                .anyMatch(b -> "Wahlvortrag 2".equals(b.getVortragTitel()) && b.getTeilnehmerNamen().contains("Wendy Darling"));
        assertTrue(schueler2InWahlvortrag2, "Schüler 2 sollte dem Wahlvortrag 2 zugewiesen sein.");

        // Beide Schüler sollten im Pflichtvortrag sein
        long anzahlSchuelerImPflichtvortrag = belegungsplan.stream()
                .filter(b -> "Pflichtvortrag".equals(b.getVortragTitel()))
                .map(RaumBelegungUebersichtDto::getTeilnehmerNamen)
                .flatMap(List::stream)
                .distinct()
                .count();
        assertEquals(2, anzahlSchuelerImPflichtvortrag, "Beide Schüler sollten dem Pflichtvortrag zugewiesen sein.");
    }
}