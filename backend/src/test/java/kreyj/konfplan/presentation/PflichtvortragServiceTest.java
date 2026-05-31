package kreyj.konfplan.presentation;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.h2.H2DatabaseTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import kreyj.konfplan.application.service.AdminService;
import kreyj.konfplan.application.service.TeilnehmerService;
import kreyj.konfplan.persistence.Admin;
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
import kreyj.konfplan.presentation.dto.VortragDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static kreyj.konfplan.persistence.NutzerVerfuegbarkeitId.nvId;
import static kreyj.konfplan.persistence.RaumVerfuegbarkeitId.rvId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@QuarkusTest
@TestSecurity(user = "admin@example.com", roles = "ADMIN")
@QuarkusTestResource(H2DatabaseTestResource.class)
class PflichtvortragServiceTest extends DatabaseCleaner {

    @Inject
    AdminService adminService;

    Veranstaltung veranstaltung;
    Gebaeude gebaeude;
    Raum raum1, raum2;
    Slot slot1, slot2;
    Referent referent;
    Teilnehmer teilnehmer1, teilnehmer2, teilnehmer3;
    @Inject
    TeilnehmerService teilnehmerService;

    @BeforeEach
    @Transactional
    void setup() {
        // Setup Admin for @TestSecurity
        Admin admin = new Admin();
        admin.setEmail("admin@example.com");
        admin.setPasswordHash("hash");
        admin.persistAndFlush();

        gebaeude = new Gebaeude();
        gebaeude.setName("Hauptgebäude");
        gebaeude.setTyp(Gebaeudetyp.SCHULE);
        gebaeude.setPostleitzahl("12345");
        gebaeude.setOrt("Testort");
        gebaeude.setStrasse("Teststraße");
        gebaeude.persistAndFlush();

        veranstaltung = new Veranstaltung();
        veranstaltung.setName("Test Event");
        veranstaltung.setBeginntAm(LocalDateTime.of(2024, 1, 1, 9, 0));
        veranstaltung.setEndetAm(LocalDateTime.of(2024, 1, 1, 17, 0));
        veranstaltung.addGebaeude(gebaeude);
        veranstaltung.persistAndFlush();


        raum1 = new Raum();
        raum1.setName("Raum 1");
        raum1.setKapazitaet(2);
        raum1.persistAndFlush();
        gebaeude.addRaum(raum1);

        raum2 = new Raum();
        raum2.setName("Raum 2");
        raum2.setKapazitaet(10);
        raum2.persistAndFlush();
        gebaeude.addRaum(raum2);

        slot1 = new Slot("Slot 1",
                LocalDateTime.of(2024, 1, 1, 9, 0),
                LocalDateTime.of(2024, 1, 1, 10, 0), veranstaltung);
        slot1.persistAndFlush();
        veranstaltung.addSlot(slot1);

        slot2 = new Slot("Slot 2",
                LocalDateTime.of(2024, 1, 1, 10, 0),
                LocalDateTime.of(2024, 1, 1, 11, 0), veranstaltung);
        slot2.persistAndFlush();
        veranstaltung.addSlot(slot2);

        referent = new Referent();
        referent.setEmail("ref@example.com");
        referent.setFirstName("Ref");
        referent.setLastName("Erent");
        referent.setPasswordHash("hash");
        referent.persistAndFlush();
        referent.addVeranstaltung(veranstaltung);

        teilnehmer1 = new Teilnehmer();
        teilnehmer1.setEmail("tn1@example.com");
        teilnehmer1.setFirstName("TN1");
        teilnehmer1.setLastName("GruppeA");
        teilnehmer1.setGruppe("Gruppe A");
        teilnehmer1.setActive(true);
        teilnehmer1.persistAndFlush();
        teilnehmer1.addVeranstaltung(veranstaltung);

        teilnehmer2 = new Teilnehmer();
        teilnehmer2.setEmail("tn2@example.com");
        teilnehmer2.setFirstName("TN2");
        teilnehmer2.setLastName("GruppeA");
        teilnehmer2.setGruppe("Gruppe A");
        teilnehmer2.setActive(true);
        teilnehmer2.persistAndFlush();
        teilnehmer2.addVeranstaltung(veranstaltung);

        teilnehmer3 = new Teilnehmer();
        teilnehmer3.setEmail("tn3@example.com");
        teilnehmer3.setFirstName("TN3");
        teilnehmer3.setLastName("GruppeB");
        teilnehmer3.setGruppe("Gruppe B");
        teilnehmer3.setActive(true);
        teilnehmer3.persistAndFlush();
        teilnehmer3.addVeranstaltung(veranstaltung);
    }

