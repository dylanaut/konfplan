package kreyj.konfplan.domain.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import kreyj.konfplan.adapter.in.web.DatabaseCleaner;
import kreyj.konfplan.adapter.in.web.dto.UmplanungErgebnisDto;
import kreyj.konfplan.domain.exception.BusinessException;
import kreyj.konfplan.persistence.Gebaeude;
import kreyj.konfplan.persistence.Gebaeudetyp;
import kreyj.konfplan.persistence.Nachricht;
import kreyj.konfplan.persistence.NachrichtKategorie;
import kreyj.konfplan.persistence.Neigung;
import kreyj.konfplan.persistence.Planungsergebnis;
import kreyj.konfplan.persistence.Raum;
import kreyj.konfplan.persistence.Referent;
import kreyj.konfplan.persistence.Slot;
import kreyj.konfplan.persistence.Teilnehmer;
import kreyj.konfplan.persistence.Veranstaltung;
import kreyj.konfplan.persistence.Wahlvortrag;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@QuarkusTest
class UmplanungServiceTest extends DatabaseCleaner {

    @Inject
    UmplanungService umplanungService;

    @Inject
    PlanService planService;

    @Inject
    NachrichtService nachrichtService;

    @Inject
    ObjectMapper objectMapper;

    private Long schuleId;
    private Long raumGrossId;


