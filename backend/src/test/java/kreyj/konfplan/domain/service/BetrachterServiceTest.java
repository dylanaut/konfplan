package kreyj.konfplan.domain.service;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import kreyj.konfplan.adapter.in.web.DatabaseCleaner;
import kreyj.konfplan.adapter.in.web.dto.BetrachterTeilnehmerVortragDto;
import kreyj.konfplan.adapter.in.web.dto.NutzerVerfuegbarkeitDto;
import kreyj.konfplan.adapter.in.web.dto.SolverConfig;
import kreyj.konfplan.adapter.in.web.dto.ZuweisungDto;
import kreyj.konfplan.persistence.Anwesenheit;
import kreyj.konfplan.persistence.Betrachter;
import kreyj.konfplan.persistence.Gebaeude;
import kreyj.konfplan.persistence.Gebaeudetyp;
import kreyj.konfplan.persistence.Gruppenkategorie;
import kreyj.konfplan.persistence.GruppenkategorieWert;
import kreyj.konfplan.persistence.Pflichtvortrag;
import kreyj.konfplan.persistence.Planungsergebnis;
import kreyj.konfplan.persistence.Prioritaet;
import kreyj.konfplan.persistence.Raum;
import kreyj.konfplan.persistence.Referent;
import kreyj.konfplan.persistence.Slot;
import kreyj.konfplan.persistence.Teilnehmer;
import kreyj.konfplan.persistence.Veranstaltung;
import kreyj.konfplan.persistence.Wahlvortrag;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
class BetrachterServiceTest extends DatabaseCleaner {

    @Inject
    BetrachterService betrachterService;

    private Veranstaltung veranstaltung;
    private Gruppenkategorie klasse;
    private GruppenkategorieWert gruppeA;
    private GruppenkategorieWert gruppeB;

    @BeforeEach
    @Transactional
    void setup() {
        veranstaltung = new Veranstaltung();
        veranstaltung.setName("Betrachter-Test");
        veranstaltung.setBeginntAm(LocalDateTime.now());
        veranstaltung.persist();

        klasse = new Gruppenkategorie(veranstaltung, "Klasse", false, false);
        klasse.persist();
        gruppeA = new GruppenkategorieWert(klasse, "Gruppe A");
        gruppeA.persist();
        gruppeB = new GruppenkategorieWert(klasse, "Gruppe B");
        gruppeB.persist();
    }


    private Teilnehmer neuerTeilnehmer(String email, GruppenkategorieWert... werte) {
        Teilnehmer tn = new Teilnehmer();
        tn.assignLoginName(email);
        tn.setEmail(email);
        tn.persist();
        tn.addVeranstaltung(Veranstaltung.findById(veranstaltung.getId()));
        for (GruppenkategorieWert wert : werte) {
            tn.addTeilnehmerGruppenwert(wert);
        }
        return tn;
    }


    private Betrachter neuerBetrachter(String email, GruppenkategorieWert... werte) {
        Betrachter b = new Betrachter();
        b.assignLoginName(email);
        b.setEmail(email);
        b.persist();
        b.addVeranstaltung(Veranstaltung.findById(veranstaltung.getId()));
        for (GruppenkategorieWert wert : werte) {
            b.addBetrachterGruppenwert(wert);
        }
        return b;
    }


    @Test
    @Transactional
    void getSichtbareTeilnehmer_ohneGruppenwerte_istLeer() {
        Betrachter betrachter = neuerBetrachter("ohne-gruppen@test.de");
        neuerTeilnehmer("tn-a@test.de", gruppeA);

        assertThat(betrachterService.getSichtbareTeilnehmer(betrachter, veranstaltung)).isEmpty();
    }


    @Test
    @Transactional
    void getSichtbareTeilnehmer_liefertNurTeilnehmerDerZugewiesenenGruppenwerte() {
        Betrachter betrachter = neuerBetrachter("lehrer.a@test.de", gruppeA);
        Teilnehmer tnA = neuerTeilnehmer("tn-a@test.de", gruppeA);
        neuerTeilnehmer("tn-b@test.de", gruppeB);

        assertThat(betrachterService.getSichtbareTeilnehmer(betrachter, veranstaltung)).containsExactly(tnA);
    }


