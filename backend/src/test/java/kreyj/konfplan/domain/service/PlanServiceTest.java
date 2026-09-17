package kreyj.konfplan.domain.service;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import kreyj.konfplan.adapter.in.web.dto.FreierSlotDto;
import kreyj.konfplan.adapter.in.web.dto.FreierSlotGrund;
import kreyj.konfplan.adapter.in.web.dto.RaumBelegungUebersicht;
import kreyj.konfplan.adapter.in.web.dto.SolverConfig;
import kreyj.konfplan.adapter.in.web.dto.TeilnehmerVortragZuweisungDto;
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
import kreyj.konfplan.persistence.Wahlvortrag;
import kreyj.konfplan.adapter.in.web.DatabaseCleaner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

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
        ergebnis.setPubliziert(true);
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


    /**
     * Regressionstest: {@code ergebnis.delete()} (Panache-Instanzmethode) meldete für dieses
     * Entity beobachtbar Erfolg, ohne dass tatsächlich eine DELETE-Anweisung an die DB ging - der
     * Datensatz blieb bestehen (siehe Kommentar an {@link PlanService#loescheErgebnis}). Über
     * getrennte Transaktionen (wie bei echten, aufeinanderfolgenden HTTP-Requests) statt
     * innerhalb derselben Transaktion, damit ein reiner Erststufen-Cache-Effekt das eigentliche
     * DB-Verhalten nicht verdeckt.
     */
    @Test
    public void testLoescheErgebnis_persistiertTatsaechlich() {
        Long veranstaltungId = veranstaltung.getId();
        Long id = QuarkusTransaction.requiringNew().call(() -> {
            Planungsergebnis pe = new Planungsergebnis();
            pe.setVeranstaltung(Veranstaltung.findById(veranstaltungId));
            pe.setPubliziert(false);
            pe.setJsonErgebnis("{}");
            pe.persist();
            return pe.getId();
        });

        QuarkusTransaction.requiringNew().run(() -> {
            Veranstaltung v = Veranstaltung.findById(veranstaltungId);
            planService.loescheErgebnis(v, id);
        });

        Planungsergebnis nachDemLoeschen = QuarkusTransaction.requiringNew()
            .call(() -> Planungsergebnis.findById(id));

        assertThat(nachDemLoeschen).describedAs("Planungsergebnis sollte nach loescheErgebnis nicht mehr existieren").isNull();
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
            results[0] = Planungsergebnis.MinizincResult.fromJson(jsonErgebnis);
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


    @Test
    @Transactional
    public void testGetPlanFuerTeilnehmerDetailliert_liefertIdsUndTypJeZuweisung() {
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
        veranstaltung.addGebaeude(gebaeude);

        Slot slotPflicht = new Slot("Slot Pflicht", LocalDateTime.of(2024, 1, 1, 9, 0), LocalDateTime.of(2024, 1, 1, 10, 0), veranstaltung);
        slotPflicht.persist();
        veranstaltung.addSlot(slotPflicht);

        Slot slotWahl = new Slot("Slot Wahl", LocalDateTime.of(2024, 1, 1, 10, 0), LocalDateTime.of(2024, 1, 1, 11, 0), veranstaltung);
        slotWahl.persist();
        veranstaltung.addSlot(slotWahl);

        Referent referent = new Referent();
        referent.assignLoginName("referent.detail");
        referent.setEmail("referent.detail@example.com");
        referent.persist();

        Pflichtvortrag.create("Pflichtvortrag Gruppe A", "Inhalt", referent, "Gruppe A", raum, slotPflicht, veranstaltung);
        Wahlvortrag wahlvortrag = Wahlvortrag.create("Wahlvortrag X", "Inhalt", referent, false, 1, veranstaltung);

        Teilnehmer teilnehmer = new Teilnehmer();
        teilnehmer.assignLoginName("teilnehmer.detail");
        teilnehmer.setEmail("teilnehmer.detail@example.com");
        teilnehmer.addGruppe("Gruppe A");
        teilnehmer.persist();

        Planungsergebnis ergebnis = Planungsergebnis.find("veranstaltung = ?1", veranstaltung).firstResult();
        Planungsergebnis.MinizincResult result = new Planungsergebnis.MinizincResult();
        result.teilnehmer_oids = new long[]{teilnehmer.getId()};
        result.wahlvortrag_oids = new long[]{wahlvortrag.getId()};
        result.slot_oids = new long[]{slotWahl.getId()};
        result.raum_oids = new long[]{raum.getId()};
        result.instanz_slot = new int[][]{{1}};
        result.instanz_raum = new int[][]{{1}};
        result.besucht = new boolean[][][]{{{true}}};
        ergebnis.setJsonErgebnis(result.toJson());
        ergebnis.persistAndFlush();

        List<TeilnehmerVortragZuweisungDto> plan = planService.getPlanFuerTeilnehmerDetailliert(teilnehmer, veranstaltung);

        assertThat(plan).hasSize(2);

        TeilnehmerVortragZuweisungDto pflicht = plan.get(0);
        assertThat(pflicht.vortragTyp).isEqualTo("PFLICHT");
        assertThat(pflicht.vortragTitel).isEqualTo("Pflichtvortrag Gruppe A");
        assertThat(pflicht.slotId).isEqualTo(slotPflicht.getId());
        assertThat(pflicht.raumId).isEqualTo(raum.getId());

        TeilnehmerVortragZuweisungDto wahl = plan.get(1);
        assertThat(wahl.vortragTyp).isEqualTo("WAHL");
        assertThat(wahl.vortragId).isEqualTo(wahlvortrag.getId());
        assertThat(wahl.vortragTitel).isEqualTo("Wahlvortrag X");
        assertThat(wahl.slotId).isEqualTo(slotWahl.getId());
        assertThat(wahl.raumId).isEqualTo(raum.getId());
    }


    @Test
    @Transactional
    public void testZurueckziehenErgebnis_entziehtVeroeffentlichungsstatus() {
        Planungsergebnis ergebnis = Planungsergebnis.find("veranstaltung = ?1", veranstaltung).firstResult();
        assertThat(ergebnis.isPubliziert()).isTrue();

        planService.zurueckziehenErgebnis(veranstaltung, ergebnis.getId());

        assertThat(ergebnis.isPubliziert()).isFalse();
    }


    @Test
    @Transactional
    public void testZurueckziehenErgebnis_beiNichtVeroeffentlichtem_wirftBusinessException() {
        Planungsergebnis ergebnis = Planungsergebnis.find("veranstaltung = ?1", veranstaltung).firstResult();
        ergebnis.setPubliziert(false);

        assertThat(org.junit.jupiter.api.Assertions.assertThrows(
            kreyj.konfplan.domain.exception.BusinessException.class,
            () -> planService.zurueckziehenErgebnis(veranstaltung, ergebnis.getId())
        )).hasMessage("Dieses Planungsergebnis ist nicht veröffentlicht.");
    }


    @Test
    @Transactional
    public void testGetRaumbelegungsplanMitErgebnisId_liefertUnveroeffentlichtesErgebnis() {
        // Das veröffentlichte Fixture-Ergebnis hat teilnehmer_oids: [] - ein zweites,
        // unveröffentlichtes Ergebnis mit abweichenden Daten muss über die ergebnisId-Überladung
        // erreichbar sein, unabhängig vom Veröffentlichungsstatus.
        Planungsergebnis unveroeffentlicht = new Planungsergebnis();
        unveroeffentlicht.setVeranstaltung(veranstaltung);
        unveroeffentlicht.setPubliziert(false);
        unveroeffentlicht.setSolverConfig(new SolverConfig(60, 1, 1, false));
        unveroeffentlicht.setJsonErgebnis("""
            {
              "instanz_slot": [[]],
              "instanz_raum": [[]],
              "besucht": [[[]]],
              "teilnehmer_oids": [99],
              "wahlvortrag_oids": [],
              "slot_oids": [],
              "raum_oids": []
            }
            """);
        unveroeffentlicht.persist();

        Planungsergebnis.MinizincResult viaErgebnisId =
            planService.getMinizincResult(veranstaltung, unveroeffentlicht.getId());
        assertThat(viaErgebnisId.teilnehmer_oids).containsExactly(99L);

        Planungsergebnis.MinizincResult veroeffentlicht = planService.getMinizincResult(veranstaltung);
        assertThat(veroeffentlicht.teilnehmer_oids).isEmpty();
    }


    @Test
    @Transactional
    public void testGetFreieSlotsTeilnehmer_unterscheidetNichtVerfuegbarVonNichtVerplant() {
        // Beide Teilnehmer sind im (leeren) Fixture-Planungsergebnis keinem Wahl- oder
        // Pflichtvortrag zugeteilt - der Slot ist fuer beide "frei". Der Grund muss sich dennoch
        // unterscheiden: wer explizit als nicht verfuegbar markiert wurde, darf nicht wie jemand
        // erscheinen, der lediglich vom Solver nicht verplant werden konnte.
        //
        // veranstaltung frisch nachladen: das Feld stammt aus der @BeforeEach-Transaktion und ist
        // in dieser Test-Transaktion detached - Nutzer.veranstaltungen cascade-persisted (PERSIST)
        // beim ersten addVeranstaltung(...) sonst ein detached Entity.
        veranstaltung = Veranstaltung.findById(veranstaltung.getId());

        Slot slot = new Slot("Slot 1", LocalDateTime.of(2024, 1, 1, 9, 0), LocalDateTime.of(2024, 1, 1, 10, 0), veranstaltung);
        slot.persist();
        veranstaltung.addSlot(slot);

        Teilnehmer verfuegbar = new Teilnehmer();
        verfuegbar.assignLoginName("teilnehmer.verfuegbar");
        verfuegbar.setEmail("teilnehmer.verfuegbar@example.com");
        verfuegbar.persist();
        verfuegbar.addVeranstaltung(veranstaltung);

        Teilnehmer nichtVerfuegbar = new Teilnehmer();
        nichtVerfuegbar.assignLoginName("teilnehmer.nichtverfuegbar");
        nichtVerfuegbar.setEmail("teilnehmer.nichtverfuegbar@example.com");
        nichtVerfuegbar.persist();
        nichtVerfuegbar.addVeranstaltung(veranstaltung);
        nichtVerfuegbar.updateVerfuegbarkeit(slot, veranstaltung, false);

        Map<Long, List<FreierSlotDto>> freieSlots = planService.getFreieSlotsTeilnehmer(veranstaltung);

        assertThat(freieSlots.get(verfuegbar.getId()))
            .extracting(f -> f.grund)
            .containsExactly(FreierSlotGrund.NICHT_VERPLANT);
        assertThat(freieSlots.get(nichtVerfuegbar.getId()))
            .extracting(f -> f.grund)
            .containsExactly(FreierSlotGrund.NICHT_VERFUEGBAR);
    }


    @Test
    @Transactional
    public void testGetMinizincResult_ohneErgebnis_wirftBusinessExceptionStattNullPointerException() {
        // Regression: fehlte bislang ein veröffentlichtes Planungsergebnis (z.B. Report-Vorschau
        // ohne wirksame ergebnisId), führte der Aufruf zu einer unbehandelten NullPointerException
        // (500) statt einer sprechenden Fehlermeldung (siehe getMinizincResult(Veranstaltung, Long)).
        Veranstaltung ohneErgebnis = new Veranstaltung();
        ohneErgebnis.setName("Event ohne Planungsergebnis");
        ohneErgebnis.setBeginntAm(LocalDateTime.now());
        ohneErgebnis.persist();

        assertThat(org.junit.jupiter.api.Assertions.assertThrows(
            kreyj.konfplan.domain.exception.BusinessException.class,
            () -> planService.getMinizincResult(ohneErgebnis)
        )).hasMessage("Für diese Veranstaltung liegt noch kein veröffentlichtes Planungsergebnis vor.");
    }


}
