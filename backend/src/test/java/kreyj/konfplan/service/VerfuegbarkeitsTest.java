package kreyj.konfplan.service;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import kreyj.konfplan.application.service.AdminService;
import kreyj.konfplan.persistence.Gebaeude;
import kreyj.konfplan.persistence.Gebaeudetyp;
import kreyj.konfplan.persistence.Nutzer;
import kreyj.konfplan.persistence.NutzerVerfuegbarkeit;
import kreyj.konfplan.persistence.NutzerVerfuegbarkeitId;
import kreyj.konfplan.persistence.Pflichtvortrag;
import kreyj.konfplan.persistence.Planungsergebnis;
import kreyj.konfplan.persistence.Prioritaet;
import kreyj.konfplan.persistence.Raum;
import kreyj.konfplan.persistence.RaumVerfuegbarkeit;
import kreyj.konfplan.persistence.RaumVerfuegbarkeitId;
import kreyj.konfplan.persistence.Referent;
import kreyj.konfplan.persistence.Slot;
import kreyj.konfplan.persistence.Teilnehmer;
import kreyj.konfplan.persistence.Veranstaltung;
import kreyj.konfplan.persistence.Vortrag;
import kreyj.konfplan.persistence.Zuweisung;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static kreyj.konfplan.persistence.NutzerVerfuegbarkeitId.nvId;
import static kreyj.konfplan.persistence.RaumVerfuegbarkeitId.rvId;
import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
public class VerfuegbarkeitsTest {

    @Inject
    AdminService adminService;

    private Veranstaltung veranstaltung;
    private Slot slot1, slot2;
    private Teilnehmer teilnehmerA, teilnehmerB;
    private Raum raum1, raum2;
    private Referent referent;

    @BeforeEach
    @Transactional
    void setUp() {
        // 1. Full Cleanup
        Zuweisung.deleteAll();
        Prioritaet.deleteAll();
        NutzerVerfuegbarkeit.deleteAll();
        RaumVerfuegbarkeit.deleteAll();
        Vortrag.deleteAll();
        Planungsergebnis.deleteAll();
        Slot.deleteAll();
        Veranstaltung.deleteAll();
        Nutzer.deleteAll();
        Raum.deleteAll();
        Gebaeude.deleteAll();

        // 2. Create Base Entities
        veranstaltung = new Veranstaltung();
        veranstaltung.setName("Verfügbarkeits-Test Event");
        veranstaltung.setBeginntAm(LocalDateTime.now());
        veranstaltung.setEndetAm(LocalDateTime.now().plusDays(1));
        veranstaltung.persistAndFlush();

        slot1 = new Slot("Slot 1", veranstaltung.getBeginntAm().plusHours(1), veranstaltung.getBeginntAm().plusHours(2));
        slot2 = new Slot("Slot 2", veranstaltung.getBeginntAm().plusHours(3), veranstaltung.getBeginntAm().plusHours(4));
        veranstaltung.addSlot(slot1);
        veranstaltung.addSlot(slot2);

        raum1 = new Raum("Raum 1", 30);
        raum2 = new Raum("Raum 2", 30);
        Gebaeude gebaeude = new Gebaeude("Testgebäude", "Ort", "Straße", "12345", Gebaeudetyp.SCHULE);
        gebaeude.addRaum(raum1);
        gebaeude.addRaum(raum2);
        gebaeude.persistAndFlush();
        veranstaltung.addGebaeude(gebaeude);

        teilnehmerA = new Teilnehmer();
        teilnehmerA.setEmail("teilnehmerA@test.com");
        teilnehmerA.setGruppe("GruppeA");
        teilnehmerA.persistAndFlush();

        teilnehmerB = new Teilnehmer();
        teilnehmerB.setEmail("teilnehmerB@test.com");
        teilnehmerB.setGruppe("GruppeB");
        teilnehmerB.persistAndFlush();

        referent = new Referent();
        referent.setEmail("referent@test.com");
        referent.persistAndFlush();

        // 3. Add all resources to the event to create initial availabilities
        veranstaltung.addNutzer(teilnehmerA);
        veranstaltung.addNutzer(teilnehmerB);
        veranstaltung.addNutzer(referent);
        veranstaltung.persistAndFlush();
    }

