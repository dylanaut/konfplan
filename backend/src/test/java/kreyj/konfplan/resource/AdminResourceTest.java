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
import kreyj.konfplan.dto.VerfuegbarkeitDto;
import kreyj.konfplan.persistence.Admin;
import kreyj.konfplan.persistence.EventSlot;
import kreyj.konfplan.persistence.Nutzer;
import kreyj.konfplan.persistence.Referent;
import kreyj.konfplan.persistence.Teilnehmer;
import kreyj.konfplan.persistence.Veranstaltung;
import kreyj.konfplan.persistence.Verfuegbarkeit;
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
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.CoreMatchers.is;

@QuarkusTest
@TestSecurity(user = "admin@example.com", roles = "ADMIN")
@QuarkusTestResource(H2DatabaseTestResource.class)
class AdminResourceTest extends ResourceTestBase {

    Long adminId;

    @BeforeEach
    @Transactional
    void setup() {
        Admin admin = new Admin();
        admin.setEmail("admin@example.com");
        admin.setPasswordHash("hash");
        admin.persist();

        adminId = admin.getId();
    }

    @Test
    void testGetAllUsersGlobal() {
        QuarkusTransaction.requiringNew().run(() -> {
            Admin a = new Admin();
            a.setEmail("admin1@example.com");
            a.persist();
        });

        given()
                .when().get("/api/admin/nutzer")
                .then()
                .statusCode(OK.getStatusCode())
                .contentType(ContentType.JSON)
                .body("size()", is(2)); // setup admin + admin1
    }

    @Test
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
    void testUpdateUser() {
        final Long[] userId = new Long[1];
        final String oldEmail = "old@test.de";
        final String newEmail = "updated@test.de";

        QuarkusTransaction.requiringNew().run(() -> {
            Teilnehmer t = new Teilnehmer();
            t.setEmail(oldEmail);
            t.persist();
            userId[0] = t.getId();
        });

        NutzerDto dto = new NutzerDto();
        dto.email = newEmail;
        dto.role = "TEILNEHMER";
        dto.firstName = "Max";
        dto.lastName = "Mustermann";
        dto.version = 0L;

        given()
                .contentType(ContentType.JSON)
                .body(dto)
                .when().put("/api/admin/nutzer/{id}", userId[0])
                .then()
                .statusCode(OK.getStatusCode())
                .body("email", is(oldEmail)); // Email should not have changed yet

        QuarkusTransaction.requiringNew().run(() -> {
            Nutzer user = Nutzer.findById(userId[0]);
            assertThat(user.getEmail()).isEqualTo(oldEmail);
            assertThat(user.getNewEmail()).isEqualTo(newEmail);
            assertThat(user.getEmailChangeToken()).isNotNull();
        });
    }

    @Test
    void testDeleteUser() {
        final Long[] userId = new Long[1];
        QuarkusTransaction.requiringNew().run(() -> {
            Teilnehmer t = new Teilnehmer();
            t.setEmail("todelete@test.de");
            t.persist();
            userId[0] = t.getId();
        });

        given()
                .when().delete("/api/admin/nutzer/{id}", userId[0])
                .then()
                .statusCode(NO_CONTENT.getStatusCode());

        Assertions.assertNull(Nutzer.findById(userId[0]));
    }

    @Test
    void testInviteUser() {
        final Long[] userId = new Long[1];
        final Long[] eventId = new Long[1];

        QuarkusTransaction.requiringNew().run(() -> {
            Teilnehmer t = new Teilnehmer();
            t.setEmail("invite@test.de");
            t.persist();
            userId[0] = t.getId();

            Veranstaltung v = new Veranstaltung();
            v.setName("Invite Event");
            v.setBeginntAm(LocalDateTime.now().plusDays(1));
            v.persist();
            eventId[0] = v.getId();

            Admin orga = Admin.findById(adminId);
            orga.addVeranstaltung(v);
        });

        given()
                .contentType(ContentType.JSON)
                .when().post("/api/admin/nutzer/{userId}/einladen/{eventId}", userId[0], eventId[0])
                .then()
                .statusCode(OK.getStatusCode());

        QuarkusTransaction.requiringNew().run(() -> {
            Teilnehmer t = Teilnehmer.findById(userId[0]);
            assertThat(1).isEqualTo(t.getVeranstaltungen().size());
        });
    }