    @Test
    @Transactional
    void getSichtbareTeilnehmer_beruecksichtigtMehrereZugewieseneWerte() {
        Betrachter betrachter = neuerBetrachter("lehrer.ab@test.de", gruppeA, gruppeB);
        Teilnehmer tnA = neuerTeilnehmer("tn-a2@test.de", gruppeA);
        Teilnehmer tnB = neuerTeilnehmer("tn-b2@test.de", gruppeB);

        assertThat(betrachterService.getSichtbareTeilnehmer(betrachter, veranstaltung)).containsExactlyInAnyOrder(tnA, tnB);
    }


    @Test
    @Transactional
    void getSichtbareTeilnehmer_verlangtMitgliedschaftInDerAbgefragtenVeranstaltung() {
        // gruppeA gehört zur Kategorie "Klasse" von 'veranstaltung' - ein Teilnehmer, der diesen
        // Wert zwar haelt, aber gar nicht Mitglied von 'veranstaltung' ist (z.B. weil er nur bei
        // einer anderen Veranstaltung angemeldet ist), darf trotzdem nicht auftauchen.
        Veranstaltung andereVeranstaltung = new Veranstaltung();
        andereVeranstaltung.setName("Andere Veranstaltung");
        andereVeranstaltung.setBeginntAm(LocalDateTime.now());
        andereVeranstaltung.persist();

        Betrachter betrachter = neuerBetrachter("lehrer.iso@test.de", gruppeA);
        Teilnehmer tnAndereVeranstaltung = new Teilnehmer();
        tnAndereVeranstaltung.assignLoginName("tn-andere@test.de");
        tnAndereVeranstaltung.setEmail("tn-andere@test.de");
        tnAndereVeranstaltung.persist();
        tnAndereVeranstaltung.addVeranstaltung(Veranstaltung.findById(andereVeranstaltung.getId()));
        tnAndereVeranstaltung.addTeilnehmerGruppenwert(gruppeA);

        assertThat(betrachterService.getSichtbareTeilnehmer(betrachter, veranstaltung)).isEmpty();
    }


    @Test
    @Transactional
    void getVerfuegbarkeiten_nurFuerSichtbareTeilnehmer() {
        Betrachter betrachter = neuerBetrachter("lehrer.verf@test.de", gruppeA);
        Teilnehmer tnA = neuerTeilnehmer("tn-verf-a@test.de", gruppeA);
        neuerTeilnehmer("tn-verf-b@test.de", gruppeB);

        List<NutzerVerfuegbarkeitDto> verfuegbarkeiten = betrachterService.getVerfuegbarkeiten(betrachter, veranstaltung);

        assertThat(verfuegbarkeiten).extracting(dto -> dto.nutzerId).containsExactly(tnA.getId());
    }


    @Test
    @Transactional
    void getZuweisungen_zeigtNurVeroeffentlichtenPlan() {
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
        referent.assignLoginName("referent.betrachter@test.de");
        referent.setEmail("referent.betrachter@test.de");
        referent.persist();

        // "Gruppe A" dient hier sowohl als Betrachter-Scoping-Wert (Gruppenkategorie) als auch als
        // Pflichtvortrag-Gruppe (altes flaches Modell) - Teilnehmer.istInGruppe prüft beide.
        Pflichtvortrag.create("Pflichtvortrag Gruppe A", "Inhalt", referent, "Gruppe A", raum, slot, veranstaltung);

        Betrachter betrachter = neuerBetrachter("lehrer.plan@test.de", gruppeA);
        Teilnehmer tnA = neuerTeilnehmer("tn-plan-a@test.de", gruppeA);

        Planungsergebnis unveroeffentlicht = new Planungsergebnis();
        unveroeffentlicht.setVeranstaltung(veranstaltung);
        unveroeffentlicht.setPubliziert(false);
        unveroeffentlicht.setSolverConfig(new SolverConfig(60, 1, 1, false));
        unveroeffentlicht.setJsonErgebnis(MINIMAL_MINIZINC_JSON);
        unveroeffentlicht.persist();

        Map<Long, List<ZuweisungDto>> ohnePubliziertesErgebnis = betrachterService.getZuweisungen(betrachter, veranstaltung);
        assertThat(ohnePubliziertesErgebnis).containsOnlyKeys(tnA.getId());
        assertThat(ohnePubliziertesErgebnis.get(tnA.getId())).isEmpty();

        unveroeffentlicht.setPubliziert(true);

        Map<Long, List<ZuweisungDto>> mitPubliziertemErgebnis = betrachterService.getZuweisungen(betrachter, veranstaltung);
        assertThat(mitPubliziertemErgebnis.get(tnA.getId()))
            .extracting(dto -> dto.vortragTitel)
            .containsExactly("Pflichtvortrag Gruppe A");
    }


