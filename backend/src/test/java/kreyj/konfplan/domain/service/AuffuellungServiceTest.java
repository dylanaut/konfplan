package kreyj.konfplan.domain.service;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import kreyj.konfplan.persistence.Berufsfeld;
import kreyj.konfplan.persistence.Gebaeude;
import kreyj.konfplan.persistence.Gebaeudetyp;
import kreyj.konfplan.persistence.Planungsergebnis;
import kreyj.konfplan.persistence.Prioritaet;
import kreyj.konfplan.persistence.Raum;
import kreyj.konfplan.persistence.Referent;
import kreyj.konfplan.persistence.Slot;
import kreyj.konfplan.persistence.Teilnehmer;
import kreyj.konfplan.persistence.Veranstaltung;
import kreyj.konfplan.persistence.Wahlvortrag;
import kreyj.konfplan.presentation.DatabaseCleaner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
public class AuffuellungServiceTest extends DatabaseCleaner {

    @Inject
    AuffuellungService auffuellungService;

    private Long schuleId;
    private Long raumGrossId;

    @BeforeEach
    @Transactional
    public void setup() {
        Gebaeude schule = new Gebaeude("Test Schule", "Testort", "Teststrasse", "4711", Gebaeudetyp.SCHULE);
        schule.persist();

        Raum raumGross = new Raum("Raum Groß", 5);
        raumGross.persist();
        schule.addRaum(raumGross);

        schuleId = schule.getId();
        raumGrossId = raumGross.getId();
    }


    private Veranstaltung neueVeranstaltung(String name) {
        Veranstaltung veranstaltung = new Veranstaltung();
        veranstaltung.setName(name);
        veranstaltung.setBeginntAm(LocalDateTime.now());
        veranstaltung.addGebaeude(Gebaeude.<Gebaeude>findById(schuleId));
        veranstaltung.persist();

        Referent referent = new Referent();
        referent.setEmail("referent@test.com");
        referent.setFirstName("Max");
        referent.setLastName("Mustermann");
        referent.persist();
        referent.addVeranstaltung(veranstaltung);

        return veranstaltung;
    }


    private Slot neuerSlot(Veranstaltung veranstaltung, int stundenOffset) {
        Slot slot = new Slot("Slot", LocalDateTime.now().plusHours(stundenOffset),
            LocalDateTime.now().plusHours(stundenOffset + 1), veranstaltung);
        slot.persist();
        veranstaltung.addSlot(slot);
        return slot;
    }


    private Wahlvortrag neuerWahlvortrag(Veranstaltung veranstaltung, String titel, Berufsfeld berufsfeld) {
        Referent referent = Referent.find("email", "referent@test.com").firstResult();
        Wahlvortrag wv = new Wahlvortrag();
        wv.setTitel(titel);
        wv.setReferent(referent);
        wv.setVeranstaltung(veranstaltung);
        wv.setBerufsfeld(berufsfeld);
        wv.persist();
        return wv;
    }


    private Teilnehmer neuerTeilnehmer(Veranstaltung veranstaltung, String email) {
        Teilnehmer tn = new Teilnehmer();
        tn.setEmail(email);
        tn.setFirstName(email);
        tn.setLastName("Test");
        tn.addGruppe("A");
        tn.persist();
        tn.addVeranstaltung(veranstaltung);
        return tn;
    }


    private Planungsergebnis.MinizincResult ergebnis(long[] tnOids, long[] wvOids, long[] slotOids, long[] raumOids,
                                                      int[][] instanzSlot, int[][] instanzRaum, boolean[][][] besucht) {
        Planungsergebnis.MinizincResult result = new Planungsergebnis.MinizincResult();
        result.teilnehmer_oids = tnOids;
        result.wahlvortrag_oids = wvOids;
        result.slot_oids = slotOids;
        result.raum_oids = raumOids;
        result.instanz_slot = instanzSlot;
        result.instanz_raum = instanzRaum;
        result.besucht = besucht;
        return result;
    }