    @Test
    @Transactional
    void testCreatePflichtvortragSuccess() {
        // Raum 2 hat Kapazität 10, Gruppe A hat 2 TN
        VortragDto pvDTO = pvDTO("PV Test", referent, "Gruppe A", raum2, slot1, veranstaltung);
        Pflichtvortrag createdPv = (Pflichtvortrag) adminService.createVortrag(pvDTO);

        assertNotNull(createdPv.getId());
        assertEquals("PV Test", createdPv.getTitel());
        assertThat(isTeilnehmerAvailable(teilnehmer1, veranstaltung, slot1)).isFalse();
        assertThat(isTeilnehmerAvailable(teilnehmer2, veranstaltung, slot1)).isFalse();
        assertThat(isTeilnehmerAvailable(teilnehmer3, veranstaltung, slot1)).isTrue(); // TN3 not in Gruppe A
        assertThat(isRaumAvailable(raum2, veranstaltung, slot1)).isFalse();
        assertThat(isRaumAvailable(raum1, veranstaltung, slot1)).isTrue(); // Raum 1 not used
    }

    private static VortragDto pvDTO(String titel, Referent referent, String gruppe, Raum raum, Slot slot,
                                    Veranstaltung veranstaltung) {
        return new VortragDto(titel, referent.getId(), gruppe, raum.getId(), slot.getId(), veranstaltung.getId());
    }

    @Test
    @Transactional
    void testCreatePflichtvortragRaumBelegtFails() {
        // Manually block raum2, slot1 in a committed transaction
        new RaumVerfuegbarkeit(raum2, veranstaltung, List.of(slot1.getId())).persistAndFlush();

        VortragDto pvDTO = pvDTO("PV Test", referent, "Gruppe A", raum2, slot1, veranstaltung);

        assertThrows(IllegalArgumentException.class, () -> adminService.createVortrag(pvDTO));

        assertThat(Pflichtvortrag.count()).isZero(); // No PV created
    }

    @Test
    @Transactional
    void testCreatePflichtvortragTeilnehmerNichtVerfuegbarFails() {
        // Manually make teilnehmer1 unavailable for slot1 in a committed transaction
        NutzerVerfuegbarkeit nv = NutzerVerfuegbarkeit.findById(nvId(teilnehmer1, veranstaltung));
        nv.getVerfuegbareSlotIds().remove(slot1.getId());
        nv.persistAndFlush();

        VortragDto pvDTO = new VortragDto("PV Test", referent, "Gruppe A", raum2, slot1, veranstaltung);

        assertThrows(IllegalArgumentException.class, () -> adminService.createVortrag(pvDTO));
        assertThat(0L).isEqualTo(Pflichtvortrag.count()); // No PV created
    }

