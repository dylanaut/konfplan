package kreyj.konfplan.domain.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import kreyj.konfplan.adapter.in.web.dto.RaumBelegungUebersicht;
import kreyj.konfplan.adapter.in.web.dto.SolverConfig;
import kreyj.konfplan.adapter.in.web.dto.ZuweisungDto;
import kreyj.konfplan.persistence.Gebaeude;
import kreyj.konfplan.persistence.Gebaeudetyp;
import kreyj.konfplan.persistence.Pflichtvortrag;
import kreyj.konfplan.persistence.Planungsergebnis;
import kreyj.konfplan.persistence.Raum;
import kreyj.konfplan.persistence.Referent;
import kreyj.konfplan.persistence.Slot;
import kreyj.konfplan.persistence.Teilnehmer;
import kreyj.konfplan.persistence.Veranstaltung;
import kreyj.konfplan.adapter.in.web.DatabaseCleaner;
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

    @Inject
    ObjectMapper objectMapper;

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


    @Test
    @Transactional
    public void testGetMinizincResultCacheInvalidatesAfterPlanRegeneration() {
        Planungsergebnis.MinizincResult first = planService.getMinizincResult(veranstaltung);
        assertThat(first.teilnehmer_oids).isEmpty();

        Planungsergebnis ergebnis = Planungsergebnis.find("veranstaltung = ?1", veranstaltung).firstResult();
        ergebnis.setJsonErgebnis("""
            {
              "instanz_slot": [[]],
              "instanz_raum": [[]],
              "besucht": [[[]]],
              "teilnehmer_oids": [42],
              "wahlvortrag_oids": [],
              "slot_oids": [],
              "raum_oids": []
            }
            """);
        ergebnis.persistAndFlush();

        Planungsergebnis.MinizincResult second = planService.getMinizincResult(veranstaltung);
        assertThat(second.teilnehmer_oids).containsExactly(42L);
    }


    @Test
    @Transactional
    public void testGetPlanFuerTeilnehmer_includesPflichtvortrag_evenWhenTeilnehmerNotInMinizincResult() {
        // Der geteilte Fixture-Planungsergebnis (siehe setup()) hat teilnehmer_oids: [] - der
        // Teilnehmer taucht also nirgends im MiniZinc-Ergebnis auf (z.B. weil er gar keine
        // Wahlvortrag-Priorität abgegeben hat). Der Pflichtvortrag ist rein gruppenbasiert und
        // muss trotzdem erscheinen.
        Gebaeude gebaeude = new Gebaeude();
        gebaeude.setName("Hauptgebäude");
        gebaeude.setTyp(Gebaeudetyp.SCHULE);
        gebaeude.setPostleitzahl("12345");
        gebaeude.setOrt("Testort");
        gebaeude.setStrasse("Teststraße");
        gebaeude.persist();

        Raum raum = new Raum();
        raum.setName("Raum 1");
        raum.setKapazitaet(30);
        raum.persist();
        gebaeude.addRaum(raum);

        Slot slot = new Slot("Slot 1", LocalDateTime.of(2024, 1, 1, 9, 0), LocalDateTime.of(2024, 1, 1, 10, 0), veranstaltung);
        slot.persist();
        veranstaltung.addSlot(slot);

        Referent referent = new Referent();
        referent.assignLoginName("referent.pflicht");
        referent.setEmail("referent.pflicht@example.com");
        referent.persist();

        Pflichtvortrag pflichtvortrag = Pflichtvortrag.create("Pflichtvortrag Gruppe A", "Inhalt", referent, "Gruppe A", raum, slot, veranstaltung);
        pflichtvortrag.persist();

        Teilnehmer teilnehmer = new Teilnehmer();
        teilnehmer.assignLoginName("teilnehmer.pflicht");
        teilnehmer.setEmail("teilnehmer.pflicht@example.com");
        teilnehmer.addGruppe("Gruppe A");
        teilnehmer.persist();

        List<ZuweisungDto> plan = planService.getPlanFuerTeilnehmer(teilnehmer, veranstaltung);

        assertThat(plan).hasSize(1);
        assertThat(plan.get(0).vortragTitel).isEqualTo("Pflichtvortrag Gruppe A");
    }


}
