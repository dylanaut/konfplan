package kreyj.konfplan.presentation;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.h2.H2DatabaseTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import jakarta.transaction.Transactional;
import kreyj.konfplan.persistence.Admin;
import kreyj.konfplan.persistence.Nutzer;
import kreyj.konfplan.persistence.NutzerVerfuegbarkeit;
import kreyj.konfplan.persistence.Referent;
import kreyj.konfplan.persistence.Slot;
import kreyj.konfplan.persistence.Teilnehmer;
import kreyj.konfplan.persistence.Veranstaltung;
import kreyj.konfplan.presentation.dto.NutzerDto;
import kreyj.konfplan.presentation.dto.VeranstaltungDto;
import kreyj.konfplan.presentation.dto.VerfuegbarkeitDto;
import kreyj.konfplan.util.JwtHelper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static io.restassured.RestAssured.given;
import static jakarta.ws.rs.core.Response.Status.CONFLICT;
import static jakarta.ws.rs.core.Response.Status.FORBIDDEN;
import static jakarta.ws.rs.core.Response.Status.NO_CONTENT;
import static jakarta.ws.rs.core.Response.Status.OK;
import static kreyj.konfplan.persistence.NutzerVerfuegbarkeitId.nvIdL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.CoreMatchers.is;

@QuarkusTest
@TestSecurity(user = "admin@example.com", roles = "ADMIN")
@QuarkusTestResource(H2DatabaseTestResource.class)
class AdminResourceTest extends DatabaseCleaner {

    Long adminId;

    @BeforeEach
    @Transactional
    void setup() {
        Admin admin = new Admin();
        admin.setEmail("admin@example.com");
        admin.setPasswordHash("hash");
        admin.persistAndFlush();

        adminId = admin.getId();
    }

    @Test
    @Transactional
    void testGetAllUsersGlobal() {
        Admin a = new Admin();
        a.setEmail("admin1@example.com");
        a.persistAndFlush();

        given()
                .when().get("/api/admin/nutzer")
                .then()
                .statusCode(OK.getStatusCode())
                .contentType(ContentType.JSON)
                .body("size()", is(2)); // setup admin + admin1
    }

    @Test
    @Transactional
    void testCreateUser() {
        NutzerDto dto = new NutzerDto();
        dto.email = "new@test.de";
        dto.role = "REFERENT";
        dto.firstName = "Max";
        dto.lastName = "Mustermann";

        given()
                .contentType(ContentType.JSON)
                .body(dto)
                .when().post("/api/admin/nutzer")
                .then()
                .statusCode(OK.getStatusCode())
                .body("email", is("new@test.de"));

        assertThat(Nutzer.findByEmail("new@test.de")).isNotNull();
    }

    @Test
    @Transactional
    void testUpdateUser() {
        Teilnehmer t = new Teilnehmer();
        t.setEmail("old@test.de");
        t.persistAndFlush();
        Long userId = t.getId();

        NutzerDto dto = new NutzerDto();
        dto.email = "updated@test.de";
        dto.role = "TEILNEHMER";
        dto.firstName = "Max";
        dto.lastName = "Mustermann";
        dto.version = 0L;

        given()
                .contentType(ContentType.JSON)
                .body(dto)
                .when().put("/api/admin/nutzer/{id}", userId)
                .then()
                .statusCode(OK.getStatusCode())
                .body("email", is("old@test.de")); // Email should not have changed yet

        Nutzer user = Nutzer.findById(userId);
        assertThat(user.getEmail()).isEqualTo("old@test.de");
        assertThat(user.getNewEmail()).isEqualTo("updated@test.de");
        assertThat(user.getEmailChangeToken()).isNotNull();
    }

    @Test
    @Transactional
    void testDeleteUser() {
        Teilnehmer t = new Teilnehmer();
        t.setEmail("todelete@test.de");
        t.persistAndFlush();
        Long userId = t.getId();

        given()
                .when().delete("/api/admin/nutzer/{id}", userId)
                .then()
                .statusCode(NO_CONTENT.getStatusCode());

        Assertions.assertNull(Nutzer.findById(userId));
    }

