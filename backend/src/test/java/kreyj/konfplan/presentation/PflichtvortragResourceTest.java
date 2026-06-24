package kreyj.konfplan.presentation;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.common.http.TestHTTPEndpoint;
import io.quarkus.test.h2.H2DatabaseTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.core.MediaType;
import kreyj.konfplan.adapter.in.web.VeranstaltungResource;
import kreyj.konfplan.persistence.Admin;
import kreyj.konfplan.persistence.Gebaeude;
import kreyj.konfplan.persistence.Gebaeudetyp;
import kreyj.konfplan.persistence.Pflichtvortrag;
import kreyj.konfplan.persistence.Raum;
import kreyj.konfplan.persistence.Referent;
import kreyj.konfplan.persistence.Slot;
import kreyj.konfplan.persistence.Teilnehmer;
import kreyj.konfplan.persistence.Veranstaltung;
import kreyj.konfplan.adapter.in.web.dto.VortragDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Set;

import static io.restassured.RestAssured.given;
import static jakarta.ws.rs.core.Response.Status.BAD_REQUEST;
import static jakarta.ws.rs.core.Response.Status.CREATED;
import static jakarta.ws.rs.core.Response.Status.NO_CONTENT;
import static jakarta.ws.rs.core.Response.Status.OK;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@QuarkusTest
@TestSecurity(user = "admin@example.com", roles = "ADMIN")
@QuarkusTestResource(H2DatabaseTestResource.class)
@TestHTTPEndpoint(VeranstaltungResource.class)
class PflichtvortragResourceTest extends DatabaseCleaner {
    Veranstaltung veranstaltung;
    Gebaeude gebaeude;
    Raum raum1, raum2;
    Slot slot1, slot2;
    Referent referent;
    Teilnehmer teilnehmer1, teilnehmer2, teilnehmer3;


    @BeforeEach
    @Transactional
    void setup() {
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
        veranstaltung.addGruppe("Gruppe A");
        veranstaltung.addGruppe("Gruppe B");
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

        slot1 = new Slot("Slot 1",
                LocalDateTime.of(2024, 1, 1, 9, 0),
                LocalDateTime.of(2024, 1, 1, 10, 0), veranstaltung);
        slot1.persist();
        veranstaltung.addSlot(slot1);

        slot2 = new Slot("Slot 2",
                LocalDateTime.of(2024, 1, 1, 10, 0),
                LocalDateTime.of(2024, 1, 1, 11, 0), veranstaltung);
        slot2.persist();
        veranstaltung.addSlot(slot2);

        referent = new Referent();
        referent.setEmail("ref@example.com");
        referent.setFirstName("Ref");
        referent.setLastName("Erent");
        referent.setPasswordHash("hash");
        referent.persist();
        referent.addVeranstaltung(veranstaltung);

        teilnehmer1 = new Teilnehmer();
        teilnehmer1.setEmail("tn1@example.com");
        teilnehmer1.setFirstName("TN1");
        teilnehmer1.setLastName("GruppeA");
        teilnehmer1.setGruppen(Set.of("Gruppe A"));
        teilnehmer1.persist();
        teilnehmer1.addVeranstaltung(veranstaltung);

        teilnehmer2 = new Teilnehmer();
        teilnehmer2.setEmail("tn2@example.com");
        teilnehmer2.setFirstName("TN2");
        teilnehmer2.setLastName("GruppeA");
        teilnehmer2.setGruppen(Set.of("Gruppe A"));
        teilnehmer2.persist();
        teilnehmer2.addVeranstaltung(veranstaltung);

        teilnehmer3 = new Teilnehmer();
        teilnehmer3.setEmail("tn3@example.com");
        teilnehmer3.setFirstName("TN3");
        teilnehmer3.setLastName("GruppeB");
        teilnehmer3.setGruppen(Set.of("Gruppe B"));
        teilnehmer3.persist();
        teilnehmer3.addVeranstaltung(veranstaltung);
    }


