package kreyj.vortragsmanager.resource;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.h2.H2DatabaseTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import kreyj.vortragsmanager.entity.*;
import kreyj.vortragsmanager.service.AdminService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

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
        admin.email = "admin@example.com";
        admin.passwordHash = "hash";
        admin.persist();

        veranstaltung = new Veranstaltung();
        veranstaltung.name = "Test Event";
        veranstaltung.beginntAm = LocalDateTime.of(2024, 1, 1, 9, 0);
        veranstaltung.endetAm = LocalDateTime.of(2024, 1, 1, 17, 0);
        veranstaltung.persist();

        gebaeude = new Gebaeude();
        gebaeude.name = "Hauptgebäude";
        gebaeude.typ = Gebaeude.Gebaeudetyp.SCHULE;
        gebaeude.postleitzahl = "12345";
        gebaeude.ort = "Testort";
        gebaeude.strasse = "Teststraße";
        gebaeude.persist();
        veranstaltung.addGebaeude(gebaeude);
        veranstaltung.persist();

        raum1 = new Raum();
        raum1.name = "Raum A";
        raum1.kapazitaet = 2;
        raum1.gebaeude = gebaeude;
        raum1.persist();

        raum2 = new Raum();
        raum2.name = "Raum B";
        raum2.kapazitaet = 10;
        raum2.gebaeude = gebaeude;
        raum2.persist();

        slot1 = new EventSlot();
        slot1.description = "Slot 1";
        slot1.startTime = LocalDateTime.of(2024, 1, 1, 9, 0);
        slot1.endTime = LocalDateTime.of(2024, 1, 1, 10, 0);
        slot1.veranstaltung = veranstaltung;
        slot1.persist();

        slot2 = new EventSlot();
        slot2.description = "Slot 2";
        slot2.startTime = LocalDateTime.of(2024, 1, 1, 10, 0);
        slot2.endTime = LocalDateTime.of(2024, 1, 1, 11, 0);
        slot2.veranstaltung = veranstaltung;
        slot2.persist();

        referent = new Referent();
        referent.email = "ref@example.com";
        referent.firstName = "Ref";
        referent.lastName = "Erent";
        referent.passwordHash = "hash";
        referent.addVeranstaltung(veranstaltung);
        referent.persist();

        teilnehmer1 = new Teilnehmer();
        teilnehmer1.email = "tn1@example.com";
        teilnehmer1.firstName = "TN1";
        teilnehmer1.lastName = "GruppeA";
        teilnehmer1.gruppe = "Gruppe A";
        teilnehmer1.isActive = true;
        teilnehmer1.addVeranstaltung(veranstaltung);
        teilnehmer1.persist();

        teilnehmer2 = new Teilnehmer();
        teilnehmer2.email = "tn2@example.com";
        teilnehmer2.firstName = "TN2";
        teilnehmer2.lastName = "GruppeA";
        teilnehmer2.gruppe = "Gruppe A";
        teilnehmer2.isActive = true;
        teilnehmer2.addVeranstaltung(veranstaltung);
        teilnehmer2.persist();

        teilnehmer3 = new Teilnehmer();
        teilnehmer3.email = "tn3@example.com";
        teilnehmer3.firstName = "TN3";
        teilnehmer3.lastName = "GruppeB";
        teilnehmer3.gruppe = "Gruppe B";
        teilnehmer3.isActive = true;
        teilnehmer3.addVeranstaltung(veranstaltung);
        teilnehmer3.persist();
    }

    // Helper to check availability - now runs in its own transaction
    private boolean isTeilnehmerAvailable(Teilnehmer tn, EventSlot slot) {
        final boolean[] boolArr = {false};

        QuarkusTransaction.requiringNew().run(() -> {
            Optional<Verfuegbarkeit> v = Verfuegbarkeit.find("nutzer = ?1 and slot = ?2", tn, slot).firstResultOptional();
            boolArr[0] = v.map(verfuegbarkeit -> verfuegbarkeit.isAvailable).orElse(true);
        });
        return boolArr[0];
    }

    // Helper to check room availability - now runs in its own transaction
    private boolean isRaumAvailable(Raum r, EventSlot slot) {
        final boolean[] boolArr = {false};

        QuarkusTransaction.requiringNew().run(() -> {
            Optional<RaumBelegbarkeit> rb = RaumBelegbarkeit.find("raum = ?1 and slot = ?2", r, slot).firstResultOptional();
            boolArr[0] = rb.map(raumBelegbarkeit -> !raumBelegbarkeit.isBelegt).orElse(true);
        });

        return boolArr[0];
    }

    @Test
    void testCreatePflichtvortragSuccess() {
        Pflichtvortrag pv = new Pflichtvortrag();
        pv.titel = "PV Test";
        pv.referent = referent;
        pv.pflichtgruppe = "Gruppe A";
        pv.pflichtraum = raum2; // Raum B hat Kapazität 10, Gruppe A hat 2 TN
        pv.pflichtslot = slot1;

        Pflichtvortrag createdPv = (Pflichtvortrag) adminService.createVortrag(pv, veranstaltung.id);

        assertNotNull(createdPv.id);
        assertEquals("PV Test", createdPv.titel);
        assertFalse(isTeilnehmerAvailable(teilnehmer1, slot1));
        assertFalse(isTeilnehmerAvailable(teilnehmer2, slot1));
        assertTrue(isTeilnehmerAvailable(teilnehmer3, slot1)); // TN3 not in Gruppe A
        assertFalse(isRaumAvailable(raum2, slot1));
        assertTrue(isRaumAvailable(raum1, slot1)); // Raum A not used
    }

    @Test
    void testCreatePflichtvortragRaumBelegtFails() {
        // Manually block raum2, slot1 in a committed transaction
        QuarkusTransaction.requiringNew().run(() -> {
            RaumBelegbarkeit rb = new RaumBelegbarkeit();
            rb.raum = raum2;
            rb.slot = slot1;
            rb.isBelegt = true;
            rb.persist();
        });

        Pflichtvortrag pv = new Pflichtvortrag();
        pv.titel = "PV Test";
        pv.referent = referent;
        pv.pflichtgruppe = "Gruppe A";
        pv.pflichtraum = raum2;
        pv.pflichtslot = slot1;

        assertThrows(IllegalArgumentException.class, () -> adminService.createVortrag(pv, veranstaltung.id));
        final long[] pvCount = {0L};
        QuarkusTransaction.requiringNew().run(() -> pvCount[0] = Pflichtvortrag.count());
        assertEquals(0L, pvCount[0]); // No PV created
    }

    @Test
    void testCreatePflichtvortragTeilnehmerNichtVerfuegbarFails() {
        // Manually make teilnehmer1 unavailable for slot1 in a committed transaction
        QuarkusTransaction.requiringNew().run(() -> {
            Verfuegbarkeit vf = new Verfuegbarkeit();
            vf.nutzer = teilnehmer1;
            vf.slot = slot1;
            vf.isAvailable = false;
            vf.persist();
        });

        Pflichtvortrag pv = new Pflichtvortrag();
        pv.titel = "PV Test";
        pv.referent = referent;
        pv.pflichtgruppe = "Gruppe A";
        pv.pflichtraum = raum2;
        pv.pflichtslot = slot1;

        assertThrows(IllegalArgumentException.class, () -> adminService.createVortrag(pv, veranstaltung.id));
        final long[] pvCount = {0L};
        QuarkusTransaction.requiringNew().run(() -> pvCount[0] = Pflichtvortrag.count());
        Assertions.assertEquals(0L, pvCount[0]); // No PV created
    }

    @Test
    void testCreatePflichtvortragRaumKapazitaetFails() {
        Pflichtvortrag pv = new Pflichtvortrag();
        pv.titel = "PV Test";
        pv.referent = referent;
        pv.pflichtgruppe = "Gruppe A"; // 2 Teilnehmer
        pv.pflichtraum = raum1; // Kapazität 2 (initial)
        QuarkusTransaction.requiringNew().run(() -> { // Update raum1 in a committed transaction
            // Retrieve the Raum entity by its ID within this new transaction
            Raum r = Raum.findById(raum1.id);
            r.kapazitaet = 1;
            pv.pflichtraum = r;
        });
        pv.pflichtslot = slot1;

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () -> {
            adminService.createVortrag(pv, veranstaltung.id);
        });
        assertTrue(thrown.getMessage().contains("Raumkapazität von 'Raum A' reicht für die Gruppe 'Gruppe A' nicht aus."));
        final long[] pvCount = {0L};
        QuarkusTransaction.requiringNew().run(() -> pvCount[0] = Pflichtvortrag.count());
        assertEquals(0, pvCount[0]); // No PV created
    }

    @Test
    void testUpdatePflichtvortragChangeSlotSuccess() {
        // Create initial PV
        Pflichtvortrag pv = new Pflichtvortrag();
        pv.titel = "PV Initial";
        pv.referent = referent;
        pv.pflichtgruppe = "Gruppe A";
        pv.pflichtraum = raum2; // Raum B hat Kapazität 10, Gruppe A hat 2 TN
        pv.pflichtslot = slot1;
        Pflichtvortrag initialPv = (Pflichtvortrag) adminService.createVortrag(pv, veranstaltung.id);

        // Verify initial state
        assertFalse(isTeilnehmerAvailable(teilnehmer1, slot1));
        assertFalse(isRaumAvailable(raum2, slot1));
        assertTrue(isTeilnehmerAvailable(teilnehmer1, slot2));
        assertTrue(isRaumAvailable(raum2, slot2));

        // Update PV to change slot to slot2
        Pflichtvortrag updatedPv = new Pflichtvortrag();
        updatedPv.titel = "PV Updated Slot";
        updatedPv.referent = referent;
        updatedPv.pflichtgruppe = "Gruppe A";
        updatedPv.pflichtraum = raum2;
        updatedPv.pflichtslot = slot2;

        adminService.updateVortrag(initialPv.id, updatedPv, veranstaltung.id);

        // Verify new state
        assertTrue(isTeilnehmerAvailable(teilnehmer1, slot1)); // Old slot freed
        assertTrue(isRaumAvailable(raum2, slot1)); // Old room-slot freed
        assertFalse(isTeilnehmerAvailable(teilnehmer1, slot2)); // New slot occupied
        assertFalse(isRaumAvailable(raum2, slot2)); // New room-slot occupied
    }

    @Test
    void testUpdatePflichtvortragChangeSlotFailsIfNewSlotNotAvailable() {
        // Create initial PV
        Pflichtvortrag pv = new Pflichtvortrag();
        pv.titel = "PV Initial";
        pv.referent = referent;
        pv.pflichtgruppe = "Gruppe A";
        pv.pflichtraum = raum2;
        pv.pflichtslot = slot1;
        Pflichtvortrag initialPv = (Pflichtvortrag) adminService.createVortrag(pv, veranstaltung.id);

        // Manually block slot2 for teilnehmer1 in a committed transaction
        QuarkusTransaction.requiringNew().run(() -> {
            Verfuegbarkeit vf = new Verfuegbarkeit();
            vf.nutzer = teilnehmer1;
            vf.slot = slot2;
            vf.isAvailable = false;
            vf.persist();
        });

        // Attempt to update PV to change slot to slot2
        Pflichtvortrag updatedPv = new Pflichtvortrag();
        updatedPv.titel = "PV Updated Slot";
        updatedPv.referent = referent;
        updatedPv.pflichtgruppe = "Gruppe A";
        updatedPv.pflichtraum = raum2;
        updatedPv.pflichtslot = slot2;

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () -> {
            adminService.updateVortrag(initialPv.id, updatedPv, veranstaltung.id);
        });
        assertTrue(thrown.getMessage().contains("Nicht alle Teilnehmer der Gruppe 'Gruppe A' sind im neuen Slot 'Slot 2' verfügbar."));

        // Verify state remains unchanged
        assertFalse(isTeilnehmerAvailable(teilnehmer1, slot1));
        assertFalse(isRaumAvailable(raum2, slot1));
        assertFalse(isTeilnehmerAvailable(teilnehmer1, slot2)); // Still unavailable due to manual block
        assertTrue(isRaumAvailable(raum2, slot2)); // Room was not blocked by PV update
    }

    @Test
    void testUpdatePflichtvortragChangeRaumSuccess() {
        // Create initial PV
        Pflichtvortrag pv = new Pflichtvortrag();
        pv.titel = "PV Initial";
        pv.referent = referent;
        pv.pflichtgruppe = "Gruppe A";
        pv.pflichtraum = raum1; // Kapazität 2
        pv.pflichtslot = slot1;
        Pflichtvortrag initialPv = (Pflichtvortrag) adminService.createVortrag(pv, veranstaltung.id);

        // Verify initial state
        assertFalse(isRaumAvailable(raum1, slot1));
        assertTrue(isRaumAvailable(raum2, slot1));

        // Update PV to change room to raum2
        Pflichtvortrag updatedPv = new Pflichtvortrag();
        updatedPv.titel = "PV Updated Raum";
        updatedPv.referent = referent;
        updatedPv.pflichtgruppe = "Gruppe A";
        updatedPv.pflichtraum = raum2; // Kapazität 10
        updatedPv.pflichtslot = slot1;

        adminService.updateVortrag(initialPv.id, updatedPv, veranstaltung.id);

        // Verify new state
        assertTrue(isRaumAvailable(raum1, slot1)); // Old room freed
        assertFalse(isRaumAvailable(raum2, slot1)); // New room occupied
        assertFalse(isTeilnehmerAvailable(teilnehmer1, slot1)); // Teilnehmer availability unchanged
    }

    @Test
    void testUpdatePflichtvortragChangeRaumFailsIfNewRaumBelegt() {
        // Create initial PV
        Pflichtvortrag pv = new Pflichtvortrag();
        pv.titel = "PV Initial";
        pv.referent = referent;
        pv.pflichtgruppe = "Gruppe A";
        pv.pflichtraum = raum1;
        pv.pflichtslot = slot1;
        Pflichtvortrag initialPv = (Pflichtvortrag) adminService.createVortrag(pv, veranstaltung.id);

        // Manually block raum2, slot1 in a committed transaction
        QuarkusTransaction.requiringNew().run(() -> {
            RaumBelegbarkeit rb = new RaumBelegbarkeit();
            rb.raum = raum2;
            rb.slot = slot1;
            rb.isBelegt = true;
            rb.persist();
        });

        // Attempt to update PV to change room to raum2
        Pflichtvortrag updatedPv = new Pflichtvortrag();
        updatedPv.titel = "PV Updated Raum";
        updatedPv.referent = referent;
        updatedPv.pflichtgruppe = "Gruppe A";
        updatedPv.pflichtraum = raum2;
        updatedPv.pflichtslot = slot1;

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () -> {
            adminService.updateVortrag(initialPv.id, updatedPv, veranstaltung.id);
        });
        assertTrue(thrown.getMessage().contains("Neuer Raum 'Raum B' ist im Slot 'Slot 1' bereits belegt."));

        // Verify state remains unchanged
        assertFalse(isRaumAvailable(raum1, slot1)); // Raum1 still occupied by PV
        assertFalse(isRaumAvailable(raum2, slot1)); // Raum2 still occupied by manual block
    }

    @Test
    void testUpdatePflichtvortragChangeGruppeSuccess() {
        // Create initial PV for Gruppe A
        Pflichtvortrag pv = new Pflichtvortrag();
        pv.titel = "PV Initial";
        pv.referent = referent;
        pv.pflichtgruppe = "Gruppe A";
        pv.pflichtraum = raum2;
        pv.pflichtslot = slot1;
        Pflichtvortrag initialPv = (Pflichtvortrag) adminService.createVortrag(pv, veranstaltung.id);

        // Verify initial state
        assertFalse(isTeilnehmerAvailable(teilnehmer1, slot1)); // Gruppe A
        assertFalse(isTeilnehmerAvailable(teilnehmer2, slot1)); // Gruppe A
        assertTrue(isTeilnehmerAvailable(teilnehmer3, slot1)); // Gruppe B

        // Update PV to change group to Gruppe B
        Pflichtvortrag updatedPv = new Pflichtvortrag();
        updatedPv.titel = "PV Updated Gruppe";
        updatedPv.referent = referent;
        updatedPv.pflichtgruppe = "Gruppe B";
        updatedPv.pflichtraum = raum2;
        updatedPv.pflichtslot = slot1;

        adminService.updateVortrag(initialPv.id, updatedPv, veranstaltung.id);

        // Verify new state
        assertTrue(isTeilnehmerAvailable(teilnehmer1, slot1)); // Old group freed
        assertTrue(isTeilnehmerAvailable(teilnehmer2, slot1)); // Old group freed
        assertFalse(isTeilnehmerAvailable(teilnehmer3, slot1)); // New group occupied
        assertFalse(isRaumAvailable(raum2, slot1)); // Room availability unchanged
    }

    @Test
    void testUpdatePflichtvortragChangeGruppeFailsIfNewGruppeNotAvailable() {
        // Create initial PV for Gruppe A
        Pflichtvortrag pv = new Pflichtvortrag();
        pv.titel = "PV Initial";
        pv.referent = referent;
        pv.pflichtgruppe = "Gruppe A";
        pv.pflichtraum = raum2;
        pv.pflichtslot = slot1;
        Pflichtvortrag initialPv = (Pflichtvortrag) adminService.createVortrag(pv, veranstaltung.id);

        // Manually make teilnehmer3 (Gruppe B) unavailable for slot1 in a committed transaction
        QuarkusTransaction.requiringNew().run(() -> {
            Verfuegbarkeit vf = new Verfuegbarkeit();
            vf.nutzer = teilnehmer3;
            vf.slot = slot1;
            vf.isAvailable = false;
            vf.persist();
        });

        // Attempt to update PV to change group to Gruppe B
        Pflichtvortrag updatedPv = new Pflichtvortrag();
        updatedPv.titel = "PV Updated Gruppe";
        updatedPv.referent = referent;
        updatedPv.pflichtgruppe = "Gruppe B";
        updatedPv.pflichtraum = raum2;
        updatedPv.pflichtslot = slot1;

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () -> {
            adminService.updateVortrag(initialPv.id, updatedPv, veranstaltung.id);
        });
        assertTrue(thrown.getMessage().contains("Nicht alle Teilnehmer der neuen Gruppe 'Gruppe B' sind im Slot 'Slot 1' verfügbar."));

        // Verify state remains unchanged
        assertFalse(isTeilnehmerAvailable(teilnehmer1, slot1)); // Gruppe A still occupied by PV
        assertFalse(isTeilnehmerAvailable(teilnehmer3, slot1)); // Gruppe B still unavailable by manual block
    }

    @Test
    void testDeletePflichtvortragSuccess() {
        // Create initial PV
        Pflichtvortrag pv = new Pflichtvortrag();
        pv.titel = "PV Test";
        pv.referent = referent;
        pv.pflichtgruppe = "Gruppe A";
        pv.pflichtraum = raum2;
        pv.pflichtslot = slot1;
        Pflichtvortrag createdPv = (Pflichtvortrag) adminService.createVortrag(pv, veranstaltung.id);

        // Verify initial state
        assertFalse(isTeilnehmerAvailable(teilnehmer1, slot1));
        assertFalse(isRaumAvailable(raum2, slot1));

        // Delete PV
        adminService.deleteVortrag(createdPv.id, veranstaltung.id);

        // Verify new state
        assertTrue(isTeilnehmerAvailable(teilnehmer1, slot1)); // Freed
        assertTrue(isRaumAvailable(raum2, slot1)); // Freed

        Object[] resultArr = {""};
        QuarkusTransaction.requiringNew().run(() -> resultArr[0] = Pflichtvortrag.findById(createdPv.id));
        assertNull(resultArr[0]); // PV deleted
    }

    @Test
    void testDeletePflichtvortragConditionalReleaseTeilnehmer() {
        // Create PV1 for Gruppe A, Slot 1
        Pflichtvortrag pv1 = new Pflichtvortrag();
        pv1.titel = "PV1";
        pv1.referent = referent;
        pv1.pflichtgruppe = "Gruppe A";
        pv1.pflichtraum = raum2;
        pv1.pflichtslot = slot1;
        Pflichtvortrag createdPv1 = (Pflichtvortrag) adminService.createVortrag(pv1, veranstaltung.id);

        // Manually make teilnehmer1 unavailable for slot1, simulating another reason, in a committed transaction
        QuarkusTransaction.requiringNew().run(() -> {
            Verfuegbarkeit vf = new Verfuegbarkeit();
            vf.nutzer = teilnehmer1;
            vf.slot = slot1;
            vf.isAvailable = false;
            vf.persist();
        });

        // Delete PV1
        adminService.deleteVortrag(createdPv1.id, veranstaltung.id);

        // Verify teilnehmer1 is *still* unavailable because of the manual entry
        assertFalse(isTeilnehmerAvailable(teilnehmer1, slot1));
        // teilnehmer2 should be available as only PV1 made it unavailable
        assertTrue(isTeilnehmerAvailable(teilnehmer2, slot1));
        // Raum should be available as only PV1 made it unavailable
        assertTrue(isRaumAvailable(raum2, slot1));
    }

    @Test
    void testDeletePflichtvortragConditionalReleaseRaum() {
        // Create PV1 for Gruppe A, Raum 2, Slot 1
        Pflichtvortrag pv1 = new Pflichtvortrag();
        pv1.titel = "PV1";
        pv1.referent = referent;
        pv1.pflichtgruppe = "Gruppe A";
        pv1.pflichtraum = raum2;
        pv1.pflichtslot = slot1;
        Pflichtvortrag createdPv1 = (Pflichtvortrag) adminService.createVortrag(pv1, veranstaltung.id);

        // Manually block raum2, slot1, simulating another reason for it to be blocked, in a committed transaction
        QuarkusTransaction.requiringNew().run(() -> {
            RaumBelegbarkeit rb = new RaumBelegbarkeit();
            rb.raum = raum2;
            rb.slot = slot1;
            rb.isBelegt = true;
            rb.persist();
        });

        // Delete PV1
        adminService.deleteVortrag(createdPv1.id, veranstaltung.id);

        // Verify raum2 is *still* unavailable because of the manual entry
        assertFalse(isRaumAvailable(raum2, slot1));
        // Teilnehmer should be available as only PV1 made them unavailable
        assertTrue(isTeilnehmerAvailable(teilnehmer1, slot1));
    }
}
