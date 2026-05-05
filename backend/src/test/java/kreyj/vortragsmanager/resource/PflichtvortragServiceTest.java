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

import static org.assertj.core.api.Assertions.assertThat;
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
        assertThat(isTeilnehmerAvailable(teilnehmer1, slot1)).isFalse();
        assertThat(isTeilnehmerAvailable(teilnehmer2, slot1)).isFalse();
        assertThat(isTeilnehmerAvailable(teilnehmer3, slot1)).isTrue(); // TN3 not in Gruppe A
        assertThat(isRaumAvailable(raum2, slot1)).isFalse();
        assertThat(isRaumAvailable(raum1, slot1)).isTrue(); // Raum A not used
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
        assertThat(thrown.getMessage().contains("Raumkapazität von 'Raum A' reicht für die Gruppe 'Gruppe A' nicht aus.")).isTrue();
        final long[] pvCount = {0L};
        QuarkusTransaction.requiringNew().run(() -> pvCount[0] = Pflichtvortrag.count());
        assertEquals(0, pvCount[0]); // No PV created
    }

    @Test
    void testCreatePflichtvortragFailsIfRaumAlreadyOccupiedByAnotherPflichtvortrag() {
        // Create first PV
        Pflichtvortrag pv1 = new Pflichtvortrag();
        pv1.titel = "PV1";
        pv1.referent = referent;
        pv1.pflichtgruppe = "Gruppe A";
        pv1.pflichtraum = raum2;
        pv1.pflichtslot = slot1;
        adminService.createVortrag(pv1, veranstaltung.id);

        // Attempt to create a second PV using the same room and slot
        Pflichtvortrag pv2 = new Pflichtvortrag();
        pv2.titel = "PV2";
        pv2.referent = referent;
        pv2.pflichtgruppe = "Gruppe B"; // Different group
        pv2.pflichtraum = raum2; // Same room
        pv2.pflichtslot = slot1; // Same slot

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () -> {
            adminService.createVortrag(pv2, veranstaltung.id);
        });
        assertThat(thrown.getMessage().contains("Raum 'Raum B' ist im Slot 'Slot 1' bereits belegt.")).isTrue();
        final long[] pvCount = {0L};
        QuarkusTransaction.requiringNew().run(() -> pvCount[0] = Pflichtvortrag.count());
        assertEquals(1, pvCount[0]); // Only PV1 created
    }

    @Test
    void testCreatePflichtvortragFailsIfGruppeAlreadyOccupiedByAnotherPflichtvortrag() {
        // Create first PV
        Pflichtvortrag pv1 = new Pflichtvortrag();
        pv1.titel = "PV1";
        pv1.referent = referent;
        pv1.pflichtgruppe = "Gruppe A";
        pv1.pflichtraum = raum2;
        pv1.pflichtslot = slot1;
        adminService.createVortrag(pv1, veranstaltung.id);

        // Attempt to create a second PV using the same group and slot
        Pflichtvortrag pv2 = new Pflichtvortrag();
        pv2.titel = "PV2";
        pv2.referent = referent;
        pv2.pflichtgruppe = "Gruppe A"; // Same group
        pv2.pflichtraum = raum1; // Different room
        pv2.pflichtslot = slot1; // Same slot

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () -> {
            adminService.createVortrag(pv2, veranstaltung.id);
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
        pv.titel = "PV Initial";
        pv.referent = referent;
        pv.pflichtgruppe = "Gruppe A";
        pv.pflichtraum = raum2; // Raum B hat Kapazität 10, Gruppe A hat 2 TN
        pv.pflichtslot = slot1;
        Pflichtvortrag initialPv = (Pflichtvortrag) adminService.createVortrag(pv, veranstaltung.id);

        // Verify initial state

        assertThat(isTeilnehmerAvailable(teilnehmer1, slot1)).isFalse();
        assertThat(isRaumAvailable(raum2, slot1)).isFalse();
        assertThat(isTeilnehmerAvailable(teilnehmer1, slot2)).isTrue();
        assertThat(isRaumAvailable(raum2, slot2)).isTrue();

        // Update PV to change slot to slot2
        Pflichtvortrag updatedPv = new Pflichtvortrag();
        updatedPv.titel = "PV Updated Slot";
        updatedPv.referent = referent;
        updatedPv.pflichtgruppe = "Gruppe A";
        updatedPv.pflichtraum = raum2;
        updatedPv.pflichtslot = slot2;

        adminService.updateVortrag(initialPv.id, updatedPv, veranstaltung.id);

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
        pv.titel = "PV Initial";
        pv.referent = referent;
        pv.pflichtgruppe = "Gruppe A";
        pv.pflichtraum = raum1; // Kapazität 2
        pv.pflichtslot = slot1;
        Pflichtvortrag initialPv = (Pflichtvortrag) adminService.createVortrag(pv, veranstaltung.id);

        // Verify initial state
        assertThat(isRaumAvailable(raum1, slot1)).isFalse();
        assertThat(isRaumAvailable(raum2, slot1)).isTrue();

        // Update PV to change room to raum2
        Pflichtvortrag updatedPv = new Pflichtvortrag();
        updatedPv.titel = "PV Updated Raum";
        updatedPv.referent = referent;
        updatedPv.pflichtgruppe = "Gruppe A";
        updatedPv.pflichtraum = raum2; // Kapazität 10
        updatedPv.pflichtslot = slot1;

        adminService.updateVortrag(initialPv.id, updatedPv, veranstaltung.id);

        // Verify new state
        assertThat(isRaumAvailable(raum1, slot1)).isTrue(); // Old room freed
        assertThat(isRaumAvailable(raum2, slot1)).isFalse(); // New room occupied
        assertThat(isTeilnehmerAvailable(teilnehmer1, slot1)).isFalse(); // Teilnehmer availability unchanged
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
        assertThat(thrown.getMessage().contains("Neuer Raum 'Raum B' ist im Slot 'Slot 1' bereits belegt.")).isTrue();

        // Verify state remains unchanged
        assertThat(isRaumAvailable(raum1, slot1)).isFalse(); // Raum1 still occupied by PV
        assertThat(isRaumAvailable(raum2, slot1)).isFalse(); // Raum2 still occupied by manual block
    }

    @Test
    void testUpdatePflichtvortragChangeRaumFailsIfNewRaumOccupiedByAnotherPflichtvortrag() {
        // Create PV1
        Pflichtvortrag pv1 = new Pflichtvortrag();
        pv1.titel = "PV1";
        pv1.referent = referent;
        pv1.pflichtgruppe = "Gruppe A";
        pv1.pflichtraum = raum1;
        pv1.pflichtslot = slot1;
        Pflichtvortrag createdPv1 = (Pflichtvortrag) adminService.createVortrag(pv1, veranstaltung.id);

        // Create PV2 that occupies raum2, slot1
        Pflichtvortrag pv2 = new Pflichtvortrag();
        pv2.titel = "PV2";
        pv2.referent = referent;
        pv2.pflichtgruppe = "Gruppe B";
        pv2.pflichtraum = raum2;
        pv2.pflichtslot = slot1;
        adminService.createVortrag(pv2, veranstaltung.id);

        // Attempt to update PV1 to use raum2 (which is occupied by PV2)
        Pflichtvortrag updatedPv1 = new Pflichtvortrag();
        updatedPv1.titel = "PV1 Updated Raum";
        updatedPv1.referent = referent;
        updatedPv1.pflichtgruppe = "Gruppe A";
        updatedPv1.pflichtraum = raum2; // Try to change to raum2
        updatedPv1.pflichtslot = slot1;

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () -> {
            adminService.updateVortrag(createdPv1.id, updatedPv1, veranstaltung.id);
        });
        assertThat(thrown.getMessage().contains("Neuer Raum 'Raum B' ist im Slot 'Slot 1' bereits belegt.")).isTrue();

        // Verify state remains unchanged for PV1
        assertThat(isRaumAvailable(raum1, slot1)).isFalse(); // raum1 still occupied by PV1
        assertThat(isRaumAvailable(raum2, slot1)).isFalse(); // raum2 still occupied by PV2
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
        assertThat(isTeilnehmerAvailable(teilnehmer1, slot1)).isFalse(); // Gruppe A
        assertThat(isTeilnehmerAvailable(teilnehmer2, slot1)).isFalse(); // Gruppe A
        assertThat(isTeilnehmerAvailable(teilnehmer3, slot1)).isTrue(); // Gruppe B

        // Update PV to change group to Gruppe B
        Pflichtvortrag updatedPv = new Pflichtvortrag();
        updatedPv.titel = "PV Updated Gruppe";
        updatedPv.referent = referent;
        updatedPv.pflichtgruppe = "Gruppe B";
        updatedPv.pflichtraum = raum2;
        updatedPv.pflichtslot = slot1;

        adminService.updateVortrag(initialPv.id, updatedPv, veranstaltung.id);

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
        assertThat(thrown.getMessage().contains("Nicht alle Teilnehmer der neuen Gruppe 'Gruppe B' sind im Slot 'Slot 1' verfügbar.")).isTrue();

        // Verify state remains unchanged
        assertThat(isTeilnehmerAvailable(teilnehmer1, slot1)).isFalse(); // Gruppe A still occupied by PV
        assertThat(isTeilnehmerAvailable(teilnehmer3, slot1)).isFalse(); // Gruppe B still unavailable by manual block
    }

    @Test
    void testUpdatePflichtvortragChangeGruppeFailsIfNewGruppeOccupiedByAnotherPflichtvortrag() {
        // Create PV1 for Gruppe A, Slot 1
        Pflichtvortrag pv1 = new Pflichtvortrag();
        pv1.titel = "PV1";
        pv1.referent = referent;
        pv1.pflichtgruppe = "Gruppe A";
        pv1.pflichtraum = raum2;
        pv1.pflichtslot = slot1;
        Pflichtvortrag createdPv1 = (Pflichtvortrag) adminService.createVortrag(pv1, veranstaltung.id);

        // Create PV2 for Gruppe B, Slot 1
        Pflichtvortrag pv2 = new Pflichtvortrag();
        pv2.titel = "PV2";
        pv2.referent = referent;
        pv2.pflichtgruppe = "Gruppe B";
        pv2.pflichtraum = raum1;
        pv2.pflichtslot = slot1;
        adminService.createVortrag(pv2, veranstaltung.id);

        // Attempt to update PV1 to use Gruppe B (which is occupied by PV2)
        Pflichtvortrag updatedPv1 = new Pflichtvortrag();
        updatedPv1.titel = "PV1 Updated Gruppe";
        updatedPv1.referent = referent;
        updatedPv1.pflichtgruppe = "Gruppe B"; // Try to change to Gruppe B
        updatedPv1.pflichtraum = raum2;
        updatedPv1.pflichtslot = slot1;

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () -> {
            adminService.updateVortrag(createdPv1.id, updatedPv1, veranstaltung.id);
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
        pv.titel = "PV Test";
        pv.referent = referent;
        pv.pflichtgruppe = "Gruppe A";
        pv.pflichtraum = raum2;
        pv.pflichtslot = slot1;
        Pflichtvortrag createdPv = (Pflichtvortrag) adminService.createVortrag(pv, veranstaltung.id);

        // Verify initial state
        assertThat(isTeilnehmerAvailable(teilnehmer1, slot1)).isFalse();
        assertThat(isRaumAvailable(raum2, slot1)).isFalse();

        // Delete PV
        adminService.deleteVortrag(createdPv.id, veranstaltung.id);

        // Verify new state
        assertThat(isTeilnehmerAvailable(teilnehmer1, slot1)).isTrue(); // Freed
        assertThat(isRaumAvailable(raum2, slot1)).isTrue(); // Freed

        Object[] resultArr = {""};
        QuarkusTransaction.requiringNew().run(() -> resultArr[0] = Pflichtvortrag.findById(createdPv.id));
        assertNull(resultArr[0]); // PV deleted
    }
}
