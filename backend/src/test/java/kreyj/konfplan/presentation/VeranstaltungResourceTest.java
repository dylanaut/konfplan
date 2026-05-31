package kreyj.konfplan.presentation;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.h2.H2DatabaseTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import jakarta.transaction.Transactional;
import kreyj.konfplan.persistence.Admin;
import kreyj.konfplan.persistence.Nutzer;
import kreyj.konfplan.persistence.Referent;
import kreyj.konfplan.persistence.Slot;
import kreyj.konfplan.persistence.Veranstaltung;
import kreyj.konfplan.persistence.Wahlvortrag;
import kreyj.konfplan.presentation.dto.NutzerDto;
import kreyj.konfplan.presentation.dto.VeranstaltungDto;
import kreyj.konfplan.presentation.dto.VortragDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static io.restassured.RestAssured.given;
import static jakarta.ws.rs.core.Response.Status.CONFLICT;
import static jakarta.ws.rs.core.Response.Status.CREATED;
import static jakarta.ws.rs.core.Response.Status.OK;
import static kreyj.konfplan.util.JwtHelper.tokenFor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.CoreMatchers.is;

@QuarkusTest
@TestSecurity(user = "admin@test.de", roles = "ADMIN")
@QuarkusTestResource(H2DatabaseTestResource.class)
class VeranstaltungResourceTest extends DatabaseCleaner {

    Long testVid;

    @BeforeEach
    @Transactional
    void setup() {
        Admin admin = new Admin();
        admin.setEmail("admin@test.de");
        admin.setPasswordHash("hash");
        admin.persistAndFlush();

        Veranstaltung v = new Veranstaltung();
        v.setName("Test Event " + System.currentTimeMillis());
        v.setBeginntAm(LocalDateTime.now());
        v.persistAndFlush();
        testVid = v.getId();

        admin.addVeranstaltung(v);
        admin.persistAndFlush();
    }

    @Test
    void testGetVortraegeHierarchical() {
        createWahlvortrag("Test Vortrag");

        given()
                .when().get("/api/veranstaltungen/{vid}/vortraege", testVid)
                .then()
                .statusCode(OK.getStatusCode())
                .body("size()", is(1))
                .body("[0].titel", is("Test Vortrag"));
    }

    @Transactional
    public void createWahlvortrag(String titel) {
        Referent r = new Referent();
        r.setEmail("ref-" + System.currentTimeMillis() + "@vresource.de");
        r.setLastName("Mustermann");
        r.persistAndFlush();

        Wahlvortrag v = new Wahlvortrag();
        v.setTitel(titel);
        v.setReferent(r);
        v.setVeranstaltung(Veranstaltung.findById(testVid));
        v.persistAndFlush();
    }

    @Test
    @Transactional
    void testGetSlotsHierarchical() {
        LocalDateTime now = LocalDateTime.now();
        Veranstaltung veranstaltung = Veranstaltung.findById(testVid);

        Slot s1 = new Slot("Slot A", now, now.plusHours(1), veranstaltung);
        s1.persistAndFlush();

        veranstaltung.addSlot(s1);


        given()
                .when().get("/api/veranstaltungen/{vid}/slots", testVid)
                .then()
                .statusCode(OK.getStatusCode())
                .body("size()", is(1))
                .body("[0].description", is("Slot A"));
    }

    @Test
    void testGetStatsHierarchical() {
        createWahlvortrag("Vortrag 1");

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
        String nutzerEmail = "new@test.de";

        t.email = nutzerEmail;
        t.role = "TEILNEHMER";
        t.firstName = "Neu";
        t.lastName = "Nutzer";

        given()
                .contentType(ContentType.JSON)
                .body(t)
                .when().post("/api/veranstaltungen/{vid}/nutzer", testVid)
                .then()
                .statusCode(CREATED.getStatusCode())
                .body("email", is(nutzerEmail));

        assertThat(Nutzer.findByEmail(nutzerEmail)).isNotNull();
    }

    @Test
    @Transactional
    void testOptimisticLockingForVortrag() {
        final String referentEmail = "referent@test.de";
        final String referentPassword = "password";

        // 1. Create a Referent and a Wahlvortrag
        Referent r = new Referent();
        r.setEmail(referentEmail);
        r.setPasswordHash(referentPassword);
        r.setFirstName("Referent");
        r.setLastName("Test");
        r.persistAndFlush();

        Wahlvortrag w = new Wahlvortrag();
        w.setTitel("Original Vortrag Titel");
        w.setReferent(r);
        w.setVeranstaltung(Veranstaltung.findById(testVid));
        w.persistAndFlush();

        // 2. Admin (via @TestSecurity) fetches vortrag data
        Long vortragId = w.getId();
        VortragDto adminFetchedVortrag = given()
                .when().get("/api/veranstaltungen/{vid}/vortraege/{vortragId}", testVid, vortragId)
                .then()
                .statusCode(OK.getStatusCode())
                .extract().as(VortragDto.class);

        // 3. Referent (via JWT token) fetches vortrag data
        String referentToken = tokenFor(referentEmail, "REFERENT");
        VortragDto referentFetchedVortrag = given()
                .auth().oauth2(referentToken)
                .when().get("/api/veranstaltungen/{vid}/vortraege/{vortragId}", testVid, vortragId)
                .then()
                .statusCode(OK.getStatusCode())
                .extract().as(VortragDto.class);

        // Ensure versions are the same initially
        assertThat(adminFetchedVortrag.version).isNotNull();
        assertThat(adminFetchedVortrag.version).isEqualTo(referentFetchedVortrag.version);

        // 4. Admin updates vortrag title (successful, increments version)
        adminFetchedVortrag.titel = "Admin Updated Vortrag Titel";
        given()
                .contentType(ContentType.JSON)
                .body(adminFetchedVortrag)
                .when().put("/api/veranstaltungen/{vid}/vortraege/{vortragId}", testVid, vortragId)
                .then()
                .statusCode(OK.getStatusCode())
                .body("titel", is("Admin Updated Vortrag Titel"))
                .body("version", is(adminFetchedVortrag.version.intValue() + 1)); // Version should increment

        // 5. Referent attempts to update with outdated version (should fail with CONFLICT.getStatusCode() Conflict)
        referentFetchedVortrag.titel = "Referent Updated Vortrag Titel"; // This change should not be saved
        given()
                .auth().oauth2(referentToken)
                .contentType(ContentType.JSON)
                .body(referentFetchedVortrag) // This DTO has the old version
                .when().put("/api/veranstaltungen/{vid}/vortraege/{vortragId}", testVid, vortragId)
                .then()
                .statusCode(CONFLICT.getStatusCode()); // Expect conflict

        // 6. Verify data integrity: only Admin's changes should be present
        VortragDto finalVortrag = given()
                .when().get("/api/veranstaltungen/{vid}/vortraege/{vortragId}", testVid, vortragId)
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
