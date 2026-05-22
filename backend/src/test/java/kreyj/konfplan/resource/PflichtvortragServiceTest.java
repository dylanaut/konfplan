package kreyj.konfplan.resource;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.h2.H2DatabaseTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import kreyj.konfplan.dto.VortragDto;
import kreyj.konfplan.persistence.Admin;
import kreyj.konfplan.persistence.EventSlot;
import kreyj.konfplan.persistence.Gebaeude;
import kreyj.konfplan.persistence.Gebaeudetyp;
import kreyj.konfplan.persistence.Nutzer;
import kreyj.konfplan.persistence.Pflichtvortrag;
import kreyj.konfplan.persistence.Raum;
import kreyj.konfplan.persistence.RaumBelegbarkeit;
import kreyj.konfplan.persistence.Referent;
import kreyj.konfplan.persistence.Teilnehmer;
import kreyj.konfplan.persistence.Veranstaltung;
import kreyj.konfplan.persistence.Verfuegbarkeit;
import kreyj.konfplan.persistence.Vortrag;
import kreyj.konfplan.persistence.Wahlvortrag;
import kreyj.konfplan.service.AdminService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@QuarkusTest
@TestSecurity(user = "admin@example.com", roles = "ADMIN")
@QuarkusTestResource(H2DatabaseTestResource.class)
public class PflichtvortragServiceTest {

    @Inject
    AdminService adminService;

    Veranstaltung veranstaltung;
    Gebaeude gebaeude;
    Raum raum1, raum2;
    EventSlot slot1, slot2;
    Referent referent;
    Teilnehmer teilnehmer1, teilnehmer2, teilnehmer3;

    @BeforeEach
    @Transactional
        // Keep @Transactional for setup to ensure data is created and then rolled back
    void setup() {
        // Clear all entities before each test
        Verfuegbarkeit.deleteAll();
        RaumBelegbarkeit.deleteAll();
        Pflichtvortrag.deleteAll();
        Wahlvortrag.deleteAll();
        Vortrag.deleteAll();
        Teilnehmer.deleteAll();
        Referent.deleteAll();
        EventSlot.deleteAll();
        Raum.deleteAll();
        Gebaeude.deleteAll();
        Veranstaltung.deleteAll();
        Nutzer.deleteAll();

        // Setup Admin for @TestSecurity
        Admin admin = new Admin();
        admin.setEmail("admin@example.com");
        admin.setPasswordHash("hash");
        admin.persist();

        gebaeude = new Gebaeude();
        gebaeude.setName("Hauptgebäude");
        gebaeude.setTyp(Gebaeudetyp.SCHULE);
        gebaeude.setPostleitzahl("12345");
        gebaeude.setOrt("Testort");
        gebaeude.setStrasse("Teststraße");
        gebaeude.persist();

        veranstaltung = new Veranstaltung();
        veranstaltung.setName("Test Event");
        veranstaltung.setBeginntAm(LocalDateTime.of(2024, 1, 1, 9, 0));
        veranstaltung.setEndetAm(LocalDateTime.of(2024, 1, 1, 17, 0));
        veranstaltung.addGebaeude(gebaeude);
        veranstaltung.persist();


        raum1 = new Raum();
        raum1.setName("Raum 1");
        raum1.setKapazitaet(2);
        raum1.persist();
        gebaeude.addRaum(raum1);

        raum2 = new Raum();
        raum2.setName("Raum 2");
        raum2.setKapazitaet(10);
        raum2.persist();
        gebaeude.addRaum(raum2);

        slot1 = new EventSlot();
        slot1.setDescription("Slot 1");
        slot1.setStartTime(LocalDateTime.of(2024, 1, 1, 9, 0));
        slot1.setEndTime(LocalDateTime.of(2024, 1, 1, 10, 0));
        slot1.persist();
        veranstaltung.addSlot(slot1);

        slot2 = new EventSlot();
        slot2.setDescription("Slot 2");
        slot2.setStartTime(LocalDateTime.of(2024, 1, 1, 10, 0));
        slot2.setEndTime(LocalDateTime.of(2024, 1, 1, 11, 0));
        slot2.persist();
        veranstaltung.addSlot(slot1);

        referent = new Referent();
        referent.setEmail("ref@example.com");
        referent.setFirstName("Ref");
        referent.setLastName("Erent");
        referent.setPasswordHash("hash");
        referent.addVeranstaltung(veranstaltung);
        referent.persist();

        teilnehmer1 = new Teilnehmer();
        teilnehmer1.setEmail("tn1@example.com");
        teilnehmer1.setFirstName("TN1");
        teilnehmer1.setLastName("GruppeA");
        teilnehmer1.setGruppe("Gruppe A");
        teilnehmer1.setActive(true);
        teilnehmer1.addVeranstaltung(veranstaltung);
        teilnehmer1.persist();

        teilnehmer2 = new Teilnehmer();
        teilnehmer2.setEmail("tn2@example.com");
        teilnehmer2.setFirstName("TN2");
        teilnehmer2.setLastName("GruppeA");
        teilnehmer2.setGruppe("Gruppe A");
        teilnehmer2.setActive(true);
        teilnehmer2.addVeranstaltung(veranstaltung);
        teilnehmer2.persist();

        teilnehmer3 = new Teilnehmer();
        teilnehmer3.setEmail("tn3@example.com");
        teilnehmer3.setFirstName("TN3");
        teilnehmer3.setLastName("GruppeB");
        teilnehmer3.setGruppe("Gruppe B");
        teilnehmer3.setActive(true);
        teilnehmer3.addVeranstaltung(veranstaltung);
        teilnehmer3.persist();
    }

