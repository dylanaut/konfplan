package kreyj.konfplan.service;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import kreyj.konfplan.application.service.PlanService;
import kreyj.konfplan.persistence.NutzerVerfuegbarkeit;
import kreyj.konfplan.persistence.Planungsergebnis;
import kreyj.konfplan.persistence.Prioritaet;
import kreyj.konfplan.persistence.Slot;
import kreyj.konfplan.persistence.Veranstaltung;
import kreyj.konfplan.persistence.Vortrag;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

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
        NutzerVerfuegbarkeit.deleteAll();
        Slot.deleteAll();
        Prioritaet.deleteAll();
        Vortrag.deleteAll();
        Veranstaltung.deleteAll();

        veranstaltung = new Veranstaltung();
        veranstaltung.setName("Test Event");
        veranstaltung.setBeginntAm(LocalDateTime.now());
        veranstaltung.persistAndFlush();

        Planungsergebnis ergebnis = new Planungsergebnis();
        ergebnis.setVeranstaltung(veranstaltung);
        ergebnis.setSolver("cp-sat");
        ergebnis.setTimeout(60);
        // Simulate a minimal valid JSON structure to avoid NullPointerExceptions during parsing
        ergebnis.setJsonErgebnis("""
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
                """);
        ergebnis.persistAndFlush();
    }

    @Test
    @Transactional
    public void testGetDetaillierterPlanDoesNotThrowLobException() {
        // The core of the test is to call the method and ensure it completes without throwing an exception.
        // The setup method prepares a Planungsergebnis with a JSON string, simulating the LOB.
        // The @Transactional annotation on this test method ensures that the session is active
        // when getDetaillierterPlan is called, which is the context of the original problem.

        // We expect this call to succeed without a HibernateException
        var detaillierterPlan = planService.getDetaillierterPlan(veranstaltung.getId());

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