    @Test
    @Transactional
    void testInviteUser() {
        Teilnehmer t = new Teilnehmer();
        t.setEmail("invite@test.de");
        t.persistAndFlush();
        Long userId = t.getId();

        Veranstaltung v = new Veranstaltung();
        v.setName("Invite Event");
        v.setBeginntAm(LocalDateTime.now().plusDays(1));
        v.persistAndFlush();
        Long eventId = v.getId();

        Admin orga = Admin.findById(adminId);
        orga.addVeranstaltung(v);

        given()
                .contentType(ContentType.JSON)
                .when().post("/api/admin/nutzer/{userId}/einladen/{eventId}", userId, eventId)
                .then()
                .statusCode(OK.getStatusCode());

        Teilnehmer invitedUser = Teilnehmer.findById(userId);
        assertThat(invitedUser.getVeranstaltungen()).hasSize(1);
    }

    @Test
    @Transactional
    void testVerfuegbarkeitenEndpoints() {
        Veranstaltung v = new Veranstaltung();
        v.setName("Event " + System.currentTimeMillis());
        v.setBeginntAm(LocalDateTime.now());
        v.persistAndFlush();
        Long vId = v.getId();

        Slot slot = new Slot();
        slot.setDescription("Slot 1");
        slot.setStartTime(LocalDateTime.now());
        slot.setEndTime(LocalDateTime.now().plusHours(1));
        v.addSlot(slot);
        slot.persistAndFlush();
        Long slotId = slot.getId();

        Referent referent = new Referent();
        referent.setEmail("ref@test.de");
        referent.setPasswordHash("hash");
        referent.persistAndFlush();
        referent.addVeranstaltung(v);
        Long refId = referent.getId();

        // GET Test
        given()
                .when().get("/api/admin/veranstaltungen/{vid}/verfuegbarkeiten", vId)
                .then()
                .statusCode(OK.getStatusCode())
                .body("size()", is(1))
                .body("[0].nutzerId", is(refId.intValue()));

        // POST Test (Update)
        VerfuegbarkeitDto updateDto = new VerfuegbarkeitDto(refId, slotId, false);
        given()
                .contentType(ContentType.JSON)
                .body(updateDto)
                .when().post("/api/admin/veranstaltungen/{vid}/verfuegbarkeiten", vId)
                .then()
                .statusCode(OK.getStatusCode());

        // Verifizieren
        NutzerVerfuegbarkeit nv = NutzerVerfuegbarkeit.findById(nvIdL(refId, vId));

        assertThat(nv.getVerfuegbareSlotIds()).doesNotContain(slotId);
    }

    @Test
    @Transactional
    void testOptimisticLockingFuerTeilnehmerProfil() {
        Teilnehmer t = new Teilnehmer();
        t.setEmail("teilnehmer@example.com");
        t.setPasswordHash("password");
        t.setFirstName("Original");
        t.setLastName("Name");
        t.persistAndFlush();
        Long tnId = t.getId();

        // 2. Admin 1 fetches teilnehmer data (initial version)
        NutzerDto adminFetchedUser1 = given()
                .when().get("/api/admin/nutzer/{id}", tnId)
                .then()
                .statusCode(OK.getStatusCode())
                .extract().as(NutzerDto.class);

        // 3. Admin 2 fetches the same participant again (simulating concurrent user/tab)
        NutzerDto adminFetchedUser2 = given()
                .when().get("/api/admin/nutzer/{id}", tnId)
                .then()
                .statusCode(OK.getStatusCode())
                .extract().as(NutzerDto.class);

        // Ensure versions are the same initially
        assertThat(adminFetchedUser1.version).isNotNull();
        assertThat(adminFetchedUser1.version).isEqualTo(adminFetchedUser2.version);

        // 4. First update succeeds (increments version)
        adminFetchedUser1.firstName = "AdminUpdated";
        NutzerDto fetchedUpdate = given()
                .contentType(ContentType.JSON)
                .body(adminFetchedUser1)
                .when().put("/api/admin/nutzer/{id}", tnId)
                .then()
                .statusCode(OK.getStatusCode())
                .extract().as(NutzerDto.class);

        assertThat(fetchedUpdate.firstName).isEqualTo("AdminUpdated");
        assertThat(fetchedUpdate.version).describedAs("Version should be incremented")
                .isEqualTo(adminFetchedUser1.version.intValue() + 1);

        // 5. Second update attempts to update with outdated version (should fail with CONFLICT.getStatusCode() Conflict)
        adminFetchedUser2.lastName = "TeilnehmerUpdated"; // This change should not be saved
        given()
                .contentType(ContentType.JSON)
                .body(adminFetchedUser2) // This DTO has the old version
                .when().put("/api/admin/nutzer/{id}", tnId)
                .then()
                .statusCode(CONFLICT.getStatusCode()); // Expect conflict

        // 6. Verify data integrity: only the first update should be present
        NutzerDto finalUser = given()
                .when().get("/api/admin/nutzer/{id}", tnId)
                .then()
                .statusCode(OK.getStatusCode())
                .extract().as(NutzerDto.class);

        assertThat(finalUser.firstName).isEqualTo("AdminUpdated");
        assertThat(finalUser.lastName).isEqualTo("Name"); // The second change should not be applied
        assertThat(finalUser.version).isEqualTo(adminFetchedUser1.version + 1); // Version should be the one after the first update
    }

