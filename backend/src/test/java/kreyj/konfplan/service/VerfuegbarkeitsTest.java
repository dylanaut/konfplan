package kreyj.konfplan.service;

import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import kreyj.konfplan.application.service.AdminService;
import kreyj.konfplan.presentation.dto.NutzerDto;
import kreyj.konfplan.persistence.*;
import kreyj.konfplan.presentation.AdminResource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
public class VerfuegbarkeitsTest {

    @Inject
    AdminService adminService;

    private Veranstaltung veranstaltung;
    private Slot slot1;
    private Slot slot2;
    private Teilnehmer teilnehmer;
    private Admin admin;
    private Referent referent;

    @BeforeEach
    @Transactional
    void setUp() {
        // Clean up database before each test
        Zuweisung.deleteAll();
        Prioritaet.deleteAll();
        NutzerVerfuegbarkeit.deleteAll();
        Vortrag.deleteAll();
        RaumVerfuegbarkeit.deleteAll();
        Slot.deleteAll();
        Nutzer.deleteAll();
        Planungsergebnis.deleteAll();
        Veranstaltung.deleteAll();
        Raum.deleteAll();
        Gebaeude.deleteAll();

        // Create an Admin to be the organizer
        admin = new Admin();
        admin.setEmail("admin@test.com");
        admin.setFirstName("Admin");
        admin.setLastName("User");
        admin.setPasswordHash("test");
        admin.persist();

        // Create a Referent for the mandatory talks
        referent = new Referent();
        referent.setEmail("referent@test.com");
        referent.setFirstName("Referent");
        referent.setLastName("User");
        referent.setPasswordHash("test");
        referent.persist();


        // Create common test data
        veranstaltung = new Veranstaltung();
        veranstaltung.setName("Test Event " + System.currentTimeMillis());
        veranstaltung.setBeginntAm(LocalDateTime.now());
        veranstaltung.setEndetAm(LocalDateTime.now().plusDays(1));
        veranstaltung.setLogo("logo.png");
        veranstaltung.setLogo_link("http://example.com");
        veranstaltung.persist();

        admin.addVeranstaltung(veranstaltung);

        slot1 = new Slot();
        slot1.setDescription("Slot 1");
        slot1.setStartTime(veranstaltung.getBeginntAm().plusHours(1));
        slot1.setEndTime(veranstaltung.getBeginntAm().plusHours(2));
        adminService.createEventSlot(slot1, veranstaltung.getId());

        slot2 = new Slot();
        slot2.setDescription("Slot 2");
        slot2.setStartTime(veranstaltung.getBeginntAm().plusHours(3));
        slot2.setEndTime(veranstaltung.getBeginntAm().plusHours(4));
        adminService.createEventSlot(slot2, veranstaltung.getId());

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
    @TestTransaction
    void testAddTeilnehmerToVeranstaltung() {
        // 1. Add participant to event
        adminService.inviteUserToEvent(teilnehmer.getId(), veranstaltung.getId());

        // 2. Assert that availabilities are created
        NutzerVerfuegbarkeit verfuegbarkeit = NutzerVerfuegbarkeit.find("nutzerId = ?1 and veranstaltungId = ?2",
                teilnehmer.getId(), veranstaltung.getId()).firstResult();
        assertNotNull(verfuegbarkeit);
        Set<Long> vSlots = veranstaltung.getSlots().stream().map(IdEntity::getId).collect(Collectors.toSet());
        assertEquals(vSlots, verfuegbarkeit.getVerfuegbareSlotIds(), "All slots should be available.");
    }

    @Test
    @TestTransaction
    void testRemoveTeilnehmerFromVeranstaltung() {
        // 1. Add participant and verify initial state
        adminService.inviteUserToEvent(teilnehmer.getId(), veranstaltung.getId());
        assertThat(NutzerVerfuegbarkeit.count("nutzerId", teilnehmer.getId())).isEqualTo(1);

        // 2. Remove participant from event
        NutzerDto dto = AdminResource.mapNutzerToDto(teilnehmer);
        adminService.updateUser(teilnehmer.getId(), dto, List.of()); // Pass empty list of events

        // 3. Assert that availabilities are gone
        long count = NutzerVerfuegbarkeit.count("nutzerId = ?1 and veranstaltungId = ?2", teilnehmer.getId(), veranstaltung.getId());
        assertEquals(0, count, "Availabilities for the event should be removed.");
    }

    @Test
    @TestTransaction
    void testAddEventSlotToVeranstaltung() {
        // 1. Add participant to event
        adminService.inviteUserToEvent(teilnehmer.getId(), veranstaltung.getId());
        assertThat(NutzerVerfuegbarkeit.count("nutzerId", teilnehmer.getId())).isEqualTo(1);

        // 2. Add a new slot to the event
        Slot slot3 = new Slot();
        slot3.setDescription("Slot 3");
        slot3.setStartTime(veranstaltung.getBeginntAm().plusHours(5));
        slot3.setEndTime(veranstaltung.getBeginntAm().plusHours(6));
        adminService.createEventSlot(slot3, veranstaltung.getId());

        // 3. Assert that the participant has a new availability
        NutzerVerfuegbarkeit neueNutzerVerfuegbarkeit =
                NutzerVerfuegbarkeit.find("nutzerId = ?1 and veranstaltungId = ?2",
                        teilnehmer.getId(), veranstaltung.getId()).firstResult();
        assertNotNull(neueNutzerVerfuegbarkeit, "Availability for the new slot should exist.");
        assertThat(neueNutzerVerfuegbarkeit.getVerfuegbareSlotIds())
                .describedAs("New availability should be present.")
                .contains(slot3.getId());
    }

    @Test
    @TestTransaction
    void testRemoveEventSlotFromVeranstaltung() {
        // 1. Add participant to event
        adminService.inviteUserToEvent(teilnehmer.getId(), veranstaltung.getId());
        assertThat(NutzerVerfuegbarkeit.count("nutzerId", teilnehmer.getId())).isEqualTo(1);

        // 2. Remove a slot from the event
        adminService.deleteEventSlot(slot2.getId(), veranstaltung.getId());

        // 3. Assert that the participant's availability for that slot is removed
        NutzerVerfuegbarkeit nv = NutzerVerfuegbarkeit.find("nutzerId = ?1 and veranstaltungId = ?2",
                teilnehmer.getId(), veranstaltung.getId()).firstResult();
        assertThat(nv.getVerfuegbareSlotIds()).doesNotContain(slot2.getId());
    }

    @Test
    @TestTransaction
    void testAssignTeilnehmerToGruppeWithPflichtvortrag() {
        // 1. Setup: Add participant to event, create mandatory lecture
        adminService.inviteUserToEvent(teilnehmer.getId(), veranstaltung.getId());

        Raum r = new Raum();
        r.setName("Test Raum");
        r.setKapazitaet(30);
        r.persistAndFlush();

        Gebaeude g = new Gebaeude();
        g.setName("Test Gebaeude");
        g.setStrasse("Testweg");
        g.setHausnummer("1");
        g.setPostleitzahl("12345");
        g.setOrt("Teststadt");
        g.setTyp(Gebaeudetyp.SCHULE);
        g.addRaum(r);

        g.persist();

        Pflichtvortrag pv = new Pflichtvortrag();
        pv.setTitel("Mandatory Talk");
        pv.setVeranstaltung(veranstaltung);
        pv.setPflichtgruppe("GroupA");
        pv.setPflichtslot(slot1);
        pv.setPflichtraum(r);
        pv.setReferent(referent); // Assign mandatory referent
        pv.persist();

        // 2. Assign participant to the group
        NutzerDto dto = AdminResource.mapNutzerToDto(Nutzer.findById(teilnehmer.getId()));
        dto.gruppe = "GroupA";
        adminService.updateUser(teilnehmer.getId(), dto, List.of(veranstaltung.getId()));

        // 3. Assert that the availability for the mandatory slot is now false
        NutzerVerfuegbarkeit nv = NutzerVerfuegbarkeit.find("nutzerId = ?1 and veranstaltungId = ?2",
                        teilnehmer.getId(), veranstaltung.getId())
                .firstResult();

        assertNotNull(nv);
        assertFalse(nv.getVerfuegbareSlotIds().contains(slot1.getId()), "Availability for mandatory slot1 should be false.");

        assertTrue(nv.getVerfuegbareSlotIds().contains(slot2.getId()), "Availability for other slot2 should remain true.");
    }

    @Test
    @TestTransaction
    void testRemoveTeilnehmerFromGruppeWithPflichtvortrag() {
        // 1. Setup: Participant is in a group with a mandatory lecture
        adminService.inviteUserToEvent(teilnehmer.getId(), veranstaltung.getId());
        Gebaeude g = new Gebaeude();
        g.setName("Test Gebaeude");
        g.setStrasse("Testweg");
        g.setHausnummer("1");
        g.setPostleitzahl("12345");
        g.setOrt("Teststadt");
        g.setTyp(Gebaeudetyp.SCHULE);
        g.persist();

        Raum r = new Raum();
        r.setName("Test Raum");
        r.setKapazitaet(30);
        r.persist();
        g.addRaum(r);

        Pflichtvortrag pv = new Pflichtvortrag();
        pv.setTitel("Mandatory Talk");
        pv.setVeranstaltung(veranstaltung);
        pv.setPflichtgruppe("GroupA");
        pv.setPflichtslot(slot1);
        pv.setPflichtraum(r);
        pv.setReferent(referent); // Assign mandatory referent
        pv.persist();

        NutzerDto dto = AdminResource.mapNutzerToDto(teilnehmer);
        dto.gruppe = "GroupA";
        dto.version = teilnehmer.getVersion() + 1;
        dto = adminService.updateUser(teilnehmer.getId(), dto, List.of(veranstaltung.getId()));

        // Verify initial state
        NutzerVerfuegbarkeit nvBefore =
                NutzerVerfuegbarkeit.find("nutzerId = ?1 and veranstaltungId = ?2",
                        teilnehmer.getId(), veranstaltung.getId()).firstResult();
        assertFalse(nvBefore.getVerfuegbareSlotIds().contains(slot1.getId()),
                "Pre-condition: Availability should be false.");

        // 2. Remove participant from the group

        dto.gruppe = "GroupB"; // or null
        adminService.updateUser(teilnehmer.getId(), dto, List.of(veranstaltung.getId()));

        // 3. Assert that the availability is reset to true
        NutzerVerfuegbarkeit nvAfter =
                NutzerVerfuegbarkeit.find("nutzerId = ?1 and veranstaltungId = ?2",
                        teilnehmer.getId(), veranstaltung.getId()).firstResult();
        assertNotNull(nvAfter);
        assertTrue(nvAfter.getVerfuegbareSlotIds().contains(slot1.getId()),
                "Availability should be reset to true after leaving the group.");
    }
}