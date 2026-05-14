package kreyj.konfplan.service;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import kreyj.konfplan.persistence.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;

@QuarkusTest
public class PlanServiceTest {

    @Inject
    PlanService planService;

    private Veranstaltung veranstaltung;

    @BeforeEach
    @Transactional
    public void setup() {
        // Clear existing data to ensure a clean slate for each test
        Planungsergebnis.deleteAll();
        EventSlot.deleteAll();
        Veranstaltung.deleteAll();

        veranstaltung = new Veranstaltung();
        veranstaltung.name = "Test Event";
        veranstaltung.beginntAm = LocalDateTime.now();
        veranstaltung.persist();

        Planungsergebnis ergebnis = new Planungsergebnis();
        ergebnis.veranstaltung = veranstaltung;
        ergebnis.solver = OptimierungService.SOLVER_TYP.CP_SAT;
        ergebnis.timeout = 60;
        // Simulate a minimal valid JSON structure to avoid NullPointerExceptions during parsing
        ergebnis.jsonErgebnis = """
        {
          "input_data": {
            "teilnehmer_oids": [],
            "wahlvortrag_oids": [],
            "slot_oids": [],
            "raum_oids": []
          },
          "instanz_slot": [],
          "instanz_raum": [],
          "besucht": []
        }
        """;
        ergebnis.persist();
    }

    @Test
    @Transactional
    public void testGetDetaillierterPlanDoesNotThrowLobException() {
        // The core of the test is to call the method and ensure it completes without throwing an exception.
        // The setup method prepares a Planungsergebnis with a JSON string, simulating the LOB.
        // The @Transactional annotation on this test method ensures that the session is active
        // when getDetaillierterPlan is called, which is the context of the original problem.
        
        // We expect this call to succeed without a HibernateException
        var detaillierterPlan = planService.getDetaillierterPlan(veranstaltung.id);

        // A simple assertion to verify that the method ran and returned a (potentially empty) list.
        assertThat(detaillierterPlan).describedAs("The returned plan should not be null.")
                .isNotNull();
        
        // The primary assertion is implicit: the test fails if a HibernateException is thrown.
        assertDoesNotThrow(() -> {
            // You could add more complex processing of the result here if needed,
            // but for the LOB issue, simply accessing it is the key.
        }, "Accessing the detailed plan should not throw any exception.");
    }
}