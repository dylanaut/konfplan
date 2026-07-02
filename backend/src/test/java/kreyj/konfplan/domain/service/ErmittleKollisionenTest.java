package kreyj.konfplan.domain.service;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.transaction.Transactional;
import kreyj.konfplan.persistence.Gebaeude;
import kreyj.konfplan.persistence.Gebaeudetyp;
import kreyj.konfplan.persistence.NutzerVerfuegbarkeit;
import kreyj.konfplan.persistence.Pflichtvortrag;
import kreyj.konfplan.persistence.Raum;
import kreyj.konfplan.persistence.RaumVerfuegbarkeit;
import kreyj.konfplan.persistence.Referent;
import kreyj.konfplan.persistence.Slot;
import kreyj.konfplan.persistence.Teilnehmer;
import kreyj.konfplan.persistence.Veranstaltung;
import kreyj.konfplan.presentation.DatabaseCleaner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static kreyj.konfplan.persistence.NutzerVerfuegbarkeitId.nvIdL;
import static kreyj.konfplan.persistence.RaumVerfuegbarkeitId.rvIdL;
import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
public class ErmittleKollisionenTest extends DatabaseCleaner {

    // ermittleKollisionen nutzt keine injizierten Felder -> direkte Instanziierung genügt.
    private final PlanErstellungService service = new PlanErstellungService(null, null, null);

    private Long veranstaltung_id;
    private Long slot1_id;
    private Long tnA_id;
    private Long raum1_id;
    private Long referent_id;

    @BeforeEach
    @Transactional
    void setUp() {
        Veranstaltung veranstaltung = new Veranstaltung();
        veranstaltung.setName("Kollisions-Test Event");
        veranstaltung.setBeginntAm(LocalDateTime.now());
        veranstaltung.setEndetAm(LocalDateTime.now().plusDays(1));
        veranstaltung.persistAndFlush();
        veranstaltung_id = veranstaltung.getId();

        Slot slot1 = new Slot("Slot 1", veranstaltung.getBeginntAm().plusHours(1),
                veranstaltung.getBeginntAm().plusHours(2), veranstaltung);
        Slot slot2 = new Slot("Slot 2", veranstaltung.getBeginntAm().plusHours(3),
                veranstaltung.getBeginntAm().plusHours(4), veranstaltung);
        veranstaltung.addSlot(slot1);
        veranstaltung.addSlot(slot2);
        veranstaltung.persistAndFlush();
        slot1_id = slot1.getId();

        Raum raum1 = new Raum("Raum 1", 30);
        Gebaeude gebaeude = new Gebaeude("Testgebäude", "Ort", "Straße", "12345", Gebaeudetyp.SCHULE);
        gebaeude.addRaum(raum1);
        gebaeude.persistAndFlush();
        raum1_id = raum1.getId();
        veranstaltung.addGebaeude(gebaeude);

        Teilnehmer tnA = new Teilnehmer();
        tnA.setEmail("tn_a@test.com");
        tnA.setFirstName("Anna");
        tnA.setLastName("Adam");
        tnA.addGruppe("GruppeA");
        tnA.persistAndFlush();
        tnA_id = tnA.getId();

        Referent referent = new Referent();
        referent.setEmail("referent@test.com");
        referent.persistAndFlush();
        referent_id = referent.getId();

        // Erzeugt die initialen Verfügbarkeiten für Teilnehmer und Raum
        veranstaltung.addNutzer(tnA);
        veranstaltung.addNutzer(referent);

        // Pflichtvortrag für GruppeA in Slot 1 / Raum 1.
        // create() entfernt Slot 1 konsistent aus NV (GruppeA) und RV (Raum 1).
        Pflichtvortrag.create("PV", "Inhalt", referent, "GruppeA", raum1, slot1, veranstaltung);
    }

    @Test
    @Transactional
    void konsistenteDaten_liefernKeineKollisionen() {
        Veranstaltung veranstaltung = Veranstaltung.findById(veranstaltung_id);

        List<Kollision> kollisionen = service.pruefeKollisionen(veranstaltung);

        assertThat(kollisionen).isEmpty();
    }