    @Test
    void testCreatePflichtvortragSuccess() {
        // Raum 2 hat Kapazität 10, Gruppe A hat 2 TN
        VortragDto pvDTO = pvDto("PV Test", referent, "Gruppe A", raum2, slot1, veranstaltung);

        VortragDto createdPv =
                given().contentType(MediaType.APPLICATION_JSON)
                        .body(pvDTO)
                        .when()
                        .post("{vid}/vortraege", veranstaltung.getId())
                        .then()
                        .statusCode(CREATED.getStatusCode())
                        .extract()
                        .as(VortragDto.class);

        assertNotNull(createdPv.id);
        assertEquals("PV Test", createdPv.titel);
        assertThat(isTeilnehmerVerfuegbar(teilnehmer1, slot1, veranstaltung)).isFalse();
        assertThat(isTeilnehmerVerfuegbar(teilnehmer2, slot1, veranstaltung)).isFalse();
        assertThat(isTeilnehmerVerfuegbar(teilnehmer3, slot1, veranstaltung)).isTrue(); // TN3 not in Gruppe A
        assertThat(isRaumVerfuegbar(raum2, slot1, veranstaltung)).isFalse();
        assertThat(isRaumVerfuegbar(raum1, slot1, veranstaltung)).isTrue(); // Raum 1 not used
    }


    @Test
    void testCreatePflichtvortragRaumBelegtFails() {
        QuarkusTransaction.requiringNew().run(() -> raum2.updateRaumVerfuegbarkeit(slot1, veranstaltung, false, false));

        VortragDto pvDTO = pvDto("PV Test", referent, "Gruppe A", raum2, slot1, veranstaltung);

        given().contentType(MediaType.APPLICATION_JSON)
                .body(pvDTO)
                .when()
                .post("{vid}/vortraege", veranstaltung.getId())
                .then()
                .log().all()
                .statusCode(BAD_REQUEST.getStatusCode())
                .body("error", startsWith("Raum 'Raum 2' ist im Slot 'Slot 1' bereits belegt."));

        assertThat(Pflichtvortrag.count()).isZero(); // No PV created
    }


    @Test
    void testCreatePflichtvortragTeilnehmerNichtVerfuegbarFails() {
        QuarkusTransaction.requiringNew().run(() -> teilnehmer1.updateVerfuegbarkeit(slot1, veranstaltung, false, false));
        VortragDto pvDTO = pvDto("PV Test", referent, "Gruppe A", raum2, slot1, veranstaltung);

        given().contentType(MediaType.APPLICATION_JSON)
                .body(pvDTO)
                .when()
                .post("{vid}/vortraege", veranstaltung.getId())
                .then()
                .log().all()
                .statusCode(BAD_REQUEST.getStatusCode())
                .body("error", startsWith("Teilnehmer der Gruppe 'Gruppe A' sind im Slot 'Slot 1'"));

        assertThat(Pflichtvortrag.count()).isZero(); // No PV created
    }


    @Test
    void testCreatePflichtvortragRaumKapazitaetFails() {
        QuarkusTransaction.requiringNew().run(() -> Raum.<Raum>findById(raum1.getId()).setKapazitaet(1));
        VortragDto pvDto = pvDto("PV Test", referent, "Gruppe A", raum1, slot1, veranstaltung);

        given().contentType(MediaType.APPLICATION_JSON)
                .body(pvDto)
                .when()
                .post("{vid}/vortraege", veranstaltung.getId())
                .then()
                .log().all()
                .statusCode(BAD_REQUEST.getStatusCode())
                .body("error", startsWith("Raumkapazität von 'Raum 1' reicht für die Gruppe 'Gruppe A' nicht aus."));

        assertThat(Pflichtvortrag.count()).isEqualTo(0L);
    }


    @Test
    void testCreatePflichtvortragFailsIfRaumAlreadyOccupiedByAnotherPflichtvortrag() {
        VortragDto pv1 = pvDto("PV1", referent, "Gruppe A", raum2, slot1, veranstaltung);

        given().contentType(MediaType.APPLICATION_JSON)
                .body(pv1)
                .when()
                .post("{vid}/vortraege", veranstaltung.getId())
                .then()
                .statusCode(CREATED.getStatusCode());

        // Attempt to create a second PV using the same room and slot
        VortragDto pv2 = pvDto("PV2", referent, "Gruppe B", raum2, slot1, veranstaltung);

        given().contentType(MediaType.APPLICATION_JSON)
                .body(pv2)
                .when()
                .post("{vid}/vortraege", veranstaltung.getId())
                .then()
                .log().all()
                .statusCode(BAD_REQUEST.getStatusCode())
                .body("error", startsWith("Raum 'Raum 2' ist im Slot 'Slot 1' bereits belegt."));

        assertThat(Pflichtvortrag.count())
                .describedAs("nur PV1 erzeugt")
                .isEqualTo(1L);
    }