    @Test
    @Transactional
    void getTeilnehmerVortraege_kennzeichnetPflichtPrioUndFuellUndBesuchtKorrekt() {
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
        Slot slotWahlPrio = new Slot("Slot Wahl Prio", LocalDateTime.of(2024, 1, 1, 10, 0), LocalDateTime.of(2024, 1, 1, 11, 0), veranstaltung);
        slotWahlPrio.persist();
        veranstaltung.addSlot(slotWahlPrio);
        Slot slotWahlFuell = new Slot("Slot Wahl Füll", LocalDateTime.of(2024, 1, 1, 11, 0), LocalDateTime.of(2024, 1, 1, 12, 0), veranstaltung);
        slotWahlFuell.persist();
        veranstaltung.addSlot(slotWahlFuell);

        Referent referent = new Referent();
        referent.assignLoginName("referent.vortraege@test.de");
        referent.setEmail("referent.vortraege@test.de");
        referent.persist();

        Pflichtvortrag.create("Pflicht X", "Inhalt", referent, "Gruppe A", raum, slotPflicht, veranstaltung);
        Wahlvortrag wahlPrio = Wahlvortrag.create("Wahl Prio", "Inhalt", referent, false, 1, veranstaltung);
        Wahlvortrag wahlFuell = Wahlvortrag.create("Wahl Füll", "Inhalt", referent, false, 1, veranstaltung);

        Betrachter betrachter = neuerBetrachter("lehrer.vortraege@test.de", gruppeA);
        Teilnehmer tnA = neuerTeilnehmer("tn-vortraege-a@test.de", gruppeA);

        Prioritaet prioritaet = new Prioritaet(tnA, wahlPrio, 5);
        prioritaet.persist();

        // tnA hat den Pflichtvortrag tatsächlich per QR-Code besucht - die beiden Wahlvorträge nicht.
        Anwesenheit anwesenheit = new Anwesenheit(tnA, veranstaltung, slotPflicht, raum, LocalDateTime.of(2024, 1, 1, 9, 5));
        anwesenheit.persist();

        Planungsergebnis ergebnis = new Planungsergebnis();
        ergebnis.setVeranstaltung(veranstaltung);
        ergebnis.setPubliziert(true);
        ergebnis.setSolverConfig(new SolverConfig(60, 1, 1, false));
        Planungsergebnis.MinizincResult result = new Planungsergebnis.MinizincResult();
        result.teilnehmer_oids = new long[]{tnA.getId()};
        result.wahlvortrag_oids = new long[]{wahlPrio.getId(), wahlFuell.getId()};
        result.slot_oids = new long[]{slotWahlPrio.getId(), slotWahlFuell.getId()};
        result.raum_oids = new long[]{raum.getId()};
        result.instanz_slot = new int[][]{{1}, {2}};
        result.instanz_raum = new int[][]{{1}, {1}};
        result.besucht = new boolean[][][]{{{true}, {true}}};
        ergebnis.setJsonErgebnis(result.toJson());
        ergebnis.persist();

        Map<Long, List<BetrachterTeilnehmerVortragDto>> vortraege = betrachterService.getTeilnehmerVortraege(betrachter, veranstaltung);

        List<BetrachterTeilnehmerVortragDto> zeilen = vortraege.get(tnA.getId());
        assertThat(zeilen).hasSize(3);

        assertThat(zeilen.get(0).vortragTitel).isEqualTo("Pflicht X");
        assertThat(zeilen.get(0).prioAnzeige).isEqualTo("Pflicht");
        assertThat(zeilen.get(0).besuchtAnzeige).isEqualTo("Raum 1 · 09:00");

        assertThat(zeilen.get(1).vortragTitel).isEqualTo("Wahl Prio");
        assertThat(zeilen.get(1).prioAnzeige).isEqualTo("5");
        assertThat(zeilen.get(1).besuchtAnzeige).isEqualTo("nicht besucht");

        assertThat(zeilen.get(2).vortragTitel).isEqualTo("Wahl Füll");
        assertThat(zeilen.get(2).prioAnzeige).isEqualTo("Füll");
        assertThat(zeilen.get(2).besuchtAnzeige).isEqualTo("nicht besucht");
    }