    @Test
    @Transactional
    void testCreatePflichtvortragSuccess() {
        // Raum 2 hat Kapazität 10, Gruppe A hat 2 TN
        Pflichtvortrag pv = new Pflichtvortrag("PV Test", referent, "Gruppe A", raum2, slot1);
        Pflichtvortrag createdPv = (Pflichtvortrag) adminService.createVortrag(pv, veranstaltung.getId());

        assertNotNull(createdPv.getId());
        assertEquals("PV Test", createdPv.getTitel());
        assertThat(isTeilnehmerAvailable(teilnehmer1, slot1)).isFalse();
        assertThat(isTeilnehmerAvailable(teilnehmer2, slot1)).isFalse();
        assertThat(isTeilnehmerAvailable(teilnehmer3, slot1)).isTrue(); // TN3 not in Gruppe A
        assertThat(isRaumAvailable(raum2, slot1)).isFalse();
        assertThat(isRaumAvailable(raum1, slot1)).isTrue(); // Raum 1 not used
    }

    @Test
    void testCreatePflichtvortragRaumBelegtFails() {
        // Manually block raum2, slot1 in a committed transaction
        QuarkusTransaction.requiringNew().run(() -> {
            RaumBelegbarkeit rb = new RaumBelegbarkeit(raum2, slot1, true);

            rb.persist();
        });

        Pflichtvortrag pv = new Pflichtvortrag();
        pv.setTitel("PV Test");
        pv.setReferent(referent);
        pv.setPflichtgruppe("Gruppe A");
        pv.setPflichtraum(raum2);
        pv.setPflichtslot(slot1);

        assertThrows(IllegalArgumentException.class, () -> adminService.createVortrag(pv, veranstaltung.getId()));
        final long[] pvCount = {0L};
        QuarkusTransaction.requiringNew().run(() -> pvCount[0] = Pflichtvortrag.count());
        assertEquals(0L, pvCount[0]); // No PV created
    }

    @Test
    void testCreatePflichtvortragTeilnehmerNichtVerfuegbarFails() {
        // Manually make teilnehmer1 unavailable for slot1 in a committed transaction
        QuarkusTransaction.requiringNew().run(() -> {
            new Verfuegbarkeit(teilnehmer1, slot1, false).persist();
        });

        Pflichtvortrag pv = new Pflichtvortrag();
        pv.setTitel("PV Test");
        pv.setReferent(referent);
        pv.setPflichtgruppe("Gruppe A");
        pv.setPflichtraum(raum2);
        pv.setPflichtslot(slot1);

        assertThrows(IllegalArgumentException.class, () -> adminService.createVortrag(pv, veranstaltung.getId()));
        final long[] pvCount = {0L};
        QuarkusTransaction.requiringNew().run(() -> pvCount[0] = Pflichtvortrag.count());
        assertThat(0L).isEqualTo(pvCount[0]); // No PV created
    }

