package kreyj.konfplan.adapter.in.web;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.common.http.TestHTTPEndpoint;
import io.quarkus.test.common.http.TestHTTPResource;
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
import kreyj.konfplan.adapter.in.web.dto.NutzerDto;
import kreyj.konfplan.adapter.in.web.dto.VeranstaltungDto;
import kreyj.konfplan.adapter.in.web.dto.VortragDto;
import kreyj.konfplan.util.JwtHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URL;
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
@TestHTTPEndpoint(VeranstaltungResource.class)
class VeranstaltungResourceTest extends DatabaseCleaner {
    @TestHTTPResource
    @TestHTTPEndpoint(AdminResource.class)
    URL adminEndpoint;

    Long testVid;


    @BeforeEach
    @Transactional
    void setup() {
        Admin admin = new Admin();
        admin.assignLoginName("admintest");
        admin.setEmail("admin@test.de");
        admin.setPasswordHash("hash");
        admin.persist();

        Veranstaltung v = new Veranstaltung();
        v.setName("Test Event " + System.currentTimeMillis());
        v.setBeginntAm(LocalDateTime.now());
        v.persist();
        testVid = v.getId();

        admin.addVeranstaltung(v);
        admin.persist();
    }


    @Test
    void testGetVortraegeHierarchical() {
        createWahlvortrag("Test Vortrag");

        given()
                .when().get("/{vid}/vortraege", testVid)
                .then()
                .statusCode(OK.getStatusCode())
                .body("size()", is(1))
                .body("[0].titel", is("Test Vortrag"));
    }


    @Transactional
    public void createWahlvortrag(String titel) {
        Referent r = new Referent();
        String uniqueSuffix = System.nanoTime() + "";
        r.assignLoginName("ref-" + uniqueSuffix);
        r.setEmail("ref-" + uniqueSuffix + "@vresource.de");
        r.setLastName("Mustermann");
        r.persist();

        Wahlvortrag v = new Wahlvortrag();
        v.setTitel(titel);
        v.setReferent(r);
        v.setVeranstaltung(Veranstaltung.findById(testVid));
        v.persist();
    }


    @Test
    void testGetSlotsHierarchical() {
        LocalDateTime now = LocalDateTime.now();

        QuarkusTransaction.requiringNew().run(() -> {
            Veranstaltung veranstaltung = Veranstaltung.findById(testVid);
            Slot s1 = new Slot("Slot A", now, now.plusHours(1), veranstaltung);
            s1.persistAndFlush();
            veranstaltung.addSlot(s1);
        });

        given().when()
                .get("/{vid}/slots", testVid)
                .then()
                .statusCode(OK.getStatusCode())
                .body("size()", is(1))
                .body("[0].description", is("Slot A"));
    }


    @Test
    void testGetStatsHierarchical() {
        createWahlvortrag("Vortrag 1");

        given()
                .when().get("/{vid}/stats", testVid)
                .then()
                .statusCode(OK.getStatusCode())
                .body("size()", is(1))
                .body("[0].titel", is("Vortrag 1"));
    }


    @Test
    void testCreateNutzerHierarchical() {
        String nutzerEmail = "new@test.de";
        NutzerDto tn = NutzerDto.teilnehmer(nutzerEmail, "Neu", "Nutzer");
        tn.loginName = "newtest";

        given().contentType(ContentType.JSON)
                .body(tn)
                .when().post("/{vid}/nutzer", testVid)
                .then()
                .statusCode(CREATED.getStatusCode())
                .body("email", is(nutzerEmail));

        assertThat(Nutzer.findByEmail(nutzerEmail)).isNotNull();
    }