    @Test
    @Transactional
    public void eigenePrioritaet_gewinntVorZufaelligerAlternative() {
        Veranstaltung veranstaltung = neueVeranstaltung("Auffuellung-Test-1");
        Slot slot1 = neuerSlot(veranstaltung, 1);
        Wahlvortrag wv1 = neuerWahlvortrag(veranstaltung, "Wahlvortrag 1", null);
        Wahlvortrag wv2 = neuerWahlvortrag(veranstaltung, "Wahlvortrag 2", null);
        Teilnehmer tnFrei = neuerTeilnehmer(veranstaltung, "frei@test.com");

        new Prioritaet(tnFrei, wv2, 1).persist();

        Planungsergebnis.MinizincResult result = ergebnis(
            new long[]{tnFrei.getId()},
            new long[]{wv1.getId(), wv2.getId()},
            new long[]{slot1.getId()},
            new long[]{raumGrossId},
            new int[][]{{1}, {1}},
            new int[][]{{1}, {1}},
            new boolean[][][]{{{false}, {false}}});

        auffuellungService.fuelleAuf(veranstaltung, result);

        assertThat(result.besucht[0][1][0]).describedAs("eigene Priorität für Wahlvortrag 2 sollte gewinnen").isTrue();
        assertThat(result.besucht[0][0][0]).describedAs("Wahlvortrag 1 (keine Priorität) sollte nicht gewählt werden").isFalse();
    }


    @Test
    @Transactional
    public void berufsfeldAbleitung_wirdGenutztWennEigenePrioritaetNichtLaeuft() {
        Veranstaltung veranstaltung = neueVeranstaltung("Auffuellung-Test-2");
        Slot slot1 = neuerSlot(veranstaltung, 1);
        // wvPraeferiert hat eine Priorität, läuft aber in keiner Instanz (instanz_slot = 0 überall)
        Wahlvortrag wvPraeferiert = neuerWahlvortrag(veranstaltung, "Präferierter Vortrag", Berufsfeld.IT_UND_COMPUTER);
        Wahlvortrag wvBerufsfeldMatch = neuerWahlvortrag(veranstaltung, "Berufsfeld-Treffer", Berufsfeld.IT_UND_COMPUTER);
        Teilnehmer tnFrei = neuerTeilnehmer(veranstaltung, "frei@test.com");

        new Prioritaet(tnFrei, wvPraeferiert, 1).persist();

        Planungsergebnis.MinizincResult result = ergebnis(
            new long[]{tnFrei.getId()},
            new long[]{wvPraeferiert.getId(), wvBerufsfeldMatch.getId()},
            new long[]{slot1.getId()},
            new long[]{raumGrossId},
            new int[][]{{0}, {1}},
            new int[][]{{0}, {1}},
            new boolean[][][]{{{false}, {false}}});

        auffuellungService.fuelleAuf(veranstaltung, result);

        assertThat(result.besucht[0][1][0])
            .describedAs("Berufsfeld-Treffer sollte über die abgeleitete Präferenz gewählt werden").isTrue();
    }


    @Test
    @Transactional
    public void kapazitaet_wirdBeiKonkurrenzNichtUeberschritten() {
        Veranstaltung veranstaltung = neueVeranstaltung("Auffuellung-Test-3");
        Slot slot1 = neuerSlot(veranstaltung, 1);
        Raum raumKlein = new Raum("Raum Klein", 1);
        raumKlein.persist();
        Gebaeude.<Gebaeude>findById(schuleId).addRaum(raumKlein);

        Wahlvortrag wv1 = neuerWahlvortrag(veranstaltung, "Wahlvortrag 1", null);
        Teilnehmer tn1 = neuerTeilnehmer(veranstaltung, "frei1@test.com");
        Teilnehmer tn2 = neuerTeilnehmer(veranstaltung, "frei2@test.com");

        Planungsergebnis.MinizincResult result = ergebnis(
            new long[]{tn1.getId(), tn2.getId()},
            new long[]{wv1.getId()},
            new long[]{slot1.getId()},
            new long[]{raumKlein.getId()},
            new int[][]{{1}},
            new int[][]{{1}},
            new boolean[][][]{{{false}}, {{false}}});

        auffuellungService.fuelleAuf(veranstaltung, result);

        long anzahlBesucht = (result.besucht[0][0][0] ? 1 : 0) + (result.besucht[1][0][0] ? 1 : 0);
        assertThat(anzahlBesucht).describedAs("Kapazität 1 darf nicht überschritten werden").isEqualTo(1);
    }