    @Test
    @Transactional
    void testCreatePflichtvortragRaumKapazitaetFails() {
        VortragDto pv = new VortragDto("PV Test", referent, "Gruppe A", raum1, slot1, veranstaltung);

        Raum r = Raum.findById(raum1.getId());
        r.setKapazitaet(1);
        r.persistAndFlush();

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () -> {
            adminService.createVortrag(pv);
        });
        assertThat(thrown.getMessage().contains("Raumkapazität von 'Raum 1' reicht für die Gruppe 'Gruppe A' nicht aus.")).isTrue();
        assertThat(Pflichtvortrag.count()).isEqualTo(0L);
    }

    @Test
    @Transactional
    void testCreatePflichtvortragFailsIfRaumAlreadyOccupiedByAnotherPflichtvortrag() {
        // Create first PV
        VortragDto pv1 = new VortragDto("PV1", referent, "Gruppe A", raum2, slot1, veranstaltung);

        adminService.createVortrag(pv1);

        // Attempt to create a second PV using the same room and slot
        VortragDto pv2 = new VortragDto("PV2", referent, "Gruppe B", raum2, slot1, veranstaltung);

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () -> {
            adminService.createVortrag(pv2);
        });
        assertThat(thrown.getMessage().contains("Raum 'Raum 2' ist im Slot 'Slot 1' bereits belegt.")).isTrue();
        assertThat(Pflichtvortrag.count()).isEqualTo(1L); // Only PV1 created
    }

    @Test
    @Transactional
    void testCreatePflichtvortragFailsIfGruppeAlreadyOccupiedByAnotherPflichtvortrag() {
        // Create first PV
        VortragDto pv1 = new VortragDto("PV1", referent, "Gruppe A", raum2, slot1, veranstaltung);

        adminService.createVortrag(pv1);

        // Attempt to create a second PV using the same group and slot
        VortragDto pv2 = new VortragDto("PV2", referent, "Gruppe A", raum1, slot1, veranstaltung);

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () -> {
            adminService.createVortrag(pv2);
        });
        assertThat(thrown.getMessage().contains("Nicht alle Teilnehmer der Gruppe 'Gruppe A' sind im Slot 'Slot 1' verfügbar.")).isTrue();
        assertThat(Pflichtvortrag.count()).isEqualTo(1L); // Only PV1 created
    }

    @Test
    @Transactional
    void testUpdatePflichtvortragChangeSlotSuccess() {
        // Create initial PV
        VortragDto pv = new VortragDto("PV Initial", referent, "Gruppe A", raum2, slot1, veranstaltung);
        Pflichtvortrag initialPv = (Pflichtvortrag) adminService.createVortrag(pv);

        // Verify initial state

        assertThat(isTeilnehmerAvailable(teilnehmer1, veranstaltung, slot1)).isFalse();
        assertThat(isRaumAvailable(raum2, veranstaltung, slot1)).isFalse();
        assertThat(isTeilnehmerAvailable(teilnehmer1, veranstaltung, slot2)).isTrue();
        assertThat(isRaumAvailable(raum2, veranstaltung, slot2)).isTrue();

        // Update PV to change slot to slot2
        VortragDto updatedPvDto = new VortragDto();
        updatedPvDto.istPflicht = true;
        updatedPvDto.titel = "PV Updated Slot";
        updatedPvDto.referentId = referent.getId();
        updatedPvDto.pflichtGruppe = "Gruppe A";
        updatedPvDto.pflichtRaumId = raum2.getId();
        updatedPvDto.pflichtSlotId = slot2.getId();
        updatedPvDto.version = 0L;

        adminService.updateVortrag(veranstaltung.getId(), initialPv.getId(), updatedPvDto);

        // Verify new state
        assertThat(isTeilnehmerAvailable(teilnehmer1, veranstaltung, slot1)).isTrue(); // Old slot freed
        assertThat(isRaumAvailable(raum2, veranstaltung, slot1)).isTrue(); // Old room-slot freed
        assertThat(isTeilnehmerAvailable(teilnehmer1, veranstaltung, slot2)).isFalse(); // New slot occupied
        assertThat(isRaumAvailable(raum2, veranstaltung, slot2)).isFalse(); // New room-slot occupied
    }

    @Test
    @Transactional
    void testUpdatePflichtvortragChangeSlotFailsIfNewSlotNotAvailable() {
        // Create initial PV
        VortragDto pvDto = new VortragDto("PV Initial", referent, "Gruppe A", raum2, slot1, veranstaltung);
        Pflichtvortrag initialPv = (Pflichtvortrag) adminService.createVortrag(pvDto);

        // Manually block slot2 for teilnehmer1 in a committed transaction
        new NutzerVerfuegbarkeit(teilnehmer1, veranstaltung, Collections.emptyList()).persistAndFlush();

        // Attempt to update PV to change slot to slot2
        VortragDto updatedPv = new VortragDto("PV Updated Slot", referent, "Gruppe A", raum2, slot2, veranstaltung);

        updatedPv.version = 0L;

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () -> {
            adminService.updateVortrag(veranstaltung.getId(), initialPv.getId(), updatedPv);
        });
        assertThat(thrown.getMessage().contains("Nicht alle Teilnehmer der Gruppe 'Gruppe A' sind im neuen Slot 'Slot 2' verfügbar.")).isTrue();

        // Verify state remains unchanged
        assertThat(isTeilnehmerAvailable(teilnehmer1, veranstaltung, slot1)).isFalse();
        assertThat(isRaumAvailable(raum2, veranstaltung, slot1)).isFalse();
        assertThat(isTeilnehmerAvailable(teilnehmer1, veranstaltung, slot2)).isFalse(); // Still unavailable due to manual block
        assertThat(isRaumAvailable(raum2, veranstaltung, slot2)).isTrue(); // Room was not blocked by PV update
    }

    @Test
    @Transactional
    void testUpdatePflichtvortragChangeRaumSuccess() {
        // Create initial PV
        VortragDto pv = new VortragDto("PV Initial", referent, "Gruppe A", raum1, slot1, veranstaltung);
        Pflichtvortrag initialPv = (Pflichtvortrag) adminService.createVortrag(pv);

        // Verify initial state
        assertThat(isRaumAvailable(raum1, veranstaltung, slot1)).isFalse();
        assertThat(isRaumAvailable(raum2, veranstaltung, slot1)).isTrue();

        // Update PV to change room to raum2
        VortragDto updatedPv = new VortragDto("PV Updated Raum", referent, "Gruppe A", raum2, slot1, veranstaltung);
        updatedPv.version = 0L;

        adminService.updateVortrag(veranstaltung.getId(), initialPv.getId(), updatedPv);

        // Verify new state
        assertThat(isRaumAvailable(raum1, veranstaltung, slot1)).isTrue(); // Old room freed
        assertThat(isRaumAvailable(raum2, veranstaltung, slot1)).isFalse(); // New room occupied
        assertThat(isTeilnehmerAvailable(teilnehmer1, veranstaltung, slot1)).isFalse(); // Teilnehmer availability unchanged
    }

    @Test
    @Transactional
    void testUpdatePflichtvortragChangeRaumFailsIfNewRaumBelegt() {
        // Create initial PV
        VortragDto pv = new VortragDto("PV Initial", referent, "Gruppe A", raum1, slot1, veranstaltung);
        Pflichtvortrag initialPv = (Pflichtvortrag) adminService.createVortrag(pv);

        // Manually block raum2, slot1 in a committed transaction
        new RaumVerfuegbarkeit(raum2, veranstaltung, List.of(slot1.getId())).persistAndFlush();


        // Attempt to update PV to change room to raum2
        VortragDto updatedPv = new VortragDto("PV Updated Raum", referent, "Gruppe A", raum2, slot1, veranstaltung);
        updatedPv.version = 0L;

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () -> {
            adminService.updateVortrag(veranstaltung.getId(), initialPv.getId(), updatedPv);
        });
        assertThat(thrown.getMessage().contains("Neuer Raum 'Raum 2' ist im Slot 'Slot 1' bereits belegt.")).isTrue();

        // Verify state remains unchanged
        assertThat(isRaumAvailable(raum1, veranstaltung, slot1)).isFalse(); // Raum1 still occupied by PV
        assertThat(isRaumAvailable(raum2, veranstaltung, slot1)).isFalse(); // Raum2 still occupied by manual block
    }

    @Test
    @Transactional
    void testUpdatePflichtvortragChangeRaumFailsIfNewRaumOccupiedByAnotherPflichtvortrag() {
        // Create PV1
        VortragDto pv1 = new VortragDto("PV1", referent, "Gruppe A", raum1, slot1, veranstaltung);
        Pflichtvortrag createdPv1 = (Pflichtvortrag) adminService.createVortrag(pv1);

        // Create PV2 that occupies raum2, slot1
        VortragDto pv2 = new VortragDto("PV2", referent, "Gruppe B", raum2, slot1, veranstaltung);
        adminService.createVortrag(pv2);

        // Attempt to update PV1 to use raum2 (which is occupied by PV2)
        VortragDto updatedPv1 = new VortragDto("PV1 Updated Raum", referent, "Gruppe A", raum2, slot1, veranstaltung);
        updatedPv1.version = 0L;

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () -> {
            adminService.updateVortrag(veranstaltung.getId(), createdPv1.getId(), updatedPv1);
        });
        assertThat(thrown.getMessage().contains("Neuer Raum 'Raum 2' ist im Slot 'Slot 1' bereits belegt.")).isTrue();

        // Verify state remains unchanged for PV1
        assertThat(isRaumAvailable(raum1, veranstaltung, slot1)).isFalse(); // raum1 still occupied by PV1
        assertThat(isRaumAvailable(raum2, veranstaltung, slot1)).isFalse(); // raum2 still occupied by PV2
    }

    @Test
    @Transactional
    void testUpdatePflichtvortragChangeGruppeSuccess() {
        // Create initial PV for Gruppe A
        VortragDto pv = new VortragDto("PV Initial", referent, "Gruppe A", raum2, slot1, veranstaltung);
        Pflichtvortrag initialPv = (Pflichtvortrag) adminService.createVortrag(pv);

        // Verify initial state
        assertThat(isTeilnehmerAvailable(teilnehmer1, veranstaltung, slot1)).isFalse(); // Gruppe A
        assertThat(isTeilnehmerAvailable(teilnehmer2, veranstaltung, slot1)).isFalse(); // Gruppe A
        assertThat(isTeilnehmerAvailable(teilnehmer3, veranstaltung, slot1)).isTrue(); // Gruppe B

        // Update PV to change group to Gruppe B
        VortragDto updatedPv = new VortragDto("PV Updated Gruppe", referent, "Gruppe B", raum2, slot1, veranstaltung);
        updatedPv.version = 0L;

        adminService.updateVortrag(veranstaltung.getId(), initialPv.getId(), updatedPv);

        // Verify new state
        assertThat(isTeilnehmerAvailable(teilnehmer1, veranstaltung, slot1)).isTrue(); // Old group freed
        assertThat(isTeilnehmerAvailable(teilnehmer2, veranstaltung, slot1)).isTrue(); // Old group freed
        assertThat(isTeilnehmerAvailable(teilnehmer3, veranstaltung, slot1)).isFalse(); // New group occupied
        assertThat(isRaumAvailable(raum2, veranstaltung, slot1)).isFalse(); // Room availability unchanged
    }

    @Test
    @Transactional
    void testUpdatePflichtvortragChangeGruppeFailsIfNewGruppeNotAvailable() {
        // Create initial PV for Gruppe A
        VortragDto pv = new VortragDto("PV Initial", referent, "Gruppe A", raum2, slot1, veranstaltung);
        Pflichtvortrag initialPv = (Pflichtvortrag) adminService.createVortrag(pv);

        // Manually make teilnehmer3 (Gruppe B) unavailable for slot1 in a committed transaction
        new NutzerVerfuegbarkeit(teilnehmer3, veranstaltung, Collections.emptyList()).persistAndFlush();

        // Attempt to update PV to change group to Gruppe B
        VortragDto updatedPv = new VortragDto("PV Updated Gruppe", referent, "Gruppe B", raum2, slot1, veranstaltung);
        updatedPv.version = 0L;

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () -> {
            adminService.updateVortrag(veranstaltung.getId(), initialPv.getId(), updatedPv);
        });
        assertThat(thrown.getMessage().contains("Nicht alle Teilnehmer der neuen Gruppe 'Gruppe B' sind im Slot 'Slot 1' verfügbar.")).isTrue();

        // Verify state remains unchanged
        assertThat(isTeilnehmerAvailable(teilnehmer1, veranstaltung, slot1)).isFalse(); // Gruppe A still occupied by PV
        assertThat(isTeilnehmerAvailable(teilnehmer3, veranstaltung, slot1)).isFalse(); // Gruppe B still unavailable by manual block
    }

    @Test
    @Transactional
    void testUpdatePflichtvortragChangeGruppeFailsIfNewGruppeOccupiedByAnotherPflichtvortrag() {
        // Create PV1 for Gruppe A, Slot 1
        VortragDto pv1 = new VortragDto("PV1", referent, "Gruppe A", raum2, slot1, veranstaltung);
        Pflichtvortrag createdPv1 = (Pflichtvortrag) adminService.createVortrag(pv1);

        // Create PV2 for Gruppe B, Slot 1
        VortragDto pv2 = new VortragDto("PV2", referent, "Gruppe B", raum1, slot1, veranstaltung);
        adminService.createVortrag(pv2);

        // Attempt to update PV1 to use Gruppe B (which is occupied by PV2)
        VortragDto updatedPv1 = new VortragDto("PV1 Updated Gruppe", referent, "Gruppe B", raum2, slot1, veranstaltung);
        updatedPv1.version = 0L;

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () -> {
            adminService.updateVortrag(veranstaltung.getId(), createdPv1.getId(), updatedPv1);
        });
        assertThat(thrown.getMessage().contains("Nicht alle Teilnehmer der neuen Gruppe 'Gruppe B' sind im Slot 'Slot 1' verfügbar.")).isTrue();

        // Verify state remains unchanged for PV1
        assertThat(isTeilnehmerAvailable(teilnehmer1, veranstaltung, slot1)).isFalse(); // Gruppe A still occupied by PV1
        assertThat(isTeilnehmerAvailable(teilnehmer3, veranstaltung, slot1)).isFalse(); // Gruppe B still occupied by PV2
    }

    @Test
    @Transactional
    void testDeletePflichtvortragSuccess() {
        // Create initial PV
        VortragDto pv = new VortragDto("PV Test", referent, "Gruppe A", raum2, slot1, veranstaltung);
        Pflichtvortrag createdPv = (Pflichtvortrag) adminService.createVortrag(pv);
        Long createdId = createdPv.getId();

        // Verify initial state
        assertThat(isTeilnehmerAvailable(teilnehmer1, veranstaltung, slot1)).isFalse();
        assertThat(isRaumAvailable(raum2, veranstaltung, slot1)).isFalse();

        // Delete PV
        adminService.deleteVortrag(createdId, veranstaltung.getId());

        // Verify new state
        assertThat(isTeilnehmerAvailable(teilnehmer1, veranstaltung, slot1)).isTrue(); // Freed
        assertThat(isRaumAvailable(raum2, veranstaltung, slot1)).isTrue(); // Freed

        assertThat(Pflichtvortrag.<Pflichtvortrag>findById(createdId)).isNull(); // PV deleted
    }

    // -------------------------------------------------------------------
    // Helper methods to check availability
    // -------------------------------------------------------------------

    // Helper to check availability - requiring surrounding transaction

    private boolean isTeilnehmerAvailable(Teilnehmer tn, Veranstaltung veranstaltung, Slot slot) {
        NutzerVerfuegbarkeit nv = NutzerVerfuegbarkeit.findById(nvId(tn, veranstaltung));

        return nv.getVerfuegbareSlotIds().contains(slot.getId());
    }


    // Helper to check room availability - now runs in its own transaction
    private boolean isRaumAvailable(Raum raum, Veranstaltung veranstaltung, Slot slot) {
        RaumVerfuegbarkeit rv = RaumVerfuegbarkeit.findById(rvId(raum, veranstaltung));

        return rv.getVerfuegbareSlotIds().contains(slot.getId());
    }
}
