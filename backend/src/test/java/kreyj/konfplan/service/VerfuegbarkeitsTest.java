package kreyj.konfplan.service;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.transaction.Transactional;
import kreyj.konfplan.persistence.Gebaeude;
import kreyj.konfplan.persistence.Gebaeudetyp;
import kreyj.konfplan.persistence.NutzerVerfuegbarkeit;
import kreyj.konfplan.persistence.NutzerVerfuegbarkeitId;
import kreyj.konfplan.persistence.Pflichtvortrag;
import kreyj.konfplan.persistence.Raum;
import kreyj.konfplan.persistence.RaumVerfuegbarkeit;
import kreyj.konfplan.persistence.RaumVerfuegbarkeitId;
import kreyj.konfplan.persistence.Referent;
import kreyj.konfplan.persistence.Slot;
import kreyj.konfplan.persistence.Teilnehmer;
import kreyj.konfplan.persistence.Veranstaltung;
import kreyj.konfplan.persistence.VortragVerfuegbarkeit;
import kreyj.konfplan.presentation.DatabaseCleaner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static kreyj.konfplan.persistence.NutzerVerfuegbarkeitId.nvIdL;
import static kreyj.konfplan.persistence.RaumVerfuegbarkeitId.rvIdL;
import static kreyj.konfplan.persistence.VortragVerfuegbarkeitId.vvId;
import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
public class VerfuegbarkeitsTest extends DatabaseCleaner {
    private Long veranstaltung_id;
    private Long slot1_id, slot2_id;
    private Long tn_in_A_id, tn_in_B_id;
    private Long raum1_id, raum2_id;
    private Long referent_id;

    @BeforeEach
    @Transactional
    void setUp() {
        // 1. Create Base Entities
        Veranstaltung veranstaltung = new Veranstaltung();
        veranstaltung.setName("Verfügbarkeits-Test Event");
        veranstaltung.setBeginntAm(LocalDateTime.now());
        veranstaltung.setEndetAm(LocalDateTime.now().plusDays(1));
        veranstaltung.persist();
        veranstaltung_id = veranstaltung.getId();

        Slot slot1 = new Slot("Slot 1", veranstaltung.getBeginntAm().plusHours(1),
                veranstaltung.getBeginntAm().plusHours(2), veranstaltung);
        Slot slot2 = new Slot("Slot 2", veranstaltung.getBeginntAm().plusHours(3),
                veranstaltung.getBeginntAm().plusHours(4), veranstaltung);
        veranstaltung.addSlot(slot1);
        veranstaltung.addSlot(slot2);
        veranstaltung.persist();
        slot1_id = slot1.getId();
        slot2_id = slot2.getId();

        Raum raum1 = new Raum("Raum 1", 30);
        Raum raum2 = new Raum("Raum 2", 30);
        Gebaeude gebaeude = new Gebaeude("Testgebäude", "Ort", "Straße", "12345", Gebaeudetyp.SCHULE);
        gebaeude.addRaum(raum1);
        gebaeude.addRaum(raum2);
        gebaeude.persist();
        raum1_id = raum1.getId();
        raum2_id = raum2.getId();

        veranstaltung.addGebaeude(gebaeude);

        Teilnehmer tn_in_A = new Teilnehmer();
        tn_in_A.setEmail("tn_in_a@test.com");
        tn_in_A.addGruppe("GruppeA");
        tn_in_A.persist();
        tn_in_A_id = tn_in_A.getId();

        Teilnehmer tn_in_B = new Teilnehmer();
        tn_in_B.setEmail("tn_in_b@test.com");
        tn_in_B.addGruppe("GruppeB");
        tn_in_B.persist();
        tn_in_B_id = tn_in_B.getId();

        Referent referent = new Referent();
        referent.setEmail("referent@test.com");
        referent.persist();
        referent_id = referent.getId();

        // 3. Add all resources to the event to create initial availabilities
        veranstaltung.addNutzer(tn_in_A);
        veranstaltung.addNutzer(tn_in_B);
        veranstaltung.addNutzer(referent);
    }

    // --- Nutzer-Lebenszyklus Tests ---

    @Test
    @Transactional
    void testAddNutzerToVeranstaltung() {
        NutzerVerfuegbarkeit nvA = NutzerVerfuegbarkeit.findById(nvIdL(tn_in_A_id, veranstaltung_id));
        assertThat(nvA).isNotNull();
        assertThat(nvA.getVerfuegbareSlotIds()).containsExactlyInAnyOrder(slot1_id, slot2_id);

        NutzerVerfuegbarkeit nvB = NutzerVerfuegbarkeit.findById(nvIdL(tn_in_B_id, veranstaltung_id));
        assertThat(nvB).isNotNull();
        assertThat(nvB.getVerfuegbareSlotIds()).containsExactlyInAnyOrder(slot1_id, slot2_id);
    }

    @Test
    @Transactional
    void testRemoveNutzerFromVeranstaltung() {
        Veranstaltung veranstaltung = Veranstaltung.findById(veranstaltung_id);
        Teilnehmer tn_in_a = Teilnehmer.findById(tn_in_A_id);
        veranstaltung.removeNutzer(tn_in_a);
        NutzerVerfuegbarkeit nv = NutzerVerfuegbarkeit.findById(nvIdL(tn_in_A_id, veranstaltung_id));
        assertThat(nv).isNull();
    }