    @Test
    @Transactional
    void teilnehmerVerfuegbarkeitImPflichtslot_wirdAlsKollisionErkannt() {
        // Inkonsistenz herstellen: Slot 1 wieder als verfügbar für TN der Pflichtgruppe markieren
        NutzerVerfuegbarkeit nv = NutzerVerfuegbarkeit.findById(nvIdL(tnA_id, veranstaltung_id));
        nv.addSlot(slot1_id);

        Veranstaltung veranstaltung = Veranstaltung.findById(veranstaltung_id);
        List<Kollision> kollisionen = service.pruefeKollisionen(veranstaltung);

        assertThat(kollisionen).hasSize(1);
        Kollision kollision = kollisionen.get(0);
        assertThat(kollision.typ()).isEqualTo(Kollision.Typ.TEILNEHMER_VERFUEGBARKEIT);
        assertThat(kollision.nachricht())
                .contains("Anna Adam")
                .contains("GruppeA")
                .contains("Verfügbarkeits-Kollision");
    }

    @Test
    @Transactional
    void raumVerfuegbarkeitImPflichtslot_wirdAlsKollisionErkannt() {
        // Inkonsistenz herstellen: Slot 1 wieder als verfügbar für den Pflichtraum markieren
        RaumVerfuegbarkeit rv = RaumVerfuegbarkeit.findById(rvIdL(raum1_id, veranstaltung_id));
        rv.addSlot(slot1_id);

        Veranstaltung veranstaltung = Veranstaltung.findById(veranstaltung_id);
        List<Kollision> kollisionen = service.pruefeKollisionen(veranstaltung);

        assertThat(kollisionen).hasSize(1);
        Kollision kollision = kollisionen.get(0);
        assertThat(kollision.typ()).isEqualTo(Kollision.Typ.RAUM_SLOT);
        assertThat(kollision.nachricht())
                .contains("Raum 1")
                .contains("Wahlvorträge");
    }

    @Test
    @Transactional
    void referentVerfuegbarkeitImPflichtslot_wirdAlsKollisionErkannt() {
        // Inkonsistenz herstellen: Slot 1 wieder als verfügbar für den Referenten markieren
        NutzerVerfuegbarkeit nv = NutzerVerfuegbarkeit.findById(nvIdL(referent_id, veranstaltung_id));
        nv.addSlot(slot1_id);

        Veranstaltung veranstaltung = Veranstaltung.findById(veranstaltung_id);
        List<Kollision> kollisionen = service.pruefeKollisionen(veranstaltung);

        assertThat(kollisionen).hasSize(1);
        Kollision kollision = kollisionen.get(0);
        assertThat(kollision.typ()).isEqualTo(Kollision.Typ.REFERENT_VERFUEGBARKEIT);
        assertThat(kollision.nachricht())
                .contains("Verfügbarkeits-Kollision")
                .contains("PV");
    }

    @Test
    @Transactional
    void teilnehmerNichtInPflichtgruppe_erzeugtKeineKollision() {
        // TN aus einer anderen Gruppe ist im Pflichtslot verfügbar -> keine Kollision
        Veranstaltung veranstaltung = Veranstaltung.findById(veranstaltung_id);
        Teilnehmer tnB = new Teilnehmer();
        tnB.setEmail("tn_b@test.com");
        tnB.addGruppe("GruppeB");
        tnB.persistAndFlush();
        veranstaltung.addNutzer(tnB);

        // tnB ist (regulär) in Slot 1 verfügbar, gehört aber nicht zur Pflichtgruppe
        NutzerVerfuegbarkeit nvB = NutzerVerfuegbarkeit.findById(nvIdL(tnB.getId(), veranstaltung_id));
        assertThat(nvB.getVerfuegbareSlotIds()).contains(slot1_id);

        List<Kollision> kollisionen = service.pruefeKollisionen(veranstaltung);

        assertThat(kollisionen).isEmpty();
    }
}