    @Test
    void testOptimisticLockingForVortrag() {
        final String referentEmail = "referent@test.de";

        // 1. Create a Referent and a Wahlvortrag
        NutzerDto refDto = NutzerDto.referent(referentEmail, "Referent", "Test");
        refDto.loginName = referentEmail; // muss mit dem im Token unten verwendeten upn übereinstimmen
        NutzerDto referent =
                given()
                    .baseUri(adminEndpoint.toString())
                    .basePath("/nutzer")
                    .contentType(ContentType.JSON)
                        .body(refDto)
                        .when().post()
                        .then()
                        .statusCode(OK.getStatusCode())
                        .extract().as(NutzerDto.class);

        VortragDto wvDto = new VortragDto(false, "Original Vortrag Titel", "Inhalt",
            referent.id, testVid);
        VortragDto w =
                given().contentType(ContentType.JSON)
                        .body(wvDto)
                        .when().post("/{vid}/vortraege", testVid)
                        .then()
                        .statusCode(CREATED.getStatusCode())
                        .extract().as(VortragDto.class);

        // 2. Admin (via @TestSecurity) fetches vortrag data
        Long vortragId = w.id;
        VortragDto adminFetchedVortrag = given()
                .when()
                .get("/{vid}/vortraege/{vortragId}", testVid, vortragId)
                .then()
                .statusCode(OK.getStatusCode())
                .extract().as(VortragDto.class);
        assertThat(adminFetchedVortrag.version).isNotNull().isEqualTo(0L);

        // 3. Referent (via JWT token) fetches vortrag data
        String referentToken = tokenFor(referentEmail, "REFERENT");
        VortragDto referentFetchedVortrag = given()
                .auth().oauth2(referentToken)
                .when().get("/{vid}/vortraege/{vortragId}", testVid, vortragId)
                .then()
                .statusCode(OK.getStatusCode())
                .extract().as(VortragDto.class);

        // Ensure versions are the same initially
        assertThat(referentFetchedVortrag.version).isEqualTo(adminFetchedVortrag.version);

        // 4. Admin updates vortrag title (successful, increments version)
        adminFetchedVortrag.titel = "Admin Updated Vortrag Titel";

        VortragDto adminUpdate =
                given().contentType(ContentType.JSON)
                        .body(adminFetchedVortrag)
                        .when()
                        .put("/{vid}/vortraege/{vortragId}",
                                testVid, vortragId)
                        .then()
                        .statusCode(OK.getStatusCode())
                        .extract().as(VortragDto.class);

        assertThat(adminUpdate.titel).isEqualTo("Admin Updated Vortrag Titel");
        assertThat(adminUpdate.version).isEqualTo(adminFetchedVortrag.version + 1);

        // 5. Referent attempts to update with outdated version (should fail with CONFLICT.getStatusCode() Conflict)
        referentFetchedVortrag.titel = "Referent Updated Vortrag Titel"; // This change should not be saved
        given()
                .auth().oauth2(referentToken)
                .contentType(ContentType.JSON)
                .body(referentFetchedVortrag) // This DTO has the old version
                .when()
                .put("/{vid}/vortraege/{vortragId}", testVid, vortragId)
                .then()
                .statusCode(CONFLICT.getStatusCode()); // Expect conflict

        // 6. Verify data integrity: only Admin's changes should be present
        VortragDto finalVortrag = given().when()
                .get("/{vid}/vortraege/{vortragId}", testVid, vortragId)
                .then()
                .statusCode(OK.getStatusCode())
                .extract().as(VortragDto.class);

        assertThat(finalVortrag.titel).isEqualTo(adminUpdate.titel);
        assertThat(finalVortrag.version)
                .describedAs("Version should be the same as admin's update")
                .isEqualTo(adminUpdate.version);
    }


    @Test
    void testUpdateVeranstaltung() {
        // 1. Veranstaltung abrufen
        VeranstaltungDto fetchedVeranstaltung = given().when()
                .get("/{id}", testVid)
                .then()
                .statusCode(OK.getStatusCode())
                .extract().as(VeranstaltungDto.class);

        // Sicherstellen, dass eine Version vorhanden ist
        assertThat(fetchedVeranstaltung.version).isNotNull();
        Long initialVersion = fetchedVeranstaltung.version;

        // 2. Veranstaltung aktualisieren
        String updatedName = "Updated Event Name " + System.currentTimeMillis();
        fetchedVeranstaltung.setName(updatedName);

        given().contentType(ContentType.JSON)
                .body(fetchedVeranstaltung)
                .when()
                .put("/{id}", testVid)
                .then()
                .statusCode(OK.getStatusCode())
                .body("name", is(updatedName))
                .body("version", is(initialVersion.intValue() + 1)); // Version sollte inkrementiert werden

        // 3. Überprüfen, ob die Änderungen persistent sind
        VeranstaltungDto finalVeranstaltung = given()
                .when().get("/{id}", testVid)
                .then()
                .statusCode(OK.getStatusCode())
                .extract().as(VeranstaltungDto.class);

        assertThat(finalVeranstaltung.getName()).isEqualTo(updatedName);
        assertThat(finalVeranstaltung.version).isEqualTo(initialVersion + 1);
    }