    // --- Nutzer-Lebenszyklus Tests ---

    @Test
    @Transactional
    void testAddNutzerToVeranstaltung() {
        NutzerVerfuegbarkeit nvA = NutzerVerfuegbarkeit.findById(nvId(teilnehmerA, veranstaltung));
        assertThat(nvA).isNotNull();
        assertThat(nvA.getVerfuegbareSlotIds()).containsExactlyInAnyOrder(slot1.getId(), slot2.getId());

        NutzerVerfuegbarkeit nvB = NutzerVerfuegbarkeit.findById(nvId(teilnehmerB, veranstaltung));
        assertThat(nvB).isNotNull();
        assertThat(nvB.getVerfuegbareSlotIds()).containsExactlyInAnyOrder(slot1.getId(), slot2.getId());
    }

    @Test
    @Transactional
    void testRemoveNutzerFromVeranstaltung() {
        veranstaltung.removeNutzer(teilnehmerA);
        NutzerVerfuegbarkeit nv = NutzerVerfuegbarkeit.findById(nvId(teilnehmerA, veranstaltung));
        assertThat(nv).isNull();
    }

    // --- Slot-Lebenszyklus Tests ---

    @Test
    @Transactional
    void testAddSlotToVeranstaltung() {
        Slot slot3 = new Slot("Slot 3", veranstaltung.getBeginntAm().plusHours(5), veranstaltung.getBeginntAm().plusHours(6));
        veranstaltung.addSlot(slot3);
        veranstaltung.persist();

        NutzerVerfuegbarkeit nvA = NutzerVerfuegbarkeit.findById(nvId(teilnehmerA, veranstaltung));
        assertThat(nvA.getVerfuegbareSlotIds()).contains(slot3.getId());

        RaumVerfuegbarkeit raumVerfuegbarkeit1 = RaumVerfuegbarkeit.findById(rvId(raum1, veranstaltung));
        assertThat(raumVerfuegbarkeit1.getVerfuegbareSlotIds()).contains(slot3.getId());
    }

    @Test
    @Transactional
    void testRemoveSlotFromVeranstaltung() {
        veranstaltung.removeSlot(slot2);
        veranstaltung.persist();

        NutzerVerfuegbarkeit nvA = NutzerVerfuegbarkeit.findById(new NutzerVerfuegbarkeitId(teilnehmerA.getId(), veranstaltung.getId()));
        assertThat(nvA.getVerfuegbareSlotIds()).doesNotContain(slot2.getId());

        RaumVerfuegbarkeit raumVerfuegbarkeit1 = RaumVerfuegbarkeit.findById(rvId(raum1, veranstaltung));
        assertThat(raumVerfuegbarkeit1.getVerfuegbareSlotIds()).doesNotContain(slot2.getId());
    }

    // --- Pflichtvortrag-Lebenszyklus Tests ---

    @Test
    @Transactional
    void testCreatePflichtvortrag() {
        Pflichtvortrag pv = new Pflichtvortrag("PV", referent, veranstaltung, "GruppeA", raum1, slot1);
        pv.persist();

        NutzerVerfuegbarkeit nvA = NutzerVerfuegbarkeit.findById(nvId(teilnehmerA, veranstaltung));
        assertThat(nvA.getVerfuegbareSlotIds()).doesNotContain(slot1.getId()).contains(slot2.getId());

        NutzerVerfuegbarkeit nvB = NutzerVerfuegbarkeit.findById(nvId(teilnehmerB, veranstaltung));
        assertThat(nvB.getVerfuegbareSlotIds()).contains(slot1.getId(), slot2.getId());

        RaumVerfuegbarkeit raumVerfuegbarkeit1 = RaumVerfuegbarkeit.findById(rvId(raum1, veranstaltung));
        assertThat(raumVerfuegbarkeit1.getVerfuegbareSlotIds()).doesNotContain(slot1.getId()).contains(slot2.getId());

        RaumVerfuegbarkeit raumVerfuegbarkeit2 = RaumVerfuegbarkeit.findById(new RaumVerfuegbarkeitId(raum2.getId(), veranstaltung.getId()));
        assertThat(raumVerfuegbarkeit2.getVerfuegbareSlotIds()).contains(slot1.getId(), slot2.getId());
    }

