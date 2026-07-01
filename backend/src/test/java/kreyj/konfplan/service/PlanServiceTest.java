package kreyj.konfplan.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import kreyj.konfplan.adapter.in.web.dto.RaumBelegungUebersicht;
import kreyj.konfplan.adapter.in.web.dto.SolverConfig;
import kreyj.konfplan.domain.service.PlanService;
import kreyj.konfplan.persistence.Planungsergebnis;
import kreyj.konfplan.persistence.Veranstaltung;
import kreyj.konfplan.presentation.DatabaseCleaner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

@QuarkusTest
public class PlanServiceTest extends DatabaseCleaner {

    @Inject
    PlanService planService;

    private Veranstaltung veranstaltung;


    @BeforeEach
    @Transactional
    public void setup() {
        veranstaltung = new Veranstaltung();
        veranstaltung.setName("Test Event");
        veranstaltung.setBeginntAm(LocalDateTime.now());
        veranstaltung.persist();

        Planungsergebnis ergebnis = new Planungsergebnis();
        ergebnis.setVeranstaltung(veranstaltung);
        ergebnis.setSolverConfig(new SolverConfig(60, 1, 1, false));
        // Simulate a minimal valid JSON structure to avoid NullPointerExceptions during parsing
        ergebnis.setJsonErgebnis("""
            {
              "instanz_slot": [[]],
              "instanz_raum": [[]],
              "besucht": [[[]]],
              "teilnehmer_oids": [],
              "wahlvortrag_oids": [],
              "slot_oids": [],
              "raum_oids": []
            }
            """);
        ergebnis.persist();
    }


    @Test
    public void testGetDetaillierterPlanDoesNotThrowLobException() {
        List<RaumBelegungUebersicht> detaillierterPlan = planService.getDetaillierterPlan(veranstaltung);

        assertThat(detaillierterPlan).describedAs("The returned plan should not be null.")
            .isNotNull();
    }


    @Test
    public void testPlanErgebnisIsParseable() {
        Planungsergebnis planungsergebnis = Planungsergebnis.find("veranstaltung = ?1", veranstaltung).firstResult();
        assertThat(planungsergebnis).isNotNull();
        String jsonErgebnis = planungsergebnis.getJsonErgebnis();
        assertThat(jsonErgebnis).isNotNull();

        ObjectMapper objectMapper = new ObjectMapper();
        final Planungsergebnis.MinizincResult[] results = {null};

        // The primary assertion is implicit: the test fails if a HibernateException is thrown.
        assertDoesNotThrow(() -> {
            results[0] = objectMapper.readValue(jsonErgebnis,
                Planungsergebnis.MinizincResult.class);
        }, "Accessing the detailed plan should not throw any exception.");

        Planungsergebnis.MinizincResult result = results[0];

        assertThat(result).isNotNull();

        assertThat(result.teilnehmer_oids).isNotNull();
        assertThat(result.wahlvortrag_oids).isNotNull();
        assertThat(result.slot_oids).isNotNull();
        assertThat(result.raum_oids).isNotNull();
        assertThat(result.besucht).isNotNull();
        assertThat(result.instanz_slot).isNotNull();
        assertThat(result.instanz_raum).isNotNull();
    }


}
