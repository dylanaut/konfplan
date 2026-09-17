package kreyj.konfplan.domain.service;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import kreyj.konfplan.adapter.in.web.DatabaseCleaner;
import kreyj.konfplan.adapter.in.web.dto.AnwesenheitAuswertungEintragDto;
import kreyj.konfplan.adapter.in.web.dto.AnwesenheitCheckinResultDto;
import kreyj.konfplan.adapter.in.web.dto.SolverConfig;
import kreyj.konfplan.persistence.Anwesenheit;
import kreyj.konfplan.persistence.Gebaeude;
import kreyj.konfplan.persistence.Gebaeudetyp;
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

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
class AnwesenheitServiceTest extends DatabaseCleaner {

    @Inject
    AnwesenheitService anwesenheitService;

    private Veranstaltung veranstaltung;
    private Raum raum;
    private Slot aktiverSlot;

    @BeforeEach
    @Transactional
    void setup() {
        veranstaltung = new Veranstaltung();
        veranstaltung.setName("Anwesenheit-Test");
        veranstaltung.setBeginntAm(LocalDateTime.now());
        veranstaltung.persist();

        Gebaeude gebaeude = new Gebaeude();
        gebaeude.setName("Hauptgebäude");
        gebaeude.setTyp(Gebaeudetyp.SCHULE);
        gebaeude.setPostleitzahl("12345");
        gebaeude.setOrt("Testort");
        gebaeude.setStrasse("Teststraße");
        gebaeude.persist();

        raum = new Raum();
        raum.setName("Raum 1");
        raum.setKapazitaet(30);
        raum.persist();
        gebaeude.addRaum(raum);
        veranstaltung.addGebaeude(gebaeude);

        // Zeitfenster um "jetzt" herum, damit findeAktivenSlot() den Slot als aktiv erkennt.
        aktiverSlot = new Slot("Aktueller Slot", LocalDateTime.now().minusMinutes(20), LocalDateTime.now().plusMinutes(20), veranstaltung);
        aktiverSlot.persist();
        veranstaltung.addSlot(aktiverSlot);

        Planungsergebnis ergebnis = new Planungsergebnis();
        ergebnis.setVeranstaltung(veranstaltung);
        ergebnis.setPubliziert(true);
        ergebnis.setSolverConfig(new SolverConfig(60, 1, 1, false));
        ergebnis.setJsonErgebnis(MINIMAL_MINIZINC_JSON);
        ergebnis.persist();
    }


    private Teilnehmer neuerTeilnehmer(String email) {
        Teilnehmer tn = new Teilnehmer();
        tn.assignLoginName(email);
        tn.setEmail(email);
        tn.persist();
        tn.addVeranstaltung(Veranstaltung.findById(veranstaltung.getId()));
        return tn;
    }


    @Test
    @Transactional
    void checkIn_ohneAktivenSlot_persistiertNichts() {
        Veranstaltung ohneSlots = new Veranstaltung();
        ohneSlots.setName("Ohne Slots");
        ohneSlots.setBeginntAm(LocalDateTime.now());
        ohneSlots.persist();

        Teilnehmer tn = neuerTeilnehmer("tn-ohne-slot@test.de");

        AnwesenheitCheckinResultDto ergebnis = anwesenheitService.checkIn(tn, ohneSlots, raum);

        assertThat(ergebnis.aktiverTermin).isFalse();
        assertThat(Anwesenheit.count()).isZero();
    }


    @Test
    @Transactional
    void checkIn_mitAktivemSlotOhneVortrag_persistiertUndMeldetKeinenTitel() {
        Teilnehmer tn = neuerTeilnehmer("tn-frei@test.de");

        AnwesenheitCheckinResultDto ergebnis = anwesenheitService.checkIn(tn, veranstaltung, raum);

        assertThat(ergebnis.aktiverTermin).isTrue();
        assertThat(ergebnis.vortragTitel).isNull();
        assertThat(ergebnis.raumName).isEqualTo("Raum 1");
        assertThat(Anwesenheit.count()).isEqualTo(1);
    }


    @Test
    @Transactional
    void checkIn_mitLaufendemPflichtvortrag_liefertVortragTitel() {
        Referent referent = new Referent();
        referent.assignLoginName("referent.anwesenheit@test.de");
        referent.setEmail("referent.anwesenheit@test.de");
        referent.persist();
        Pflichtvortrag.create("Erste Hilfe", "Inhalt", referent, "Gruppe A", raum, aktiverSlot, veranstaltung);

        Teilnehmer tn = neuerTeilnehmer("tn-pflicht@test.de");

        AnwesenheitCheckinResultDto ergebnis = anwesenheitService.checkIn(tn, veranstaltung, raum);

        assertThat(ergebnis.aktiverTermin).isTrue();
        assertThat(ergebnis.vortragTitel).isEqualTo("Erste Hilfe");
    }


    @Test
    @Transactional
    void checkIn_erneutesEinchecken_aktualisiertBestehendeZeileStattNeueAnzulegen() {
        Teilnehmer tn = neuerTeilnehmer("tn-doppelt@test.de");
        Raum anderer = new Raum();
        anderer.setName("Raum 2");
        anderer.setKapazitaet(20);
        anderer.persist();
        raum.getGebaeude().addRaum(anderer);

        anwesenheitService.checkIn(tn, veranstaltung, raum);
        anwesenheitService.checkIn(tn, veranstaltung, anderer);

        assertThat(Anwesenheit.count()).isEqualTo(1);
        Anwesenheit einzige = Anwesenheit.<Anwesenheit>findAll().firstResult();
        assertThat(einzige.getRaum().getId()).isEqualTo(anderer.getId());
    }


    @Test
    @Transactional
    void getAuswertung_unterscheidetWarDaFehlteUndUnangemeldet() {
        Referent referent = new Referent();
        referent.assignLoginName("referent.auswertung@test.de");
        referent.setEmail("referent.auswertung@test.de");
        referent.persist();
        Pflichtvortrag.create("Erste Hilfe", "Inhalt", referent, "Gruppe A", raum, aktiverSlot, veranstaltung);

        Teilnehmer warDa = neuerTeilnehmer("tn-warda@test.de");
        warDa.addGruppe("Gruppe A");
        Teilnehmer fehlte = neuerTeilnehmer("tn-fehlte@test.de");
        fehlte.addGruppe("Gruppe A");
        Teilnehmer unangemeldet = neuerTeilnehmer("tn-unangemeldet@test.de");

        anwesenheitService.checkIn(warDa, veranstaltung, raum);
        anwesenheitService.checkIn(unangemeldet, veranstaltung, raum);

        List<AnwesenheitAuswertungEintragDto> auswertung = anwesenheitService.getAuswertung(veranstaltung, null);

        assertThat(auswertung).hasSize(1);
        AnwesenheitAuswertungEintragDto eintrag = auswertung.get(0);
        assertThat(eintrag.vortragTitel).isEqualTo("Erste Hilfe");
        assertThat(eintrag.warDa).containsExactly(warDa.getFullName());
        assertThat(eintrag.fehlte).containsExactly(fehlte.getFullName());
        assertThat(eintrag.unangemeldet).containsExactly(unangemeldet.getFullName());
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
