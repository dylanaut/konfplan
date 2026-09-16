package kreyj.konfplan.domain.service;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import kreyj.konfplan.adapter.in.web.DatabaseCleaner;
import kreyj.konfplan.adapter.in.web.dto.NutzerVerfuegbarkeitDto;
import kreyj.konfplan.adapter.in.web.dto.SolverConfig;
import kreyj.konfplan.adapter.in.web.dto.ZuweisungDto;
import kreyj.konfplan.persistence.Betrachter;
import kreyj.konfplan.persistence.Gebaeude;
import kreyj.konfplan.persistence.Gebaeudetyp;
import kreyj.konfplan.persistence.Gruppenkategorie;
import kreyj.konfplan.persistence.GruppenkategorieWert;
import kreyj.konfplan.persistence.Pflichtvortrag;
import kreyj.konfplan.persistence.Planungsergebnis;
import kreyj.konfplan.persistence.Raum;
import kreyj.konfplan.persistence.Referent;
import kreyj.konfplan.persistence.Slot;
import kreyj.konfplan.persistence.Teilnehmer;
import kreyj.konfplan.persistence.Veranstaltung;
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
            tn.addGruppenwert(wert);
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
            b.addGruppenwert(wert);
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
        tnAndereVeranstaltung.addGruppenwert(gruppeA);

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