    @Test
    void testVerfuegbarkeitenEndpoints() {
        final Long[] vid = new Long[1];
        final Long[] rid = new Long[1];
        final Long[] sid = new Long[1];

        QuarkusTransaction.requiringNew().run(() -> {
            Veranstaltung v = new Veranstaltung();
            v.setName("Event " + System.currentTimeMillis());
            v.setBeginntAm(LocalDateTime.now());
            v.persist();
            vid[0] = v.getId();

            EventSlot slot = new EventSlot();
            slot.setDescription("Slot 1");
            slot.setStartTime(LocalDateTime.now());
            slot.setEndTime(LocalDateTime.now().plusHours(1));
            slot.setVeranstaltung(v);
            slot.persist();
            sid[0] = slot.getId();

            Referent r = new Referent();
            r.setEmail("ref@test.de");
            r.setPasswordHash("hash");
            r.addVeranstaltung(v);
            r.persist();
            rid[0] = r.getId();

            Verfuegbarkeit vf = new Verfuegbarkeit(r, slot, true);
            vf.persist();
        });

        // GET Test
        given()
                .when().get("/api/admin/veranstaltungen/{vid}/verfuegbarkeiten", vid[0])
                .then()
                .statusCode(OK.getStatusCode())
                .body("size()", is(1))
                .body("[0].userId", is(rid[0].intValue()))
                .body("[0].isAvailable", is(true));

        // POST Test (Update)
        VerfuegbarkeitDto updateDto = new VerfuegbarkeitDto(rid[0], sid[0], false);
        given()
                .contentType(ContentType.JSON)
                .body(updateDto)
                .when().post("/api/admin/veranstaltungen/{vid}/verfuegbarkeiten", vid[0])
                .then()
                .statusCode(OK.getStatusCode());

        // Verifizieren
        QuarkusTransaction.requiringNew().run(() -> {
            Referent r = Referent.findById(rid[0]);
            EventSlot s = EventSlot.findById(sid[0]);
            Verfuegbarkeit updated = Verfuegbarkeit.find("nutzer = ?1 and slot = ?2", r, s).firstResult();
            assertThat(updated.isAvailable()).isFalse();
        });
    }

    @Test
    void testOptimisticLockingFuerTeilnehmerProfil() {
        final Long[] teilnehmerId = new Long[1];
        final String teilnehmerEmail = "teilnehmer@example.com";
        final String teilnehmerPassword = "password";

        // 1. Create a teilnehmer
        QuarkusTransaction.requiringNew().run(() -> {
            Teilnehmer t = new Teilnehmer();
            t.setEmail(teilnehmerEmail);
            t.setPasswordHash(teilnehmerPassword);
            t.setFirstName("Original");
            t.setLastName("Name");
            t.persist();
            teilnehmerId[0] = t.getId();
        });

        // 2. Admin 1 fetches teilnehmer data (initial version)
        Long tnId = teilnehmerId[0];
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
    void testOptimisticLockingForVeranstaltungName() {
        final Long[] veranstaltungId = new Long[1];
        final String admin2Email = "admin2@example.com";

        // 1. Create a Veranstaltung
        QuarkusTransaction.requiringNew().run(() -> {
            Veranstaltung v = new Veranstaltung();
            v.setName ("Original Event Name");
            v.setBeginntAm(LocalDateTime.now().plusDays(1));
            v.setEndetAm(LocalDateTime.now().plusDays(2));
            v.persist();
            veranstaltungId[0] = v.getId();

            Admin admin2 = new Admin();
            admin2.setEmail(admin2Email);
            admin2.setPasswordHash("hash");
            admin2.persist();
        });


        given()
                .when().get("/api/veranstaltungen")
                .then()
                .statusCode(OK.getStatusCode())
                .extract().body().jsonPath().getList(".", VeranstaltungDto.class);

        // 2. Admin 1 (via @TestSecurity) fetches veranstaltung data
        Long vid = veranstaltungId[0];
        VeranstaltungDto admin1FetchedVeranstaltung = given()
                .when().get("/api/veranstaltungen/{id}", vid)
                .then()
                .statusCode(OK.getStatusCode())
                .extract().as(VeranstaltungDto.class);

        // 3. Admin 2 (via JWT token) fetches veranstaltung data
        String admin2Token = JwtHelper.tokenFor(admin2Email, "ADMIN");
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