    // --- Slot-Lebenszyklus Tests ---

    @Test
    @Transactional
    void testAddSlotToVeranstaltung() {
        Veranstaltung veranstaltung = Veranstaltung.findById(veranstaltung_id);
        Slot slot3 = new Slot("Slot 3", veranstaltung.getBeginntAm().plusHours(5),
                veranstaltung.getBeginntAm().plusHours(6), veranstaltung);
        slot3.persist();

        veranstaltung.addSlot(slot3);
        veranstaltung.persist();

        veranstaltung = Veranstaltung.findById(veranstaltung_id);
        assertThat(veranstaltung.getSlots()).hasSize(3).contains(slot3);

        NutzerVerfuegbarkeit nvA = NutzerVerfuegbarkeit.findById(nvIdL(tn_in_A_id, veranstaltung_id));
        assertThat(nvA.getVerfuegbareSlotIds()).contains(slot3.getId());

        RaumVerfuegbarkeit rv1 = RaumVerfuegbarkeit.findById(rvIdL(raum1_id, veranstaltung_id));
        assertThat(rv1.getVerfuegbareSlotIds()).contains(slot3.getId());

        assertThat(VortragVerfuegbarkeit.count()).isZero();
    }

    @Test
    @Transactional
    void testRemoveSlotFromVeranstaltung() {
        Veranstaltung veranstaltung = Veranstaltung.findById(veranstaltung_id);
        Slot slot2 = Slot.findById(slot2_id);
        veranstaltung.removeSlot(slot2);
        veranstaltung.persist();

        NutzerVerfuegbarkeit nvA = NutzerVerfuegbarkeit.findById(new NutzerVerfuegbarkeitId(tn_in_A_id,
                veranstaltung.getId()));
        assertThat(nvA.getVerfuegbareSlotIds()).doesNotContain(slot2_id);

        RaumVerfuegbarkeit raumVerfuegbarkeit1 = RaumVerfuegbarkeit.findById(rvIdL(raum1_id, veranstaltung_id));
        assertThat(raumVerfuegbarkeit1.getVerfuegbareSlotIds()).doesNotContain(slot2_id);
    }

    // --- Pflichtvortrag-Lebenszyklus Tests ---

    @Test
    @Transactional
    void testCreatePflichtvortrag() {
        Veranstaltung veranstaltung = Veranstaltung.findById(veranstaltung_id);
        Referent referent = Referent.findById(referent_id);
        Raum raum1 = Raum.findById(raum1_id);
        Slot slot1 = Slot.findById(slot1_id);

        Pflichtvortrag pv = new Pflichtvortrag("PV", referent, veranstaltung, "GruppeA", raum1, slot1);
        pv.persist();
        pv.afterPersist();

        NutzerVerfuegbarkeit nvA = NutzerVerfuegbarkeit.findById(nvIdL(tn_in_A_id, veranstaltung_id));
        assertThat(nvA.getVerfuegbareSlotIds()).doesNotContain(slot1_id).contains(slot2_id);

        NutzerVerfuegbarkeit nvB = NutzerVerfuegbarkeit.findById(nvIdL(tn_in_B_id, veranstaltung_id));
        assertThat(nvB.getVerfuegbareSlotIds()).contains(slot1_id, slot2_id);

        RaumVerfuegbarkeit raumVerfuegbarkeit1 = RaumVerfuegbarkeit.findById(rvIdL(raum1_id, veranstaltung_id));
        assertThat(raumVerfuegbarkeit1.getVerfuegbareSlotIds()).doesNotContain(slot1_id).contains(slot2_id);

        RaumVerfuegbarkeit raumVerfuegbarkeit2 = RaumVerfuegbarkeit.findById(rvIdL(raum2_id, veranstaltung_id));
        assertThat(raumVerfuegbarkeit2.getVerfuegbareSlotIds()).contains(slot1_id, slot2_id);
    }