    @Test
    @Transactional
    public void nichtVerfuegbarerTeilnehmer_wirdNichtEingeplant() {
        Veranstaltung veranstaltung = neueVeranstaltung("Auffuellung-Test-4");
        Slot slot1 = neuerSlot(veranstaltung, 1);
        Wahlvortrag wv1 = neuerWahlvortrag(veranstaltung, "Wahlvortrag 1", null);
        Teilnehmer tnAbwesend = neuerTeilnehmer(veranstaltung, "abwesend@test.com");
        tnAbwesend.updateVerfuegbarkeit(slot1, veranstaltung, false);

        Planungsergebnis.MinizincResult result = ergebnis(
            new long[]{tnAbwesend.getId()},
            new long[]{wv1.getId()},
            new long[]{slot1.getId()},
            new long[]{raumGrossId},
            new int[][]{{1}},
            new int[][]{{1}},
            new boolean[][][]{{{false}}});

        auffuellungService.fuelleAuf(veranstaltung, result);

        assertThat(result.besucht[0][0][0])
            .describedAs("nicht verfügbarer Teilnehmer darf nicht per Auffüllung eingeplant werden").isFalse();
    }


    @Test
    @Transactional
    public void bereitsImSlotVerplanterTeilnehmer_wirdNichtZusaetzlichEingeplant() {
        Veranstaltung veranstaltung = neueVeranstaltung("Auffuellung-Test-5");
        Slot slot1 = neuerSlot(veranstaltung, 1);
        Wahlvortrag wv1 = neuerWahlvortrag(veranstaltung, "Wahlvortrag 1", null);
        Wahlvortrag wv2 = neuerWahlvortrag(veranstaltung, "Wahlvortrag 2", null);
        Teilnehmer tn = neuerTeilnehmer(veranstaltung, "belegt@test.com");

        Planungsergebnis.MinizincResult result = ergebnis(
            new long[]{tn.getId()},
            new long[]{wv1.getId(), wv2.getId()},
            new long[]{slot1.getId()},
            new long[]{raumGrossId},
            new int[][]{{1}, {1}},
            new int[][]{{1}, {1}},
            // tn ist bereits in wv1 (Instanz 0) verplant
            new boolean[][][]{{{true}, {false}}});

        auffuellungService.fuelleAuf(veranstaltung, result);

        assertThat(result.besucht[0][1][0])
            .describedAs("bereits im Slot verplanter Teilnehmer darf nicht zusätzlich in Wahlvortrag 2 eingeplant werden")
            .isFalse();
    }


    @Test
    @Transactional
    public void maxEineInstanzProWahlvortrag_bleibtUeberMehrereSlotsHinwegErhalten() {
        Veranstaltung veranstaltung = neueVeranstaltung("Auffuellung-Test-6");
        Slot slot1 = neuerSlot(veranstaltung, 1);
        Slot slot2 = neuerSlot(veranstaltung, 2);
        Wahlvortrag wv1 = neuerWahlvortrag(veranstaltung, "Wahlvortrag 1", null);
        Teilnehmer tnFrei = neuerTeilnehmer(veranstaltung, "frei@test.com");

        new Prioritaet(tnFrei, wv1, 1).persist();

        // wv1 läuft in Instanz 0 (Slot 1) und Instanz 1 (Slot 2), beide mit freier Kapazität.
        Planungsergebnis.MinizincResult result = ergebnis(
            new long[]{tnFrei.getId()},
            new long[]{wv1.getId()},
            new long[]{slot1.getId(), slot2.getId()},
            new long[]{raumGrossId},
            new int[][]{{1, 2}},
            new int[][]{{1, 1}},
            new boolean[][][]{{{false, false}}});

        auffuellungService.fuelleAuf(veranstaltung, result);

        long anzahlInstanzenBesucht = (result.besucht[0][0][0] ? 1 : 0) + (result.besucht[0][0][1] ? 1 : 0);
        assertThat(anzahlInstanzenBesucht)
            .describedAs("Teilnehmer darf nur einer Instanz von Wahlvortrag 1 zugewiesen werden, nicht beiden")
            .isEqualTo(1);
    }
}
