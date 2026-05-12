package kreyj.konfplan.resource;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.h2.H2DatabaseTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import jakarta.transaction.Transactional;
import kreyj.konfplan.dto.NutzerDto;
import kreyj.konfplan.dto.VeranstaltungDto;
import kreyj.konfplan.dto.VortragDto;
import kreyj.konfplan.persistence.*;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static io.restassured.RestAssured.given;
import static jakarta.ws.rs.core.Response.Status.*;
import static kreyj.konfplan.util.JwtHelper.tokenFor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.CoreMatchers.is;

@QuarkusTest
@TestSecurity(user = "admin@test.de", roles = "ADMIN")
@QuarkusTestResource(H2DatabaseTestResource.class)
class VeranstaltungResourceTest extends ResourceTestBase {

    Long testVid;

    @BeforeEach
    @Transactional
    void setup() {
        Admin admin = new Admin();
        admin.email = "admin@test.de";
        admin.passwordHash = "hash";
        admin.persist();

        Veranstaltung v = new Veranstaltung();
        v.name = "Test Event " + System.currentTimeMillis();
        v.beginntAm = LocalDateTime.now();
        v.persist();
        testVid = v.id;

        admin.addVeranstaltung(v);
        admin.persist();
    }

    @Test
    void testGetVortraegeHierarchical() {
        QuarkusTransaction.requiringNew().run(() -> {
            createWahlvortrag("Test Vortrag");
        });

        given()
                .when().get("/api/veranstaltungen/{vid}/vortraege", testVid)
                .then()
                .statusCode(OK.getStatusCode())
                .body("size()", is(1))
                .body("[0].titel", is("Test Vortrag"));
    }

    private void createWahlvortrag(String titel) {
        Referent r = new Referent();
        r.email = "ref-" + System.currentTimeMillis() + "@vresource.de";
        r.lastName = "Mustermann";
        r.persist();

        Wahlvortrag v = new Wahlvortrag();
        v.titel = titel;
        v.referent = r;
        v.veranstaltung = Veranstaltung.findById(testVid);
        v.persist();
    }

    @Test
    void testGetSlotsHierarchical() {
        QuarkusTransaction.requiringNew().run(() -> {
            EventSlot s1 = new EventSlot();
            s1.description = "Slot A";
            s1.startTime = LocalDateTime.now();
            s1.endTime = LocalDateTime.now().plusHours(1);
            s1.veranstaltung = Veranstaltung.findById(testVid);
            s1.persist();
        });

        given()
                .when().get("/api/veranstaltungen/{vid}/slots", testVid)
                .then()
                .statusCode(OK.getStatusCode())
                .body("size()", is(1))
                .body("[0].description", is("Slot A"));
    }

    @Test
    void testGetStatsHierarchical() {
        QuarkusTransaction.requiringNew().run(() -> {
            createWahlvortrag("Vortrag 1");
        });

        given()
                .when().get("/api/veranstaltungen/{vid}/stats", testVid)
                .then()
                .statusCode(OK.getStatusCode())
                .body("size()", is(1))
                .body("[0].titel", is("Vortrag 1"));
    }

    @Test
    @Transactional
    void testCreateNutzerHierarchical() {
        NutzerDto t = new NutzerDto();
        t.email = "new@test.de";
        t.role = "TEILNEHMER";
        t.firstName = "Neu";
        t.lastName = "Nutzer";

        given()
                .contentType(ContentType.JSON)
                .body(t)
                .when().post("/api/veranstaltungen/{vid}/nutzer", testVid)
                .then()
                .statusCode(CREATED.getStatusCode())
                .body("email", is("new@test.de"));

        Assertions.assertNotNull(Nutzer.findByEmail("new@test.de"));
    }