    @BeforeEach
    @Transactional
    void setup() {
        Gebaeude schule = new Gebaeude("Test Schule", "Testort", "Teststrasse", "4711", Gebaeudetyp.SCHULE);
        schule.persist();

        Raum raumGross = new Raum("Raum Groß", 10);
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
        referent.assignLoginName("referent-" + name + "@test.com");
        referent.setEmail("referent-" + name + "@test.com");
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


    private Wahlvortrag neuerWahlvortrag(Veranstaltung veranstaltung, String titel, Neigung... neigungen) {
        Referent referent = Referent.find("email", "referent-" + veranstaltung.getName() + "@test.com").firstResult();
        Wahlvortrag wv = new Wahlvortrag();
        wv.setTitel(titel);
        wv.setReferent(referent);
        wv.setVeranstaltung(veranstaltung);
        wv.setNeigungen(new HashSet<>(Arrays.asList(neigungen)));
        wv.persist();
        return wv;
    }


    private Teilnehmer neuerTeilnehmer(Veranstaltung veranstaltung, String email, Neigung... neigungen) {
        Teilnehmer tn = new Teilnehmer();
        tn.assignLoginName(email);
        tn.setEmail(email);
        tn.setFirstName(email);
        tn.setLastName("Test");
        tn.addGruppe("A");
        tn.setNeigungen(new HashSet<>(Arrays.asList(neigungen)));
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


    private Planungsergebnis persistiereErgebnis(Veranstaltung veranstaltung, Planungsergebnis.MinizincResult result) {
        Planungsergebnis ergebnis = new Planungsergebnis();
        ergebnis.setVeranstaltung(veranstaltung);
        ergebnis.setJsonErgebnis(result.toJson(objectMapper));
        ergebnis.setErsteller("test-organisator");
        ergebnis.setErstelltAm(LocalDateTime.now());
        ergebnis.setPubliziert(true);
        ergebnis.persist();
        return ergebnis;
    }


    @Test
    @Transactional
    void ausfallOhneAlternative_befreitTeilnehmerUndMarkiertInstanzAlsAusgefallen() {
        Veranstaltung veranstaltung = neueVeranstaltung("Umplanung-Test-1");
        Slot slot1 = neuerSlot(veranstaltung, 1);
        Wahlvortrag wv1 = neuerWahlvortrag(veranstaltung, "Wahlvortrag 1");
        Teilnehmer tn = neuerTeilnehmer(veranstaltung, "tn1@test.com");

        Planungsergebnis ergebnis = persistiereErgebnis(veranstaltung, ergebnis(
            new long[]{tn.getId()},
            new long[]{wv1.getId()},
            new long[]{slot1.getId()},
            new long[]{raumGrossId},
            new int[][]{{1}},
            new int[][]{{1}},
            new boolean[][][]{{{true}}}));

        UmplanungErgebnisDto dto = umplanungService.vortragsInstanzUmplanen(veranstaltung, ergebnis.getId(), wv1.getId(), 0, "organisator@test.com");

        assertThat(dto.umverteilt).isEmpty();
        assertThat(dto.nichtPlatziert).containsExactly(tn.getFullName());

        Planungsergebnis.MinizincResult aktualisiert = ladeAktualisiertesErgebnis(ergebnis.getId());
        assertThat(aktualisiert.besucht[0][0][0]).describedAs("Teilnehmer muss aus der ausgefallenen Instanz entfernt sein").isFalse();
        assertThat(aktualisiert.istAusgefallen(0, 0)).isTrue();
    }


    @Test
    @Transactional
    void umverteilung_bevorzugtInstanzMitGemeinsamerNeigung() {
        Veranstaltung veranstaltung = neueVeranstaltung("Umplanung-Test-2");
        Slot slot1 = neuerSlot(veranstaltung, 1);
        Wahlvortrag wvAusfall = neuerWahlvortrag(veranstaltung, "Fällt aus", Neigung.TECHNISCH);
        Wahlvortrag wvOhneMatch = neuerWahlvortrag(veranstaltung, "Ohne Übereinstimmung", Neigung.KREATIV);
        Wahlvortrag wvMitMatch = neuerWahlvortrag(veranstaltung, "Mit Übereinstimmung", Neigung.TECHNISCH);
        Teilnehmer tn = neuerTeilnehmer(veranstaltung, "tn1@test.com", Neigung.TECHNISCH);

        Planungsergebnis ergebnis = persistiereErgebnis(veranstaltung, ergebnis(
            new long[]{tn.getId()},
            new long[]{wvAusfall.getId(), wvOhneMatch.getId(), wvMitMatch.getId()},
            new long[]{slot1.getId()},
            new long[]{raumGrossId},
            new int[][]{{1}, {1}, {1}},
            new int[][]{{1}, {1}, {1}},
            new boolean[][][]{{{true}, {false}, {false}}}));

        UmplanungErgebnisDto dto = umplanungService.vortragsInstanzUmplanen(veranstaltung, ergebnis.getId(), wvAusfall.getId(), 0, "organisator@test.com");

        assertThat(dto.nichtPlatziert).isEmpty();
        assertThat(dto.umverteilt).hasSize(1);
        assertThat(dto.umverteilt.get(0).neuerVortragTitel).isEqualTo("Mit Übereinstimmung");

        Planungsergebnis.MinizincResult aktualisiert = ladeAktualisiertesErgebnis(ergebnis.getId());
        assertThat(aktualisiert.besucht[0][2][0]).describedAs("Teilnehmer sollte im Vortrag mit gemeinsamer Neigung landen").isTrue();
        assertThat(aktualisiert.besucht[0][1][0]).isFalse();
    }


    @Test
    @Transactional
    void kapazitaetsgrenze_wirdBeiUmverteilungNichtUeberschritten() {
        Veranstaltung veranstaltung = neueVeranstaltung("Umplanung-Test-3");
        Slot slot1 = neuerSlot(veranstaltung, 1);
        Raum raumKlein = new Raum("Raum Klein", 1);
        raumKlein.persist();
        Gebaeude.<Gebaeude>findById(schuleId).addRaum(raumKlein);

        Wahlvortrag wvAusfall = neuerWahlvortrag(veranstaltung, "Fällt aus");
        Wahlvortrag wvKandidat = neuerWahlvortrag(veranstaltung, "Kandidat");
        Teilnehmer tn1 = neuerTeilnehmer(veranstaltung, "tn1@test.com");
        Teilnehmer tn2 = neuerTeilnehmer(veranstaltung, "tn2@test.com");

        Planungsergebnis ergebnis = persistiereErgebnis(veranstaltung, ergebnis(
            new long[]{tn1.getId(), tn2.getId()},
            new long[]{wvAusfall.getId(), wvKandidat.getId()},
            new long[]{slot1.getId()},
            new long[]{raumGrossId, raumKlein.getId()},
            new int[][]{{1}, {1}},
            new int[][]{{1}, {2}},
            new boolean[][][]{{{true}, {false}}, {{true}, {false}}}));

        UmplanungErgebnisDto dto = umplanungService.vortragsInstanzUmplanen(veranstaltung, ergebnis.getId(), wvAusfall.getId(), 0, "organisator@test.com");

        assertThat(dto.umverteilt).hasSize(1);
        assertThat(dto.nichtPlatziert).hasSize(1);

        Planungsergebnis.MinizincResult aktualisiert = ladeAktualisiertesErgebnis(ergebnis.getId());
        int anzahlImKandidat = (aktualisiert.besucht[0][1][0] ? 1 : 0) + (aktualisiert.besucht[1][1][0] ? 1 : 0);
        assertThat(anzahlImKandidat).describedAs("Kapazität 1 des Kandidaten-Raums darf nicht überschritten werden").isEqualTo(1);
    }


    @Test
    @Transactional
    void keinKandidatImSlot_fuehrtZuNichtPlatziertOhneException() {
        Veranstaltung veranstaltung = neueVeranstaltung("Umplanung-Test-4");
        Slot slot1 = neuerSlot(veranstaltung, 1);
        Slot slot2 = neuerSlot(veranstaltung, 2);
        Wahlvortrag wvAusfall = neuerWahlvortrag(veranstaltung, "Fällt aus");
        Wahlvortrag wvAndererSlot = neuerWahlvortrag(veranstaltung, "Anderer Slot");
        Teilnehmer tn = neuerTeilnehmer(veranstaltung, "tn1@test.com");

        Planungsergebnis ergebnis = persistiereErgebnis(veranstaltung, ergebnis(
            new long[]{tn.getId()},
            new long[]{wvAusfall.getId(), wvAndererSlot.getId()},
            new long[]{slot1.getId(), slot2.getId()},
            new long[]{raumGrossId},
            new int[][]{{1}, {2}},
            new int[][]{{1}, {1}},
            new boolean[][][]{{{true}, {false}}}));

        UmplanungErgebnisDto dto = umplanungService.vortragsInstanzUmplanen(veranstaltung, ergebnis.getId(), wvAusfall.getId(), 0, "organisator@test.com");

        assertThat(dto.umverteilt).isEmpty();
        assertThat(dto.nichtPlatziert).containsExactly(tn.getFullName());
    }


    @Test
    @Transactional
    void bereitsAusgefalleneInstanz_kannNichtErneutMarkiertWerden() {
        Veranstaltung veranstaltung = neueVeranstaltung("Umplanung-Test-5");
        Slot slot1 = neuerSlot(veranstaltung, 1);
        Wahlvortrag wv1 = neuerWahlvortrag(veranstaltung, "Wahlvortrag 1");
        Teilnehmer tn = neuerTeilnehmer(veranstaltung, "tn1@test.com");

        Planungsergebnis ergebnis = persistiereErgebnis(veranstaltung, ergebnis(
            new long[]{tn.getId()},
            new long[]{wv1.getId()},
            new long[]{slot1.getId()},
            new long[]{raumGrossId},
            new int[][]{{1}},
            new int[][]{{1}},
            new boolean[][][]{{{true}}}));

        umplanungService.vortragsInstanzUmplanen(veranstaltung, ergebnis.getId(), wv1.getId(), 0, "organisator@test.com");

        assertThatThrownBy(() -> umplanungService.vortragsInstanzUmplanen(veranstaltung, ergebnis.getId(), wv1.getId(), 0, "organisator@test.com"))
            .isInstanceOf(BusinessException.class);
    }


    @Test
    @Transactional
    void benachrichtigungen_werdenAnReferentUndBetroffeneTeilnehmerVersendet() {
        Veranstaltung veranstaltung = neueVeranstaltung("Umplanung-Test-6");
        Slot slot1 = neuerSlot(veranstaltung, 1);
        Wahlvortrag wvAusfall = neuerWahlvortrag(veranstaltung, "Fällt aus");
        Wahlvortrag wvKandidat = neuerWahlvortrag(veranstaltung, "Kandidat");
        Teilnehmer tn = neuerTeilnehmer(veranstaltung, "tn1@test.com");

        Planungsergebnis ergebnis = persistiereErgebnis(veranstaltung, ergebnis(
            new long[]{tn.getId()},
            new long[]{wvAusfall.getId(), wvKandidat.getId()},
            new long[]{slot1.getId()},
            new long[]{raumGrossId},
            new int[][]{{1}, {1}},
            new int[][]{{1}, {1}},
            new boolean[][][]{{{true}, {false}}}));

        umplanungService.vortragsInstanzUmplanen(veranstaltung, ergebnis.getId(), wvAusfall.getId(), 0, "organisator@test.com");

        assertThat(nachrichtService.getNachrichtenFuerNutzer("tn1@test.com")).hasSize(1);
        assertThat(nachrichtService.getNachrichtenFuerNutzer("referent-Umplanung-Test-6@test.com")).hasSize(1);

        List<Nachricht> teilnehmerNachrichten = nachrichtService.getNachrichtenFuerNutzer("tn1@test.com");
        assertThat(teilnehmerNachrichten.get(0).getKategorie()).isEqualTo(NachrichtKategorie.VORTRAG_AUSGEFALLEN);
    }


    private Planungsergebnis.MinizincResult ladeAktualisiertesErgebnis(Long ergebnisId) {
        Planungsergebnis neu = Planungsergebnis.findById(ergebnisId);
        try {
            return objectMapper.readValue(neu.getJsonErgebnis(), Planungsergebnis.MinizincResult.class);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