    @Test
    @Transactional
    void testOptimisticLockingForVeranstaltungName() {
        Veranstaltung v = new Veranstaltung();
        v.setName("Original Event Name");
        v.setBeginntAm(LocalDateTime.now().plusDays(1));
        v.setEndetAm(LocalDateTime.now().plusDays(2));
        v.persistAndFlush();
        Long vid = v.getId();

        Admin admin2 = new Admin();
        admin2.setEmail("admin2@example.com");
        admin2.setPasswordHash("hash");
        admin2.persistAndFlush();

        given()
                .when().get("/api/veranstaltungen")
                .then()
                .statusCode(OK.getStatusCode())
                .extract().body().jsonPath().getList(".", VeranstaltungDto.class);

        // 2. Admin 1 (via @TestSecurity) fetches veranstaltung data
        VeranstaltungDto admin1FetchedVeranstaltung = given()
                .when().get("/api/veranstaltungen/{id}", vid)
                .then()
                .statusCode(OK.getStatusCode())
                .extract().as(VeranstaltungDto.class);

        // 3. Admin 2 (via JWT token) fetches veranstaltung data
        String admin2Token = JwtHelper.tokenFor(admin2.getEmail(), "ADMIN");
        VeranstaltungDto admin2FetchedVeranstaltung = given()
                .auth().oauth2(admin2Token)
                .when().get("/api/veranstaltungen/{id}", vid)
                .then()
                .statusCode(OK.getStatusCode())
                .extract().as(VeranstaltungDto.class);

        // Ensure versions are the same initially
        assertThat(admin1FetchedVeranstaltung.version).isEqualTo(admin2FetchedVeranstaltung.version);
        assertThat(admin1FetchedVeranstaltung.version).isNotNull();

        // 4. Admin 1 updates veranstaltung name (successful, increments version)
        admin1FetchedVeranstaltung.name = "Admin1 Updated Event Name";
        VeranstaltungDto updatedVDto = given()
                .contentType(ContentType.JSON)
                .body(admin1FetchedVeranstaltung)
                .when().put("/api/veranstaltungen/{id}", vid)
                .then()
                .statusCode(OK.getStatusCode())
                .extract().as(VeranstaltungDto.class);

        assertThat(updatedVDto.name).isEqualTo("Admin1 Updated Event Name");
        assertThat(updatedVDto.version).isEqualTo(admin1FetchedVeranstaltung.version + 1); // Version should increment

        // 5. Admin 2 attempts to update with outdated version (should fail with CONFLICT.getStatusCode() Conflict)
        admin2FetchedVeranstaltung.name = "Admin2 Updated Event Name"; // This change should not be saved
        given()
                .auth().oauth2(admin2Token)
                .contentType(ContentType.JSON)
                .body(admin2FetchedVeranstaltung) // This DTO has the old version
                .when().put("/api/veranstaltungen/{id}", vid)
                .then()
                .statusCode(CONFLICT.getStatusCode()); // Expect conflict

        // 6. Verify data integrity: only Admin 1's changes should be present
        VeranstaltungDto finalVeranstaltung = given()
                .when().get("/api/veranstaltungen/{id}", vid)
                .then()
                .statusCode(OK.getStatusCode())
                .extract().as(VeranstaltungDto.class);

        assertThat(finalVeranstaltung.name).isEqualTo("Admin1 Updated Event Name");
        assertThat(finalVeranstaltung.version).isEqualTo(admin1FetchedVeranstaltung.version + 1); // Version should be the one after admin1's update
    }

    @Test
    @TestSecurity(user = "nutzer@example.com", roles = "USER")
    void testGlobalAccessForbidden() {
        given()
                .when().get("/api/admin/nutzer")
                .then()
                .statusCode(FORBIDDEN.getStatusCode());
    }
}