    @Test
    void testCreatePflichtvortragFailsIfGruppeAlreadyOccupiedByAnotherPflichtvortrag() {
        VortragDto pv1 = pvDto("PV1", referent, "Gruppe A", raum2, slot1, veranstaltung);

        given().contentType(MediaType.APPLICATION_JSON)
                .body(pv1)
                .when()
                .post("{vid}/vortraege", veranstaltung.getId())
                .then()
                .statusCode(CREATED.getStatusCode());

        // Attempt to create a second PV using the same group and slot
        VortragDto pv2 = pvDto("PV2", referent, "Gruppe A", raum1, slot1, veranstaltung);

        given().contentType(MediaType.APPLICATION_JSON)
                .body(pv2)
                .when()
                .post("{vid}/vortraege", veranstaltung.getId())
                .then()
                .log().all()
                .statusCode(BAD_REQUEST.getStatusCode())
                .body("error", startsWith("Teilnehmer der Gruppe 'Gruppe A' sind im Slot 'Slot 1'"));

        assertThat(Pflichtvortrag.count())
                .describedAs("nur PV1 erzeugt")
                .isEqualTo(1L);
    }


    @Test
    void testUpdatePflichtvortragChangeSlotSuccess() {
        // Create initial PV
        VortragDto pvDto = pvDto("PV Initial", referent, "Gruppe A", raum2, slot1, veranstaltung);
        VortragDto initialPv =
                given().contentType(MediaType.APPLICATION_JSON)
                        .body(pvDto)
                        .when()
                        .post("{vid}/vortraege", veranstaltung.getId())
                        .then()
                        .statusCode(CREATED.getStatusCode())
                        .extract()
                        .as(VortragDto.class);

        // Verify initial state
        assertThat(isTeilnehmerVerfuegbar(teilnehmer1, slot1, veranstaltung)).isFalse();
        assertThat(isRaumVerfuegbar(raum2, slot1, veranstaltung)).isFalse();
        assertThat(isTeilnehmerVerfuegbar(teilnehmer1, slot2, veranstaltung)).isTrue();
        assertThat(isRaumVerfuegbar(raum2, slot2, veranstaltung)).isTrue();

        // Update PV to change slot1 to slot2, same room
        VortragDto updatedPvDto = pvDto("PV Updated Slot", referent, "Gruppe A", raum2, slot2, veranstaltung);
        updatedPvDto.version = 0L;

        given().contentType(MediaType.APPLICATION_JSON)
                .body(updatedPvDto)
                .when()
                .put("{vid}/vortraege/{vortragId}", veranstaltung.getId(), initialPv.id)
                .then()
                .statusCode(OK.getStatusCode())
                .extract()
                .as(VortragDto.class);

        // Verify new state
        assertThat(isTeilnehmerVerfuegbar(teilnehmer1, slot1, veranstaltung)).isTrue(); // Old slot freed
        assertThat(isRaumVerfuegbar(raum2, slot1, veranstaltung)).isTrue(); // Old room-slot freed
        assertThat(isTeilnehmerVerfuegbar(teilnehmer1, slot2, veranstaltung)).isFalse(); // New slot occupied
        assertThat(isRaumVerfuegbar(raum2, slot2, veranstaltung)).isFalse(); // New room-slot occupied
    }