    @Test
    @Transactional
    void testDeletePflichtvortrag() {
        Pflichtvortrag pv = new Pflichtvortrag("PV", referent, veranstaltung, "GruppeA", raum1, slot1);
        pv.persist();

        // Verify initial block
        assertThat(NutzerVerfuegbarkeit.<NutzerVerfuegbarkeit>findById(nvId(teilnehmerA, veranstaltung)).getVerfuegbareSlotIds()).doesNotContain(slot1.getId());
        assertThat(RaumVerfuegbarkeit.<RaumVerfuegbarkeit>findById(rvId(raum1, veranstaltung)).getVerfuegbareSlotIds()).doesNotContain(slot1.getId());

        pv.delete();

        // Verify availability is restored
        assertThat(NutzerVerfuegbarkeit.<NutzerVerfuegbarkeit>findById(nvId(teilnehmerA, veranstaltung)).getVerfuegbareSlotIds()).contains(slot1.getId());
        assertThat(RaumVerfuegbarkeit.<RaumVerfuegbarkeit>findById(rvId(raum1, veranstaltung)).getVerfuegbareSlotIds()).contains(slot1.getId());
    }

    @Test
    @Transactional
    void testChangePflichtvortragSlot() {
        Pflichtvortrag pv = new Pflichtvortrag("PV", referent, veranstaltung, "GruppeA", raum1, slot1);
        pv.persist();

        pv.setPflichtslot(slot2);

        NutzerVerfuegbarkeit nvA = NutzerVerfuegbarkeit.findById(nvId(teilnehmerA, veranstaltung));
        assertThat(nvA.getVerfuegbareSlotIds()).contains(slot1.getId()).doesNotContain(slot2.getId());

        RaumVerfuegbarkeit raumVerfuegbarkeit1 = RaumVerfuegbarkeit.findById(rvId(raum1, veranstaltung));
        assertThat(raumVerfuegbarkeit1.getVerfuegbareSlotIds()).contains(slot1.getId()).doesNotContain(slot2.getId());
    }

    @Test
    @Transactional
    void testChangePflichtvortragGruppe() {
        Pflichtvortrag pv = new Pflichtvortrag("PV", referent, veranstaltung, "GruppeA", raum1, slot1);
        pv.persist();

        pv.setPflichtgruppe("GruppeB");

        NutzerVerfuegbarkeit nvA = NutzerVerfuegbarkeit.findById(nvId(teilnehmerA, veranstaltung));
        assertThat(nvA.getVerfuegbareSlotIds()).contains(slot1.getId());

        NutzerVerfuegbarkeit nvB = NutzerVerfuegbarkeit.findById(nvId(teilnehmerB, veranstaltung));
        assertThat(nvB.getVerfuegbareSlotIds()).doesNotContain(slot1.getId());
    }

    @Test
    @Transactional
    void testChangePflichtvortragRaum() {
        Pflichtvortrag pv = new Pflichtvortrag("PV", referent, veranstaltung, "GruppeA", raum1, slot1);
        pv.persist();

        pv.setPflichtraum(raum2);

        RaumVerfuegbarkeit raumVerfuegbarkeit1 = RaumVerfuegbarkeit.findById(rvId(raum1, veranstaltung));
        assertThat(raumVerfuegbarkeit1.getVerfuegbareSlotIds()).contains(slot1.getId());

        RaumVerfuegbarkeit raumVerfuegbarkeit2 = RaumVerfuegbarkeit.findById(new RaumVerfuegbarkeitId(raum2.getId(), veranstaltung.getId()));
        assertThat(raumVerfuegbarkeit2.getVerfuegbareSlotIds()).doesNotContain(slot1.getId());
    }
}