    @Test
    @Transactional
    void testDeletePflichtvortrag() {
        Veranstaltung veranstaltung = Veranstaltung.findById(veranstaltung_id);
        Referent referent = Referent.findById(referent_id);
        Raum raum1 = Raum.findById(raum1_id);
        Slot slot1 = Slot.findById(slot1_id);

        Pflichtvortrag pv = new Pflichtvortrag("PV", referent, veranstaltung, "GruppeA", raum1, slot1);
        pv.persist();
        pv.afterPersist();

        // Verify initial block
        NutzerVerfuegbarkeit nv = NutzerVerfuegbarkeit.findById(nvIdL(tn_in_A_id, veranstaltung_id));
        assertThat(nv.getVerfuegbareSlotIds())
                .describedAs("NutzerVerfuegbarkeit für Teilnehmer_in_A sollte Slot_1 nach Anlegen nicht mehr enthalten")
                .doesNotContain(slot1_id);

        RaumVerfuegbarkeit rv = RaumVerfuegbarkeit.findById(rvIdL(raum1_id, veranstaltung_id));
        assertThat(rv.getVerfuegbareSlotIds())
                .describedAs("RaumVerfuegbarkeit für Raum_1 sollte Slot_1 nach Anlegen nicht mehr enthalten")
                .doesNotContain(slot1_id);

        VortragVerfuegbarkeit vv = VortragVerfuegbarkeit.findById(vvId(pv, veranstaltung));
//        assertThat(vv.getVerfuegbareSlotIds())
//                .describedAs("VortragVerfügbarkeit für ")
//                .doesNotContain(slot1_id);

        pv.delete();

        // Verify availability is restored
        nv = NutzerVerfuegbarkeit.findById(nvIdL(tn_in_A_id, veranstaltung_id));
        assertThat(nv.getVerfuegbareSlotIds())
                .describedAs("NutzerVerfuegbarkeit für TN_in_A sollte Slot_1 nach Löschen wieder enthalten")
                .contains(slot1_id);

        rv = RaumVerfuegbarkeit.findById(rvIdL(raum1_id, veranstaltung_id));
        assertThat(rv.getVerfuegbareSlotIds())
                .describedAs("RaumVerfuegbarkeit für Raum_1 sollte Slot_1 nach Löschen wieder enthalten")
                .contains(slot1_id);

//        vv = VortragVerfuegbarkeit.findById(vvId(pv, veranstaltung));
//        assertThat(vv.getVerfuegbareSlotIds())
//                .describedAs("VortragVerfuegbarkeit für PV sollte Slot_1 nach Löschen wieder enthalten")
//                .contains(slot1_id);
    }

    @Test
    @Transactional
    void testChangePflichtvortragSlot() {
        Veranstaltung veranstaltung = Veranstaltung.findById(veranstaltung_id);
        Referent referent = Referent.findById(referent_id);
        Raum raum1 = Raum.findById(raum1_id);
        Slot slot1 = Slot.findById(slot1_id);

        Pflichtvortrag pv = new Pflichtvortrag("PV", referent, veranstaltung, "GruppeA", raum1, slot1);
        pv.persist();
        pv.afterPersist();

        Slot slot2 = Slot.findById(slot2_id);
        pv.updatePflichtslot(slot2);

        NutzerVerfuegbarkeit nvA = NutzerVerfuegbarkeit.findById(nvIdL(tn_in_A_id, veranstaltung_id));
        assertThat(nvA.getVerfuegbareSlotIds()).contains(slot1_id).doesNotContain(slot2_id);

        RaumVerfuegbarkeit raumVerfuegbarkeit1 = RaumVerfuegbarkeit.findById(rvIdL(raum1_id, veranstaltung_id));
        assertThat(raumVerfuegbarkeit1.getVerfuegbareSlotIds()).contains(slot1_id).doesNotContain(slot2_id);
    }

    @Test
    @Transactional
    void testChangePflichtvortragGruppe() {
        Veranstaltung veranstaltung = Veranstaltung.findById(veranstaltung_id);
        Referent referent = Referent.findById(referent_id);
        Raum raum1 = Raum.findById(raum1_id);
        Slot slot1 = Slot.findById(slot1_id);
        Pflichtvortrag pv = new Pflichtvortrag("PV", referent, veranstaltung, "GruppeA", raum1, slot1);
        pv.persist();
        pv.afterPersist();

        pv.updatePflichtgruppe("GruppeB");

        NutzerVerfuegbarkeit nvA = NutzerVerfuegbarkeit.findById(nvIdL(tn_in_A_id, veranstaltung_id));
        assertThat(nvA.getVerfuegbareSlotIds()).contains(slot1_id);

        NutzerVerfuegbarkeit nvB = NutzerVerfuegbarkeit.findById(nvIdL(tn_in_B_id, veranstaltung_id));
        assertThat(nvB.getVerfuegbareSlotIds()).doesNotContain(slot1_id);
    }

    @Test
    @Transactional
    void testChangePflichtvortragRaum() {
        Veranstaltung veranstaltung = Veranstaltung.findById(veranstaltung_id);
        Referent referent = Referent.findById(referent_id);
        Raum raum1 = Raum.findById(raum1_id);
        Slot slot1 = Slot.findById(slot1_id);
        Pflichtvortrag pv = new Pflichtvortrag("PV", referent, veranstaltung, "GruppeA", raum1, slot1);
        pv.persist();
        pv.afterPersist();

        Raum raum2 = Raum.findById(raum2_id);
        pv.updatePflichtraum(raum2);

        RaumVerfuegbarkeit raumVerfuegbarkeit1 = RaumVerfuegbarkeit.findById(rvIdL(raum1_id, veranstaltung_id));
        assertThat(raumVerfuegbarkeit1.getVerfuegbareSlotIds()).contains(slot1_id);

        RaumVerfuegbarkeit raumVerfuegbarkeit2 = RaumVerfuegbarkeit.findById(new RaumVerfuegbarkeitId(raum2.getId(), veranstaltung.getId()));
        assertThat(raumVerfuegbarkeit2.getVerfuegbareSlotIds()).doesNotContain(slot1_id);
    }
}