    @Test
    void testUpdatePflichtvortragChangeSlotFailsIfNewSlotNotAvailable() {
        VortragDto pvDto = pvDto("PV Initial", referent, "Gruppe A", raum2, slot1, veranstaltung);
        VortragDto initialPv =
                given().contentType(MediaType.APPLICATION_JSON)
                        .body(pvDto)
                        .when()
                        .post("{vid}/vortraege", veranstaltung.getId())
                        .then()
                        .statusCode(CREATED.getStatusCode())
                        .extract()
                        .as(VortragDto.class);

        assertThat(isRaumVerfuegbar(raum2, slot1, veranstaltung))
                .describedAs("Raum 2 ist für Slot 1 durch PV belegt").isFalse();
        assertThat(isRaumVerfuegbar(raum2, slot2, veranstaltung))
                .describedAs("Raum 2 ist für Slot 2 verfügbar").isTrue();

        QuarkusTransaction.requiringNew().run(() -> teilnehmer1.updateVerfuegbarkeit(slot2, veranstaltung, false, false));

        // Slotwechsel für PV von slot1 to slot2
        VortragDto updatedPv = pvDto("PV Updated Slot", referent, "Gruppe A", raum2, slot2, veranstaltung);
        updatedPv.version = 0L;

        given().contentType(MediaType.APPLICATION_JSON)
                .body(updatedPv)
                .when()
                .put("{vid}/vortraege/{vortragId}", veranstaltung.getId(), initialPv.id)
                .then()
                .log().all()
                .statusCode(BAD_REQUEST.getStatusCode())
                .body("error", startsWith("Neuer Slot 'Slot 2' ist für Teilnehmer 'tn1@example.com' nicht verfügbar."));

        // Verify state remains unchanged
        assertThat(isTeilnehmerVerfuegbar(teilnehmer1, slot1, veranstaltung))
                .describedAs("Gruppe A belegt durch Pflichtvortrag")
                .isFalse();
        assertThat(isTeilnehmerVerfuegbar(teilnehmer1, slot2, veranstaltung))
                .describedAs("Gruppe A nicht verfügbar wegen manueller Blockade")
                .isFalse();
        assertThat(isRaumVerfuegbar(raum2, slot1, veranstaltung))
                .describedAs("Raum 2 muss für Slot 1 wegen fehlgeschlagenem PV-Update weiter belegt bleiben")
                .isFalse();
        assertThat(isRaumVerfuegbar(raum2, slot2, veranstaltung))
                .describedAs("Raum 2 darf für Slot 2 wegen fehlgeschlagenem PV-Update verfügbar bleiben")
                .isTrue();
    }


    @Test
    void testUpdatePflichtvortragChangeRaumSuccess() {
        VortragDto pvDto = pvDto("PV Initial", referent, "Gruppe A", raum1, slot1, veranstaltung);
        VortragDto initialPv =
                given().contentType(MediaType.APPLICATION_JSON)
                        .body(pvDto)
                        .when()
                        .post("{vid}/vortraege", veranstaltung.getId())
                        .then()
                        .statusCode(CREATED.getStatusCode())
                        .extract()
                        .as(VortragDto.class);

        // Verify initial state
        assertThat(isRaumVerfuegbar(raum1, slot1, veranstaltung)).isFalse();
        assertThat(isRaumVerfuegbar(raum2, slot1, veranstaltung)).isTrue();

        // Update PV to change room to raum2
        VortragDto updatedPv = pvDto("PV Updated Raum", referent, "Gruppe A", raum2, slot1, veranstaltung);
        updatedPv.version = 0L;

        given().contentType(MediaType.APPLICATION_JSON)
                .body(updatedPv)
                .when()
                .put("{vid}/vortraege/{vortragId}", veranstaltung.getId(), initialPv.id)
                .then()
                .log().all()
                .statusCode(OK.getStatusCode());

        // Verify new state
        assertThat(isRaumVerfuegbar(raum1, slot1, veranstaltung)).isTrue(); // Old room freed
        assertThat(isRaumVerfuegbar(raum2, slot1, veranstaltung)).isFalse(); // New room occupied
        assertThat(isTeilnehmerVerfuegbar(teilnehmer1, slot1, veranstaltung)).isFalse(); // Teilnehmer availability unchanged
    }