    @Test
    void testOptimisticLockingForVortrag() {
        final Long[] vortragId = new Long[1];
        final String referentEmail = "referent@test.de";
        final String referentPassword = "password";

        // 1. Create a Referent and a Wahlvortrag
        QuarkusTransaction.requiringNew().run(() -> {
            Referent r = new Referent();
            r.email = referentEmail;
            r.passwordHash = referentPassword;
            r.firstName = "Referent";
            r.lastName = "Test";
            r.persist();

            Wahlvortrag w = new Wahlvortrag();
            w.titel = "Original Vortrag Titel";
            w.referent = r;
            w.veranstaltung = Veranstaltung.findById(testVid);
            w.persist();
            vortragId[0] = w.id;
        });

        // 2. Admin (via @TestSecurity) fetches vortrag data
        Long talkId = vortragId[0];
        VortragDto adminFetchedVortrag = given()
                .when().get("/api/veranstaltungen/{vid}/vortraege/{vortragId}", testVid, talkId)
                .then()
                .statusCode(OK.getStatusCode())
                .extract().as(VortragDto.class);

        // 3. Referent (via JWT token) fetches vortrag data
        String referentToken = tokenFor(referentEmail, "REFERENT");
        VortragDto referentFetchedVortrag = given()
                .auth().oauth2(referentToken)
                .when().get("/api/veranstaltungen/{vid}/vortraege/{vortragId}", testVid, talkId)
                .then()
                .statusCode(OK.getStatusCode())
                .extract().as(VortragDto.class);

        // Ensure versions are the same initially
        assertThat(adminFetchedVortrag.version).isEqualTo(referentFetchedVortrag.version);
        assertThat(adminFetchedVortrag.version).isNotNull();

        // 4. Admin updates vortrag title (successful, increments version)
        adminFetchedVortrag.titel = "Admin Updated Vortrag Titel";
        given()
                .contentType(ContentType.JSON)
                .body(adminFetchedVortrag)
                .when().put("/api/veranstaltungen/{vid}/vortraege/{vortragId}", testVid, talkId)
                .then()
                .log().everything()
                .statusCode(OK.getStatusCode())
                .body("titel", is("Admin Updated Vortrag Titel"))
                .body("version", is(adminFetchedVortrag.version.intValue() + 1)); // Version should increment

        // 5. Referent attempts to update with outdated version (should fail with CONFLICT.getStatusCode() Conflict)
        referentFetchedVortrag.titel = "Referent Updated Vortrag Titel"; // This change should not be saved
        given()
                .auth().oauth2(referentToken)
                .contentType(ContentType.JSON)
                .body(referentFetchedVortrag) // This DTO has the old version
                .when().put("/api/veranstaltungen/{vid}/vortraege/{vortragId}", testVid, talkId)
                .then()
                .statusCode(CONFLICT.getStatusCode()); // Expect conflict

        // 6. Verify data integrity: only Admin's changes should be present
        VortragDto finalVortrag = given()
                .when().get("/api/veranstaltungen/{vid}/vortraege/{vortragId}", testVid, talkId)
                .then()
                .statusCode(OK.getStatusCode())
                .extract().as(VortragDto.class);

        assertThat(finalVortrag.titel).isEqualTo("Admin Updated Vortrag Titel");
        assertThat(finalVortrag.version).isEqualTo(adminFetchedVortrag.version + 1); // Version should be the one after admin's update
    }

    @Test
    void testUpdateVeranstaltung() {
        // 1. Veranstaltung abrufen
        VeranstaltungDto fetchedVeranstaltung = given()
                .when().get("/api/veranstaltungen/{id}", testVid)
                .then()
                .statusCode(OK.getStatusCode())
                .extract().as(VeranstaltungDto.class);

        // Sicherstellen, dass eine Version vorhanden ist
        assertThat(fetchedVeranstaltung.version).isNotNull();
        Long initialVersion = fetchedVeranstaltung.version;

        // 2. Veranstaltung aktualisieren
        String updatedName = "Updated Event Name " + System.currentTimeMillis();
        fetchedVeranstaltung.name = updatedName;

        given()
                .contentType(ContentType.JSON)
                .body(fetchedVeranstaltung)
                .when().put("/api/veranstaltungen/{id}", testVid)
                .then()
                .statusCode(OK.getStatusCode())
                .body("name", is(updatedName))
                .body("version", is(initialVersion.intValue() + 1)); // Version sollte inkrementiert werden

        // 3. Überprüfen, ob die Änderungen persistent sind
        VeranstaltungDto finalVeranstaltung = given()
                .when().get("/api/veranstaltungen/{id}", testVid)
                .then()
                .statusCode(OK.getStatusCode())
                .extract().as(VeranstaltungDto.class);

        assertThat(finalVeranstaltung.name).isEqualTo(updatedName);
        assertThat(finalVeranstaltung.version).isEqualTo(initialVersion + 1);
    }
}