    @Test
    @Transactional
    void getTeilnehmerVortraege_zeigtUnangemeldetenBesuchAlsEigeneZeile() {
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

        Slot slotWalkIn = new Slot("Slot Walk-in", LocalDateTime.of(2024, 1, 1, 10, 0), LocalDateTime.of(2024, 1, 1, 11, 0), veranstaltung);
        slotWalkIn.persist();
        veranstaltung.addSlot(slotWalkIn);

        Referent referent = new Referent();
        referent.assignLoginName("referent.walkin@test.de");
        referent.setEmail("referent.walkin@test.de");
        referent.persist();

        Wahlvortrag wahlWalkIn = Wahlvortrag.create("Wahl Walk-in", "Inhalt", referent, false, 1, veranstaltung);

        Betrachter betrachter = neuerBetrachter("lehrer.walkin@test.de", gruppeA);
        // tnB ist sichtbar, aber weder für einen Pflichtvortrag noch für "Wahl Walk-in" eingeplant.
        Teilnehmer tnB = neuerTeilnehmer("tn-walkin-b@test.de", gruppeA);

        Anwesenheit anwesenheit = new Anwesenheit(tnB, veranstaltung, slotWalkIn, raum, LocalDateTime.of(2024, 1, 1, 10, 5));
        anwesenheit.persist();

        Planungsergebnis ergebnis = new Planungsergebnis();
        ergebnis.setVeranstaltung(veranstaltung);
        ergebnis.setPubliziert(true);
        ergebnis.setSolverConfig(new SolverConfig(60, 1, 1, false));
        Planungsergebnis.MinizincResult result = new Planungsergebnis.MinizincResult();
        // "Wahl Walk-in" ist in Raum/Slot eingeplant, aber tnB taucht nirgends im Solver-Ergebnis
        // auf (keine Prioritaet, keine Zuweisung) - trotzdem ist der Raum belegt, und der QR-Scan
        // von tnB dort muss als "unangemeldet" erscheinen.
        result.teilnehmer_oids = new long[]{};
        result.wahlvortrag_oids = new long[]{wahlWalkIn.getId()};
        result.slot_oids = new long[]{slotWalkIn.getId()};
        result.raum_oids = new long[]{raum.getId()};
        result.instanz_slot = new int[][]{{1}};
        result.instanz_raum = new int[][]{{1}};
        result.besucht = new boolean[][][]{};
        ergebnis.setJsonErgebnis(result.toJson());
        ergebnis.persist();

        Map<Long, List<BetrachterTeilnehmerVortragDto>> vortraege = betrachterService.getTeilnehmerVortraege(betrachter, veranstaltung);

        List<BetrachterTeilnehmerVortragDto> zeilen = vortraege.get(tnB.getId());
        assertThat(zeilen).hasSize(1);
        assertThat(zeilen.get(0).vortragTitel).isEqualTo("Wahl Walk-in");
        assertThat(zeilen.get(0).prioAnzeige).isEqualTo("unangemeldet");
        assertThat(zeilen.get(0).besuchtAnzeige).isEqualTo("Raum 1 · 10:00");
    }


    private static final String MINIMAL_MINIZINC_JSON = """
        {
          "instanz_slot": [[]],
          "instanz_raum": [[]],
          "besucht": [[[]]],
          "teilnehmer_oids": [],
          "wahlvortrag_oids": [],
          "slot_oids": [],
          "raum_oids": []
        }
        """;
}