    @Test
    void testUpdatePflichtvortragChangeRaumFailsIfNewRaumBelegt() {
        VortragDto pvDto = pvDto("PV Initial", referent, "Gruppe A", raum1, slot1, veranstaltung);
        VortragDto initialPv =
                given().contentType(MediaType.APPLICATION_JSON)
                        .body(pvDto)
                        .when()
                        .post("{vid}/vortraege", veranstaltung.getId())
                        .then()
                        .statusCode(CREATED.getStatusCode())
                        .extract()
                        .as(VortragDto.class);

        // Manually block raum2, slot1 in a committed transaction
        QuarkusTransaction.requiringNew().run(() -> raum2.updateRaumVerfuegbarkeit(slot1, veranstaltung, false, false));

        assertThat(isRaumVerfuegbar(raum2, slot1, veranstaltung)).isFalse();

        // Attempt to update PV to change room to raum2
        VortragDto updatedPv = pvDto("PV Updated Raum", referent, "Gruppe A", raum2, slot1, veranstaltung);
        updatedPv.version = 0L;

        given().contentType(MediaType.APPLICATION_JSON)
                .body(updatedPv)
                .when()
                .put("{vid}/vortraege/{vortragId}", veranstaltung.getId(), initialPv.id)
                .then()
                .log().all()
                .statusCode(BAD_REQUEST.getStatusCode())
                .body("error", startsWith("Neuer Raum 'Raum 2' ist im Slot 'Slot 1' nicht verfügbar."));

        // Verify state remains unchanged
        assertThat(isRaumVerfuegbar(raum1, slot1, veranstaltung)).isFalse(); // Raum1 still occupied by PV
        assertThat(isRaumVerfuegbar(raum2, slot1, veranstaltung)).isFalse(); // Raum2 still occupied by manual block
    }


    @Test
    void testUpdatePflichtvortragChangeRaumFailsIfNewRaumOccupiedByAnotherPflichtvortrag() {
        VortragDto pv1 = pvDto("PV1", referent, "Gruppe A", raum1, slot1, veranstaltung);
        VortragDto createdPv1 =
                given()
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(pv1)
                        .when()
                        .post("{vid}/vortraege", veranstaltung.getId())
                        .then()
                        .statusCode(CREATED.getStatusCode())
                        .extract()
                        .as(VortragDto.class);

        // Create PV2 that occupies raum2, slot1
        VortragDto pv2 = pvDto("PV2", referent, "Gruppe B", raum2, slot1, veranstaltung);
        given().contentType(MediaType.APPLICATION_JSON)
                .body(pv2)
                .when()
                .post("{vid}/vortraege", veranstaltung.getId())
                .then()
                .statusCode(CREATED.getStatusCode());

        // Attempt to update PV1 to use raum2 (which is occupied by PV2)
        VortragDto updatedPv1 = pvDto("PV1 Updated Raum", referent, "Gruppe A", raum2, slot1, veranstaltung);
        updatedPv1.version = 0L;

        given().contentType(MediaType.APPLICATION_JSON)
                .body(updatedPv1)
                .when()
                .put("{vid}/vortraege/{vortragId}", veranstaltung.getId(), createdPv1.id)
                .then()
                .log().all()
                .statusCode(BAD_REQUEST.getStatusCode())
                .body("error", startsWith("Neuer Raum 'Raum 2' ist im Slot 'Slot 1' nicht verfügbar."));

        // Verify state remains unchanged for PV1
        assertThat(isRaumVerfuegbar(raum1, slot1, veranstaltung)).isFalse(); // raum1 still occupied by PV1
        assertThat(isRaumVerfuegbar(raum2, slot1, veranstaltung)).isFalse(); // raum2 still occupied by PV2
    }