    @Test
    void testOptimisticLockingForVeranstaltungUpdate() {
        final Long[] vIdArray = {0L};
        String admin2Email = "admin2@example.com";

        QuarkusTransaction.requiringNew().run(() -> {
            Veranstaltung v = new Veranstaltung();
            v.setName("Original Event Name");
            v.setBeginntAm(LocalDateTime.now().plusDays(1));
            v.setEndetAm(LocalDateTime.now().plusDays(2));
            v.persist();
            vIdArray[0] = v.getId();

            Admin admin2 = new Admin();
            admin2.assignLoginName(admin2Email);
            admin2.setEmail(admin2Email);
            admin2.setPasswordHash("hash");
            admin2.persist();
        });
        Long vId = vIdArray[0];

        given()
            .when().get()
            .then()
            .statusCode(OK.getStatusCode())
            .extract().body().jsonPath().getList(".", VeranstaltungDto.class);

        // 2. Admin 1 (via @TestSecurity) fetches veranstaltung data
        VeranstaltungDto admin1FetchedVeranstaltung = given()
            .when().get("/{id}", vId)
            .then()
            .statusCode(OK.getStatusCode())
            .extract().as(VeranstaltungDto.class);

        // 3. Admin 2 (via JWT token) fetches veranstaltung data
        String admin2Token = JwtHelper.tokenFor(admin2Email, "ADMIN");
        VeranstaltungDto admin2FetchedVeranstaltung = given()
            .auth().oauth2(admin2Token)
            .when().get("/{id}", vId)
            .then()
            .statusCode(OK.getStatusCode())
            .extract().as(VeranstaltungDto.class);

        // Ensure versions are the same initially
        assertThat(admin1FetchedVeranstaltung.version).isEqualTo(admin2FetchedVeranstaltung.version);
        assertThat(admin1FetchedVeranstaltung.version).isNotNull();

        // 4. Admin 1 updates veranstaltung name (successful, increments version)
        admin1FetchedVeranstaltung.setName("Admin1 Updated Event Name");
        VeranstaltungDto updatedVDto = given()
            .contentType(ContentType.JSON)
            .body(admin1FetchedVeranstaltung)
            .when().put("/{id}", vId)
            .then()
            .statusCode(OK.getStatusCode())
            .extract().as(VeranstaltungDto.class);

        assertThat(updatedVDto.getName()).isEqualTo("Admin1 Updated Event Name");
        assertThat(updatedVDto.version)
            .describedAs("Version should be incremented")
            .isEqualTo(admin1FetchedVeranstaltung.version + 1);

        // 5. Admin 2 attempts to update with outdated version (should fail with CONFLICT.getStatusCode() Conflict)
        admin2FetchedVeranstaltung.setName("Admin2 Updated Event Name"); // This change should not be saved
        given()
            .auth().oauth2(admin2Token)
            .contentType(ContentType.JSON)
            .body(admin2FetchedVeranstaltung) // This DTO has the old version
            .when().put("/{id}", vId)
            .then()
            .statusCode(CONFLICT.getStatusCode());

        // 6. Verify data integrity: only Admin 1's changes should be present
        VeranstaltungDto finalVeranstaltung = given()
            .when().get("/{id}", vId)
            .then()
            .statusCode(OK.getStatusCode())
            .extract().as(VeranstaltungDto.class);

        assertThat(finalVeranstaltung.getName()).isEqualTo("Admin1 Updated Event Name");
        assertThat(finalVeranstaltung.version)
            .describedAs("Version should be the one after Admin 1's update")
            .isEqualTo(admin1FetchedVeranstaltung.version + 1);
    }
}
