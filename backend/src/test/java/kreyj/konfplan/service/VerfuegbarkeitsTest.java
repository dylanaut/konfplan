package kreyj.konfplan.service;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import kreyj.konfplan.dto.NutzerDto;
import kreyj.konfplan.persistence.*;
import kreyj.konfplan.resource.AdminResource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
public class VerfuegbarkeitsTest {

    @Inject
    AdminService adminService;

    private Veranstaltung veranstaltung;
    private EventSlot slot1;
    private EventSlot slot2;
    private Teilnehmer teilnehmer;
    private Admin admin;
    private Referent referent;

    @BeforeEach
    @Transactional
    void setUp() {
        // Clean up database before each test
        Zuweisung.deleteAll();
        Prioritaet.deleteAll();
        Verfuegbarkeit.deleteAll();
        Vortrag.deleteAll();
        EventSlot.deleteAll();
        Nutzer.deleteAll();
        Veranstaltung.deleteAll();
        Raum.deleteAll();
        Gebaeude.deleteAll();

        // Create an Admin to be the organizer
        admin = new Admin();
        admin.email = "admin@test.com";
        admin.firstName = "Admin";
        admin.lastName = "User";
        admin.passwordHash = "test";
        admin.persist();

        // Create a Referent for the mandatory talks
        referent = new Referent();
        referent.email = "referent@test.com";
        referent.firstName = "Referent";
        referent.lastName = "User";
        referent.passwordHash = "test";
        referent.persist();


        // Create common test data
        veranstaltung = new Veranstaltung();
        veranstaltung.name = "Test Event " + System.currentTimeMillis();
        veranstaltung.beginntAm = LocalDateTime.now();
        veranstaltung.endetAm = LocalDateTime.now().plusDays(1);
        veranstaltung.logo = "logo.png";
        veranstaltung.logo_link = "http://example.com";
        veranstaltung.addNutzer(admin); // Assign organizer
        veranstaltung.persist();

        slot1 = new EventSlot();
        slot1.description = "Slot 1";
        slot1.startTime = veranstaltung.beginntAm.plusHours(1);
        slot1.endTime = veranstaltung.beginntAm.plusHours(2);
        adminService.createEventSlot(slot1, veranstaltung.id);

        slot2 = new EventSlot();
        slot2.description = "Slot 2";
        slot2.startTime = veranstaltung.beginntAm.plusHours(3);
        slot2.endTime = veranstaltung.beginntAm.plusHours(4);
        adminService.createEventSlot(slot2, veranstaltung.id);

        NutzerDto teilnehmerDto = new NutzerDto();
        teilnehmerDto.email = "test.teilnehmer@example.com";
        teilnehmerDto.firstName = "Test";
        teilnehmerDto.lastName = "Teilnehmer";
        teilnehmerDto.role = "TEILNEHMER";
        teilnehmerDto.isActive = true;
        NutzerDto createdNutzer = adminService.createUser(teilnehmerDto, List.of());
        teilnehmer = Teilnehmer.findById(createdNutzer.id);
    }

    @Test
    @Transactional
    void testAddTeilnehmerToVeranstaltung() {
        // 1. Add participant to event
        adminService.inviteUserToEvent(teilnehmer.id, veranstaltung.id);

        // 2. Assert that availabilities are created
        long count = Verfuegbarkeit.count("nutzer", teilnehmer);
        assertEquals(2, count, "Participant should have 2 availability records.");

        List<Verfuegbarkeit> verfuegbarkeiten = Verfuegbarkeit.find("nutzer", teilnehmer).list();
        assertTrue(verfuegbarkeiten.stream().allMatch(v -> v.isAvailable), "All availabilities should be true by default.");
    }

    @Test
    @Transactional
    void testRemoveTeilnehmerFromVeranstaltung() {
        // 1. Add participant and verify initial state
        adminService.inviteUserToEvent(teilnehmer.id, veranstaltung.id);
        assertEquals(2, Verfuegbarkeit.count("nutzer", teilnehmer));

        // 2. Remove participant from event
        NutzerDto dto = AdminResource.mapNutzerToDto(teilnehmer);
        adminService.updateUser(teilnehmer.id, dto, List.of()); // Pass empty list of events

        // 3. Assert that availabilities are gone
        long count = Verfuegbarkeit.count("nutzer = ?1 and slot.veranstaltung = ?2", teilnehmer, veranstaltung);
        assertEquals(0, count, "Availabilities for the event should be removed.");
    }

    @Test
    @Transactional
    void testAddEventSlotToVeranstaltung() {
        // 1. Add participant to event
        adminService.inviteUserToEvent(teilnehmer.id, veranstaltung.id);
        assertEquals(2, Verfuegbarkeit.count("nutzer", teilnehmer));

        // 2. Add a new slot to the event
        EventSlot slot3 = new EventSlot();
        slot3.description = "Slot 3";
        slot3.startTime = veranstaltung.beginntAm.plusHours(5);
        slot3.endTime = veranstaltung.beginntAm.plusHours(6);
        adminService.createEventSlot(slot3, veranstaltung.id);

        // 3. Assert that the participant has a new availability
        assertEquals(3, Verfuegbarkeit.count("nutzer", teilnehmer), "Participant should have 3 availability records after adding a slot.");
        Verfuegbarkeit neueVerfuegbarkeit = Verfuegbarkeit.find("nutzer = ?1 and slot = ?2", teilnehmer, slot3).firstResult();
        assertNotNull(neueVerfuegbarkeit, "Availability for the new slot should exist.");
        assertTrue(neueVerfuegbarkeit.isAvailable, "New availability should be true.");
    }