    @Test
    void testUpdatePflichtvortragChangeGruppeSuccess() {
        VortragDto pvDto = pvDto("PV Initial", referent, "Gruppe A", raum2, slot1, veranstaltung);
        VortragDto initialPv =
                given().contentType(MediaType.APPLICATION_JSON)
                        .body(pvDto)
                        .when()
                        .post("{vid}/vortraege", veranstaltung.getId())
                        .then()
                        .statusCode(CREATED.getStatusCode())
                        .extract()
                        .as(VortragDto.class);

        // Verify initial state
        assertThat(isTeilnehmerVerfuegbar(teilnehmer1, slot1, veranstaltung)).isFalse(); // Gruppe A
        assertThat(isTeilnehmerVerfuegbar(teilnehmer2, slot1, veranstaltung)).isFalse(); // Gruppe A
        assertThat(isTeilnehmerVerfuegbar(teilnehmer3, slot1, veranstaltung)).isTrue(); // Gruppe B

        // Update PV to change group to Gruppe B
        VortragDto updatedPv = pvDto("PV Updated Gruppe", referent, "Gruppe B", raum2, slot1, veranstaltung);
        updatedPv.version = 0L;

        given().contentType(MediaType.APPLICATION_JSON)
                .body(updatedPv)
                .when()
                .put("{vid}/vortraege/{vortragId}", veranstaltung.getId(), initialPv.id)
                .then()
                .log().all()
                .statusCode(OK.getStatusCode());

        // Verify new state
        assertThat(isTeilnehmerVerfuegbar(teilnehmer1, slot1, veranstaltung))
                .describedAs("TN1 aus alter Gruppe für Slot1 wieder verfuegbar").isTrue();
        assertThat(isTeilnehmerVerfuegbar(teilnehmer2, slot1, veranstaltung))
                .describedAs("TN2 aus alter Gruppe für Slot1 wieder verfuegbar").isTrue();
        assertThat(isTeilnehmerVerfuegbar(teilnehmer3, slot1, veranstaltung))
                .describedAs("TN3 fuer neue Gruppe für Slot1 nicht mehr verfuegbar").isFalse();
        assertThat(isRaumVerfuegbar(raum2, slot1, veranstaltung))
                .describedAs("Raum2 für Slot1 weiterhin nicht verfuegbar").isFalse();
    }


    @Test
    void testUpdatePflichtvortragChangeGruppeFailsIfNewGruppeNotAvailable() {
        // Create initial PV for Gruppe A
        String gruppeA = "Gruppe A";
        VortragDto pvDto = pvDto("PV Initial", referent, gruppeA, raum2, slot1, veranstaltung);
        VortragDto initialPv =
                given().contentType(MediaType.APPLICATION_JSON)
                        .body(pvDto)
                        .when()
                        .post("{vid}/vortraege", veranstaltung.getId())
                        .then()
                        .statusCode(CREATED.getStatusCode())
                        .extract()
                        .as(VortragDto.class);

        // Für die Gruppe B (von TN3) gibt es keinen Vortrag, deshalb ist er verfügbar.
        // Wir ändern seine Verfügbarkeit manuell für Slot_1
        assertThat(isTeilnehmerVerfuegbar(teilnehmer3, slot1, veranstaltung)).isTrue();

        QuarkusTransaction.requiringNew().run(() -> teilnehmer3.updateVerfuegbarkeit(slot1, veranstaltung, false, false));

        assertThat(isTeilnehmerVerfuegbar(teilnehmer3, slot1, veranstaltung)).isFalse();

        // Attempt to update PV to change group to Gruppe B
        VortragDto updatedPv = pvDto("PV Updated Gruppe", referent, "Gruppe B", raum2, slot1, veranstaltung);
        updatedPv.version = 0L;


        given().contentType(MediaType.APPLICATION_JSON)
                .body(updatedPv)
                .when()
                .put("{vid}/vortraege/{vortragId}", veranstaltung.getId(), initialPv.id)
                .then()
                .log().all()
                .statusCode(BAD_REQUEST.getStatusCode())
                .body("error", equalTo("Nicht alle Teilnehmer der neuen Gruppe 'Gruppe B' sind im Slot 'Slot 1' verfügbar."));

        // Verify state remains unchanged
        Pflichtvortrag reloaded = Pflichtvortrag.findById(initialPv.id);

        assertThat(reloaded.getPflichtgruppe())
                .describedAs("Gruppe des zu aktualisierenden Pflichtvortrags ist unverändert")
                .isEqualTo(gruppeA);
        assertThat(isTeilnehmerVerfuegbar(teilnehmer1, slot1, veranstaltung))
                .describedAs("Teilnehmer1 (Gruppe A) ist weiter für Slot1 nicht verfügbar").isFalse();
        assertThat(isTeilnehmerVerfuegbar(teilnehmer3, slot1, veranstaltung))
                .describedAs("Teilnehmer3 (Gruppe B) ist weiter für Slot1 nicht verfügbar").isFalse();
    }