    @Test
    void testCreatePflichtvortragRaumKapazitaetFails() {
        Pflichtvortrag pv = new Pflichtvortrag();
        pv.setTitel("PV Test");
        pv.setReferent(referent);
        pv.setPflichtgruppe("Gruppe A"); // 2 Teilnehmer
        pv.setPflichtraum(raum1); // Kapazität 2 (initial)
        QuarkusTransaction.requiringNew().run(() -> { // Update raum1 in a committed transaction
            // Retrieve the Raum persistence by its ID within this new transaction
            Raum r = Raum.findById(raum1.getId());
            r.setKapazitaet(1);
            pv.setPflichtraum(r);
        });
        pv.setPflichtslot(slot1);

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () -> {
            adminService.createVortrag(pv, veranstaltung.getId());
        });
        assertThat(thrown.getMessage().contains("Raumkapazität von 'Raum 1' reicht für die Gruppe 'Gruppe A' nicht aus.")).isTrue();
        final long[] pvCount = {0L};
        QuarkusTransaction.requiringNew().run(() -> pvCount[0] = Pflichtvortrag.count());
        assertEquals(0, pvCount[0]); // No PV created
    }

    @Test
    void testCreatePflichtvortragFailsIfRaumAlreadyOccupiedByAnotherPflichtvortrag() {
        // Create first PV
        Pflichtvortrag pv1 = new Pflichtvortrag();
        pv1.setTitel("PV1");
        pv1.setReferent(referent);
        pv1.setPflichtgruppe("Gruppe A");
        pv1.setPflichtraum(raum2);
        pv1.setPflichtslot(slot1);
        adminService.createVortrag(pv1, veranstaltung.getId());

        // Attempt to create a second PV using the same room and slot
        Pflichtvortrag pv2 = new Pflichtvortrag();
        pv2.setTitel("PV2");
        pv2.setReferent(referent);
        pv2.setPflichtgruppe("Gruppe B"); // Different group
        pv2.setPflichtraum(raum2); // Same room
        pv2.setPflichtslot(slot1); // Same slot

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () -> {
            adminService.createVortrag(pv2, veranstaltung.getId());
        });
        assertThat(thrown.getMessage().contains("Raum 'Raum 2' ist im Slot 'Slot 1' bereits belegt.")).isTrue();
        final long[] pvCount = {0L};
        QuarkusTransaction.requiringNew().run(() -> pvCount[0] = Pflichtvortrag.count());
        assertEquals(1, pvCount[0]); // Only PV1 created
    }

    @Test
    void testCreatePflichtvortragFailsIfGruppeAlreadyOccupiedByAnotherPflichtvortrag() {
        // Create first PV
        Pflichtvortrag pv1 = new Pflichtvortrag();
        pv1.setTitel("PV1");
        pv1.setReferent(referent);
        pv1.setPflichtgruppe("Gruppe A");
        pv1.setPflichtraum(raum2);
        pv1.setPflichtslot(slot1);
        adminService.createVortrag(pv1, veranstaltung.getId());

        // Attempt to create a second PV using the same group and slot
        Pflichtvortrag pv2 = new Pflichtvortrag();
        pv2.setTitel("PV2");
        pv2.setReferent(referent);
        pv2.setPflichtgruppe("Gruppe A"); // Same group
        pv2.setPflichtraum(raum1); // Different room
        pv2.setPflichtslot(slot1); // Same slot

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () -> {
            adminService.createVortrag(pv2, veranstaltung.getId());
        });
        assertThat(thrown.getMessage().contains("Nicht alle Teilnehmer der Gruppe 'Gruppe A' sind im Slot 'Slot 1' verfügbar.")).isTrue();
        final long[] pvCount = {0L};
        QuarkusTransaction.requiringNew().run(() -> pvCount[0] = Pflichtvortrag.count());
        assertEquals(1, pvCount[0]); // Only PV1 created
    }

    @Test
    void testUpdatePflichtvortragChangeSlotSuccess() {
        // Create initial PV
        Pflichtvortrag pv = new Pflichtvortrag();
        pv.setTitel("PV Initial");
        pv.setReferent(referent);
        pv.setPflichtgruppe("Gruppe A");
        pv.setPflichtraum(raum2); // Raum 2 hat Kapazität 10, Gruppe A hat 2 TN
        pv.setPflichtslot(slot1);
        Pflichtvortrag initialPv = (Pflichtvortrag) adminService.createVortrag(pv, veranstaltung.getId());

        // Verify initial state

        assertThat(isTeilnehmerAvailable(teilnehmer1, slot1)).isFalse();
        assertThat(isRaumAvailable(raum2, slot1)).isFalse();
        assertThat(isTeilnehmerAvailable(teilnehmer1, slot2)).isTrue();
        assertThat(isRaumAvailable(raum2, slot2)).isTrue();

        // Update PV to change slot to slot2
        VortragDto updatedPvDto = new VortragDto();
        updatedPvDto.istPflicht = true;
        updatedPvDto.titel = "PV Updated Slot";
        updatedPvDto.referentId = referent.getId();
        updatedPvDto.pflichtgruppe = "Gruppe A";
        updatedPvDto.pflichtRaumId = raum2.getId();
        updatedPvDto.pflichtSlotId = slot2.getId();
        updatedPvDto.version = 0L;

        adminService.updateVortrag(veranstaltung.getId(), initialPv.getId(), updatedPvDto);

        // Verify new state
        assertThat(isTeilnehmerAvailable(teilnehmer1, slot1)).isTrue(); // Old slot freed
        assertThat(isRaumAvailable(raum2, slot1)).isTrue(); // Old room-slot freed
        assertThat(isTeilnehmerAvailable(teilnehmer1, slot2)).isFalse(); // New slot occupied
        assertThat(isRaumAvailable(raum2, slot2)).isFalse(); // New room-slot occupied
    }

    @Test
    void testUpdatePflichtvortragChangeSlotFailsIfNewSlotNotAvailable() {
        // Create initial PV
        Pflichtvortrag pv = new Pflichtvortrag();
        pv.setTitel("PV Initial");
        pv.setReferent(referent);
        pv.setPflichtgruppe("Gruppe A");
        pv.setPflichtraum(raum2);
        pv.setPflichtslot(slot1);
        Pflichtvortrag initialPv = (Pflichtvortrag) adminService.createVortrag(pv, veranstaltung.getId());

        // Manually block slot2 for teilnehmer1 in a committed transaction
        QuarkusTransaction.requiringNew().run(() -> {
            new Verfuegbarkeit(teilnehmer1, slot2, false).persist();
        });

        // Attempt to update PV to change slot to slot2
        VortragDto updatedPv = new VortragDto();
        updatedPv.istPflicht = true;
        updatedPv.titel = "PV Updated Slot";
        updatedPv.referentId = referent.getId();
        updatedPv.pflichtgruppe = "Gruppe A";
        updatedPv.pflichtRaumId = raum2.getId();
        updatedPv.pflichtSlotId = slot2.getId();
        updatedPv.version = 0L;

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () -> {
            adminService.updateVortrag(veranstaltung.getId(), initialPv.getId(), updatedPv);
        });
        assertThat(thrown.getMessage().contains("Nicht alle Teilnehmer der Gruppe 'Gruppe A' sind im neuen Slot 'Slot 2' verfügbar.")).isTrue();

        // Verify state remains unchanged
        assertThat(isTeilnehmerAvailable(teilnehmer1, slot1)).isFalse();
        assertThat(isRaumAvailable(raum2, slot1)).isFalse();
        assertThat(isTeilnehmerAvailable(teilnehmer1, slot2)).isFalse(); // Still unavailable due to manual block
        assertThat(isRaumAvailable(raum2, slot2)).isTrue(); // Room was not blocked by PV update
    }

    @Test
    void testUpdatePflichtvortragChangeRaumSuccess() {
        // Create initial PV
        Pflichtvortrag pv = new Pflichtvortrag();
        pv.setTitel("PV Initial");
        pv.setReferent(referent);
        pv.setPflichtgruppe("Gruppe A");
        pv.setPflichtraum(raum1); // Kapazität 2
        pv.setPflichtslot(slot1);
        Pflichtvortrag initialPv = (Pflichtvortrag) adminService.createVortrag(pv, veranstaltung.getId());

        // Verify initial state
        assertThat(isRaumAvailable(raum1, slot1)).isFalse();
        assertThat(isRaumAvailable(raum2, slot1)).isTrue();

        // Update PV to change room to raum2
        VortragDto updatedPv = new VortragDto();
        updatedPv.istPflicht = true;
        updatedPv.titel = "PV Updated Raum";
        updatedPv.referentId = referent.getId();

        updatedPv.pflichtgruppe = "Gruppe A";
        updatedPv.pflichtRaumId = raum2.getId();
        // Kapazität 10
        updatedPv.pflichtSlotId = slot1.getId();
        updatedPv.version = 0L;

        adminService.updateVortrag(veranstaltung.getId(), initialPv.getId(), updatedPv);

        // Verify new state
        assertThat(isRaumAvailable(raum1, slot1)).isTrue(); // Old room freed
        assertThat(isRaumAvailable(raum2, slot1)).isFalse(); // New room occupied
        assertThat(isTeilnehmerAvailable(teilnehmer1, slot1)).isFalse(); // Teilnehmer availability unchanged
    }

    @Test
    @Transactional
    void testUpdatePflichtvortragChangeRaumFailsIfNewRaumBelegt() {
        // Create initial PV
        Pflichtvortrag pv = new Pflichtvortrag();
        pv.setTitel("PV Initial");
        pv.setReferent(referent);
        pv.setPflichtgruppe("Gruppe A");
        pv.setPflichtraum(raum1);
        pv.setPflichtslot(slot1);
        Pflichtvortrag initialPv = (Pflichtvortrag) adminService.createVortrag(pv, veranstaltung.getId());

        // Manually block raum2, slot1 in a committed transaction
        QuarkusTransaction.requiringNew().run(() -> {
            new RaumBelegbarkeit(raum2, slot1, true).persist();
        });

        // Attempt to update PV to change room to raum2
        VortragDto updatedPv = new VortragDto();
        updatedPv.istPflicht = true;
        updatedPv.titel = "PV Updated Raum";
        updatedPv.referentId = referent.getId();

        updatedPv.pflichtgruppe = "Gruppe A";
        updatedPv.pflichtRaumId = raum2.getId();

        updatedPv.pflichtSlotId = slot1.getId();
        updatedPv.version = 0L;

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () -> {
            adminService.updateVortrag(veranstaltung.getId(), initialPv.getId(), updatedPv);
        });
        assertThat(thrown.getMessage().contains("Neuer Raum 'Raum 2' ist im Slot 'Slot 1' bereits belegt.")).isTrue();

        // Verify state remains unchanged
        assertThat(isRaumAvailable(raum1, slot1)).isFalse(); // Raum1 still occupied by PV
        assertThat(isRaumAvailable(raum2, slot1)).isFalse(); // Raum2 still occupied by manual block
    }

    @Test
    void testUpdatePflichtvortragChangeRaumFailsIfNewRaumOccupiedByAnotherPflichtvortrag() {
        // Create PV1
        Pflichtvortrag pv1 = new Pflichtvortrag();
        pv1.setTitel("PV1");
        pv1.setReferent(referent);
        pv1.setPflichtgruppe("Gruppe A");
        pv1.setPflichtraum(raum1);
        pv1.setPflichtslot(slot1);
        Pflichtvortrag createdPv1 = (Pflichtvortrag) adminService.createVortrag(pv1, veranstaltung.getId());

        // Create PV2 that occupies raum2, slot1
        Pflichtvortrag pv2 = new Pflichtvortrag();
        pv2.setTitel("PV2");
        pv2.setReferent(referent);
        pv2.setPflichtgruppe("Gruppe B");
        pv2.setPflichtraum(raum2);
        pv2.setPflichtslot(slot1);
        adminService.createVortrag(pv2, veranstaltung.getId());

        // Attempt to update PV1 to use raum2 (which is occupied by PV2)
        VortragDto updatedPv1 = new VortragDto();
        updatedPv1.istPflicht = true;
        updatedPv1.titel = "PV1 Updated Raum";
        updatedPv1.referentId = referent.getId();
        updatedPv1.pflichtgruppe = "Gruppe A";
        updatedPv1.pflichtRaumId = raum2.getId(); // Try to change to raum2
        updatedPv1.pflichtSlotId = slot1.getId();
        updatedPv1.version = 0L;

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () -> {
            adminService.updateVortrag(veranstaltung.getId(), createdPv1.getId(), updatedPv1);
        });
        assertThat(thrown.getMessage().contains("Neuer Raum 'Raum 2' ist im Slot 'Slot 1' bereits belegt.")).isTrue();

        // Verify state remains unchanged for PV1
        assertThat(isRaumAvailable(raum1, slot1)).isFalse(); // raum1 still occupied by PV1
        assertThat(isRaumAvailable(raum2, slot1)).isFalse(); // raum2 still occupied by PV2
    }

    @Test
    void testUpdatePflichtvortragChangeGruppeSuccess() {
        // Create initial PV for Gruppe A
        Pflichtvortrag pv = new Pflichtvortrag();
        pv.setTitel("PV Initial");
        pv.setReferent(referent);
        pv.setPflichtgruppe("Gruppe A");
        pv.setPflichtraum(raum2);
        pv.setPflichtslot(slot1);
        Pflichtvortrag initialPv = (Pflichtvortrag) adminService.createVortrag(pv, veranstaltung.getId());

        // Verify initial state
        assertThat(isTeilnehmerAvailable(teilnehmer1, slot1)).isFalse(); // Gruppe A
        assertThat(isTeilnehmerAvailable(teilnehmer2, slot1)).isFalse(); // Gruppe A
        assertThat(isTeilnehmerAvailable(teilnehmer3, slot1)).isTrue(); // Gruppe B

        // Update PV to change group to Gruppe B
        VortragDto updatedPv = new VortragDto();
        updatedPv.istPflicht = true;
        updatedPv.titel = "PV Updated Gruppe";
        updatedPv.referentId = referent.getId();
        updatedPv.pflichtgruppe = "Gruppe B";
        updatedPv.pflichtRaumId = raum2.getId();
        updatedPv.pflichtSlotId = slot1.getId();
        updatedPv.version = 0L;

        adminService.updateVortrag(veranstaltung.getId(), initialPv.getId(), updatedPv);

        // Verify new state
        assertThat(isTeilnehmerAvailable(teilnehmer1, slot1)).isTrue(); // Old group freed
        assertThat(isTeilnehmerAvailable(teilnehmer2, slot1)).isTrue(); // Old group freed
        assertThat(isTeilnehmerAvailable(teilnehmer3, slot1)).isFalse(); // New group occupied
        assertThat(isRaumAvailable(raum2, slot1)).isFalse(); // Room availability unchanged
    }

    @Test
    void testUpdatePflichtvortragChangeGruppeFailsIfNewGruppeNotAvailable() {
        // Create initial PV for Gruppe A
        Pflichtvortrag pv = new Pflichtvortrag();
        pv.setTitel("PV Initial");
        pv.setReferent(referent);
        pv.setPflichtgruppe("Gruppe A");
        pv.setPflichtraum(raum2);
        pv.setPflichtslot(slot1);
        Pflichtvortrag initialPv = (Pflichtvortrag) adminService.createVortrag(pv, veranstaltung.getId());

        // Manually make teilnehmer3 (Gruppe B) unavailable for slot1 in a committed transaction
        QuarkusTransaction.requiringNew().run(() -> {
            new Verfuegbarkeit(teilnehmer3, slot1, false).persist();
        });

        // Attempt to update PV to change group to Gruppe B
        VortragDto updatedPv = new VortragDto();
        updatedPv.istPflicht = true;
        updatedPv.titel = "PV Updated Gruppe";
        updatedPv.referentId = referent.getId();
        updatedPv.pflichtgruppe = "Gruppe B";
        updatedPv.pflichtRaumId = raum2.getId();
        updatedPv.pflichtSlotId = slot1.getId();
        updatedPv.version = 0L;

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () -> {
            adminService.updateVortrag(veranstaltung.getId(), initialPv.getId(), updatedPv);
        });
        assertThat(thrown.getMessage().contains("Nicht alle Teilnehmer der neuen Gruppe 'Gruppe B' sind im Slot 'Slot 1' verfügbar.")).isTrue();

        // Verify state remains unchanged
        assertThat(isTeilnehmerAvailable(teilnehmer1, slot1)).isFalse(); // Gruppe A still occupied by PV
        assertThat(isTeilnehmerAvailable(teilnehmer3, slot1)).isFalse(); // Gruppe B still unavailable by manual block
    }

    @Test
    void testUpdatePflichtvortragChangeGruppeFailsIfNewGruppeOccupiedByAnotherPflichtvortrag() {
        // Create PV1 for Gruppe A, Slot 1
        Pflichtvortrag pv1 = new Pflichtvortrag();
        pv1.setTitel("PV1");
        pv1.setReferent(referent);
        pv1.setPflichtgruppe("Gruppe A");
        pv1.setPflichtraum(raum2);
        pv1.setPflichtslot(slot1);
        Pflichtvortrag createdPv1 = (Pflichtvortrag) adminService.createVortrag(pv1, veranstaltung.getId());

        // Create PV2 for Gruppe B, Slot 1
        Pflichtvortrag pv2 = new Pflichtvortrag();
        pv2.setTitel("PV2");
        pv2.setReferent(referent);
        pv2.setPflichtgruppe("Gruppe B");
        pv2.setPflichtraum(raum1);
        pv2.setPflichtslot(slot1);
        adminService.createVortrag(pv2, veranstaltung.getId());

        // Attempt to update PV1 to use Gruppe B (which is occupied by PV2)
        VortragDto updatedPv1 = new VortragDto();
        updatedPv1.istPflicht = true;
        updatedPv1.titel = "PV1 Updated Gruppe";
        updatedPv1.referentId = referent.getId();
        updatedPv1.pflichtgruppe = "Gruppe B"; // Try to change to Gruppe B
        updatedPv1.pflichtRaumId = raum2.getId();
        updatedPv1.pflichtSlotId = slot1.getId();
        updatedPv1.version = 0L;

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () -> {
            adminService.updateVortrag(veranstaltung.getId(), createdPv1.getId(), updatedPv1);
        });
        assertThat(thrown.getMessage().contains("Nicht alle Teilnehmer der neuen Gruppe 'Gruppe B' sind im Slot 'Slot 1' verfügbar.")).isTrue();

        // Verify state remains unchanged for PV1
        assertThat(isTeilnehmerAvailable(teilnehmer1, slot1)).isFalse(); // Gruppe A still occupied by PV1
        assertThat(isTeilnehmerAvailable(teilnehmer3, slot1)).isFalse(); // Gruppe B still occupied by PV2
    }

    @Test
    void testDeletePflichtvortragSuccess() {
        // Create initial PV
        Pflichtvortrag pv = new Pflichtvortrag();
        pv.setTitel("PV Test");
        pv.setReferent(referent);
        pv.setPflichtgruppe("Gruppe A");
        pv.setPflichtraum(raum2);
        pv.setPflichtslot(slot1);
        Pflichtvortrag createdPv = (Pflichtvortrag) adminService.createVortrag(pv, veranstaltung.getId());

        // Verify initial state
        assertThat(isTeilnehmerAvailable(teilnehmer1, slot1)).isFalse();
        assertThat(isRaumAvailable(raum2, slot1)).isFalse();

        // Delete PV
        adminService.deleteVortrag(createdPv.getId(), veranstaltung.getId());

        // Verify new state
        assertThat(isTeilnehmerAvailable(teilnehmer1, slot1)).isTrue(); // Freed
        assertThat(isRaumAvailable(raum2, slot1)).isTrue(); // Freed

        Object[] resultArr = {""};
        QuarkusTransaction.requiringNew().run(() -> resultArr[0] = Pflichtvortrag.findById(createdPv.getId()));
        assertNull(resultArr[0]); // PV deleted
    }

    // -------------------------------------------------------------------
    // Helper methods to check availability
    // -------------------------------------------------------------------

    // Helper to check availability - now runs in its own transaction
    private boolean isTeilnehmerAvailable(Teilnehmer tn, EventSlot slot) {
        final boolean[] boolArr = {false};

        QuarkusTransaction.requiringNew().run(() -> {
            Optional<Verfuegbarkeit> v = Verfuegbarkeit.find("nutzer = ?1 and slot = ?2", tn, slot).firstResultOptional();
            boolArr[0] = v.map(Verfuegbarkeit::isAvailable).orElse(true);
        });
        return boolArr[0];
    }


    // Helper to check room availability - now runs in its own transaction
    private boolean isRaumAvailable(Raum r, EventSlot slot) {
        final boolean[] boolArr = {false};

        QuarkusTransaction.requiringNew().run(() -> {
            Optional<RaumBelegbarkeit> rb = RaumBelegbarkeit.find("raum = ?1 and slot = ?2", r, slot).firstResultOptional();
            boolArr[0] = rb.map(raumBelegbarkeit -> !raumBelegbarkeit.isBelegt()).orElse(true);
        });

        return boolArr[0];
    }
}