    @Test
    @Transactional
    void testRemoveEventSlotFromVeranstaltung() {
        // 1. Add participant to event
        adminService.inviteUserToEvent(teilnehmer.id, veranstaltung.id);
        assertEquals(2, Verfuegbarkeit.count("nutzer", teilnehmer));

        // 2. Remove a slot from the event
        adminService.deleteEventSlot(slot2.id, veranstaltung.id);

        // 3. Assert that the participant's availability for that slot is removed
        assertEquals(1, Verfuegbarkeit.count("nutzer", teilnehmer), "Participant should have 1 availability record left.");
        long countForSlot2 = Verfuegbarkeit.count("nutzer = ?1 and slot = ?2", teilnehmer, slot2);
        assertEquals(0, countForSlot2, "Availability for the deleted slot should be removed.");
    }

    @Test
    @Transactional
    void testAssignTeilnehmerToGruppeWithPflichtvortrag() {
        // 1. Setup: Add participant to event, create mandatory lecture
        adminService.inviteUserToEvent(teilnehmer.id, veranstaltung.id);

        Raum r = new Raum();
        r.name = "Test Raum";
        r.kapazitaet = 30;
        r.persistAndFlush();

        Gebaeude g = new Gebaeude();
        g.name = "Test Gebaeude";
        g.strasse = "Testweg";
        g.hausnummer = "1";
        g.postleitzahl = "12345";
        g.ort = "Teststadt";
        g.typ = Gebaeude.Gebaeudetyp.SCHULE;
        g.addRaum(r);

        g.persist();

        Pflichtvortrag pv = new Pflichtvortrag();
        pv.titel = "Mandatory Talk";
        pv.veranstaltung = veranstaltung;
        pv.pflichtgruppe = "GroupA";
        pv.pflichtslot = slot1;
        pv.pflichtraum = r;
        pv.referent = referent; // Assign mandatory referent
        pv.persist();

        // 2. Assign participant to the group
        NutzerDto dto = AdminResource.mapNutzerToDto(Nutzer.findById(teilnehmer.id));
        dto.gruppe = "GroupA";
        adminService.updateUser(teilnehmer.id, dto, List.of(veranstaltung.id));

        // 3. Assert that the availability for the mandatory slot is now false
        Verfuegbarkeit verfuegbarkeitSlot1 = Verfuegbarkeit.find("nutzer = ?1 and slot = ?2", teilnehmer, slot1).firstResult();
        Verfuegbarkeit verfuegbarkeitSlot2 = Verfuegbarkeit.find("nutzer = ?1 and slot = ?2", teilnehmer, slot2).firstResult();

        assertNotNull(verfuegbarkeitSlot1);
        assertFalse(verfuegbarkeitSlot1.isAvailable, "Availability for mandatory slot should be false.");

        assertNotNull(verfuegbarkeitSlot2);
        assertTrue(verfuegbarkeitSlot2.isAvailable, "Availability for other slot should remain true.");
    }

    @Test
    @Transactional
    void testRemoveTeilnehmerFromGruppeWithPflichtvortrag() {
        // 1. Setup: Participant is in a group with a mandatory lecture
        adminService.inviteUserToEvent(teilnehmer.id, veranstaltung.id);
        Gebaeude g = new Gebaeude();
        g.name = "Test Gebaeude";
        g.strasse = "Testweg";
        g.hausnummer = "1";
        g.postleitzahl = "12345";
        g.ort = "Teststadt";
        g.typ = Gebaeude.Gebaeudetyp.SCHULE;
        g.persist();
        Raum r = new Raum();
        r.name = "Test Raum";
        r.gebaeude = g;
        r.kapazitaet = 30;
        r.persist();
        Pflichtvortrag pv = new Pflichtvortrag();
        pv.titel = "Mandatory Talk";
        pv.veranstaltung = veranstaltung;
        pv.pflichtgruppe = "GroupA";
        pv.pflichtslot = slot1;
        pv.pflichtraum = r;
        pv.referent = referent; // Assign mandatory referent
        pv.persist();
        NutzerDto dto = AdminResource.mapNutzerToDto(teilnehmer);
        dto.gruppe = "GroupA";
        dto = adminService.updateUser(teilnehmer.id, dto, List.of(veranstaltung.id));

        // Verify initial state
        Verfuegbarkeit verfuegbarkeitSlot1_before = Verfuegbarkeit.find("nutzer = ?1 and slot = ?2", teilnehmer, slot1).firstResult();
        assertFalse(verfuegbarkeitSlot1_before.isAvailable, "Pre-condition: Availability should be false.");

        // 2. Remove participant from the group

        dto.gruppe = "GroupB"; // or null
        adminService.updateUser(teilnehmer.id, dto, List.of(veranstaltung.id));

        // 3. Assert that the availability is reset to true
        Verfuegbarkeit verfuegbarkeitSlot1_after = Verfuegbarkeit.find("nutzer = ?1 and slot = ?2", teilnehmer, slot1).firstResult();
        assertNotNull(verfuegbarkeitSlot1_after);
        assertTrue(verfuegbarkeitSlot1_after.isAvailable, "Availability should be reset to true after leaving the group.");
    }
}