    @Test
    void testUpdatePflichtvortragChangeGruppeFailsIfNewGruppeOccupiedByAnotherPflichtvortrag() {
        // Create PV1 for Gruppe A, Slot 1
        VortragDto pv1 = pvDto("PV1", referent, "Gruppe A", raum2, slot1, veranstaltung);
        VortragDto createdPv1 =
                given().contentType(MediaType.APPLICATION_JSON)
                        .body(pv1)
                        .when()
                        .post("{vid}/vortraege", veranstaltung.getId())
                        .then()
                        .statusCode(CREATED.getStatusCode())
                        .extract()
                        .as(VortragDto.class);

        // Create PV2 for Gruppe B, Slot 1
        VortragDto pv2 = pvDto("PV2", referent, "Gruppe B", raum1, slot1, veranstaltung);
        given().contentType(MediaType.APPLICATION_JSON)
                .body(pv2)
                .when()
                .post("{vid}/vortraege", veranstaltung.getId())
                .then()
                .statusCode(CREATED.getStatusCode());

        // Attempt to update PV1 to use Gruppe B (which is occupied by PV2)
        VortragDto updatedPv1 = pvDto("PV1 Updated Gruppe", referent, "Gruppe B", raum2, slot1, veranstaltung);
        updatedPv1.version = 0L;

        given().contentType(MediaType.APPLICATION_JSON)
                .body(updatedPv1)
                .when()
                .put("{vid}/vortraege/{vortragId}", veranstaltung.getId(), createdPv1.id)
                .then()
                .log().all()
                .statusCode(BAD_REQUEST.getStatusCode())
                .body("error", equalTo("Nicht alle Teilnehmer der neuen Gruppe 'Gruppe B' sind im Slot 'Slot 1' verfügbar."));

        // Verify state remains unchanged for PV1
        assertThat(isTeilnehmerVerfuegbar(teilnehmer1, slot1, veranstaltung))
                .describedAs("Teilnehmer1 aus Gruppe A weiterhin belegt für PV1")
                .isFalse();
        assertThat(isTeilnehmerVerfuegbar(teilnehmer3, slot1, veranstaltung))
                .describedAs("Teilnehmer1 aus Gruppe B weiterhin belegt für PV2")
                .isFalse();
    }


    @Test
    void testDeletePflichtvortragSuccess() {
        // Create initial PV
        VortragDto pvDto = pvDto("PV Test", referent, "Gruppe A", raum2, slot1, veranstaltung);
        VortragDto createdPv =
                given().contentType(MediaType.APPLICATION_JSON)
                        .body(pvDto)
                        .when()
                        .post("{vid}/vortraege", veranstaltung.getId())
                        .then()
                        .statusCode(CREATED.getStatusCode())
                        .extract()
                        .as(VortragDto.class);
        Long createdId = createdPv.id;

        // Verify initial state
        assertThat(isTeilnehmerVerfuegbar(teilnehmer1, slot1, veranstaltung)).isFalse();
        assertThat(isRaumVerfuegbar(raum2, slot1, veranstaltung)).isFalse();

        given().contentType(MediaType.APPLICATION_JSON)
                .when()
                .delete("{vid}/vortraege/{vortragId}", veranstaltung.getId(), createdId)
                .then()
                .statusCode(NO_CONTENT.getStatusCode());

        assertThat(isRaumVerfuegbar(raum2, slot1, veranstaltung))
                .describedAs("Raum2 ist für Slot1 wieder verfügbar").isTrue(); // Freed
        assertThat(isTeilnehmerVerfuegbar(teilnehmer1, slot1, veranstaltung))
                .describedAs("Teilnehmer1 ist für Slot1 wieder verfügbar").isTrue(); // Freed

        assertThat(Pflichtvortrag.<Pflichtvortrag>findById(createdId)).isNull(); // PV deleted
    }
}
