package kreyj.konfplan.adapter.in.web;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.common.http.TestHTTPEndpoint;
import io.quarkus.test.h2.H2DatabaseTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.InjectMock;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import jakarta.transaction.Transactional;
import kreyj.konfplan.adapter.in.web.dto.OrganisatorPasswordResetDto;
import kreyj.konfplan.adapter.in.web.dto.NutzerDto;
import kreyj.konfplan.adapter.in.web.dto.NutzerVerfuegbarkeitDto;
import kreyj.konfplan.domain.service.KeycloakUserProvisioningService;
import kreyj.konfplan.persistence.Organisator;
import kreyj.konfplan.persistence.Nutzer;
import kreyj.konfplan.persistence.NutzerVerfuegbarkeit;
import kreyj.konfplan.persistence.Referent;
import kreyj.konfplan.persistence.Slot;
import kreyj.konfplan.persistence.Teilnehmer;
import kreyj.konfplan.persistence.Veranstaltung;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static io.restassured.RestAssured.given;
import static jakarta.ws.rs.core.Response.Status.BAD_REQUEST;
import static jakarta.ws.rs.core.Response.Status.CONFLICT;
import static jakarta.ws.rs.core.Response.Status.CREATED;
import static jakarta.ws.rs.core.Response.Status.FORBIDDEN;
import static jakarta.ws.rs.core.Response.Status.NOT_FOUND;
import static jakarta.ws.rs.core.Response.Status.NO_CONTENT;
import static jakarta.ws.rs.core.Response.Status.OK;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;
import static kreyj.konfplan.persistence.NutzerVerfuegbarkeitId.nvIdL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@QuarkusTest
@TestSecurity(user = "admin@example.com", roles = "ORGANISATOR")
@QuarkusTestResource(H2DatabaseTestResource.class)
@TestHTTPEndpoint(OrganisatorResource.class)
class OrganisatorResourceTest extends DatabaseCleaner {

    @InjectMock
    KeycloakUserProvisioningService keycloakUserProvisioningService;

    Long adminId;


    @BeforeEach
    @Transactional
    void setup() {
        Organisator admin = new Organisator();
        admin.assignLoginName("admin@example.com");
        admin.setEmail("admin@example.com");
        admin.persist();

        adminId = admin.getId();
    }


    @Test
    void testGetAllUsersGlobal() {
        QuarkusTransaction.requiringNew().run(() -> {
            Organisator a = new Organisator();
            a.assignLoginName("admin1@example.com");
            a.setEmail("admin1@example.com");
            a.persist();
        });

        given()
            .when().get("/nutzer")
            .then()
            .statusCode(OK.getStatusCode())
            .contentType(ContentType.JSON)
            .body("size()", is(2)); // setup admin + admin1
    }


    @Test
    void testCreateUser() {
        NutzerDto dto = NutzerDto.referent("new@test.de", "Max", "Mustermann");
        dto.loginName = "newtest";

        given().contentType(ContentType.JSON)
            .body(dto)
            .when().post("/nutzer")
            .then()
            .statusCode(OK.getStatusCode())
            .body("email", is("new@test.de"));

        assertThat(Nutzer.findByEmail("new@test.de")).isNotNull();
    }


    @Test
    void testUpdateUser() {
        String oldEmail = "old@test.de";
        NutzerDto dto = NutzerDto.teilnehmer(oldEmail, null, null);
        dto.loginName = "oldtest";
        NutzerDto created =
            given().contentType(ContentType.JSON)
                .body(dto)
                .when().post("/nutzer")
                .then()
                .statusCode(OK.getStatusCode())
                .extract().as(NutzerDto.class);

        assertThat(created.email).isEqualTo(oldEmail);
        assertThat(created.version).isEqualTo(0L);

        String newEmail = "updated@test.de";
        NutzerDto tnUpdate = NutzerDto.teilnehmer(newEmail, "Max", "Mustermann", List.of("New Group"), emptyList());
        tnUpdate.version = created.version;

        NutzerDto updated =
            given().contentType(ContentType.JSON)
                .body(tnUpdate)
                .when().put("/nutzer/{id}", created.id)
                .then()
                .statusCode(OK.getStatusCode())
                .extract().as(NutzerDto.class);

        // Organisator-getriebene E-Mail-Aenderung wird direkt uebernommen (keine Bestaetigung mehr
        // noetig - das Self-Service-Aequivalent laeuft jetzt ueber Keycloaks Account-Console).
        assertThat(updated.email).isEqualTo(newEmail);

        Nutzer user = Nutzer.findById(updated.id);
        assertThat(user.getEmail()).isEqualTo(newEmail);
        assertThat(((Teilnehmer) user).getGruppen()).contains("New Group");
        verify(keycloakUserProvisioningService).updateUser(user);
    }


    @Test
    void testResetPassword() {
        NutzerDto dto = NutzerDto.teilnehmer("reset.me@test.de", "Reset", "Me");
        dto.loginName = "reset.me";
        NutzerDto created =
            given().contentType(ContentType.JSON)
                .body(dto)
                .when().post("/nutzer")
                .then()
                .statusCode(OK.getStatusCode())
                .extract().as(NutzerDto.class);

        given()
            .contentType(ContentType.JSON)
            .body(new OrganisatorPasswordResetDto("einNeuesPasswort123"))
            .when().post("/nutzer/{id}/reset-password", created.id)
            .then()
            .statusCode(OK.getStatusCode());

        Nutzer updated = Nutzer.findById(created.id);
        verify(keycloakUserProvisioningService).resetPassword(eq(updated), eq("einNeuesPasswort123"));
    }


    @Test
    void testResetPassword_UnknownUser_ReturnsNotFound() {
        given()
            .contentType(ContentType.JSON)
            .body(new OrganisatorPasswordResetDto("einNeuesPasswort123"))
            .when().post("/nutzer/{id}/reset-password", -1L)
            .then()
            .statusCode(NOT_FOUND.getStatusCode());
    }


    @Test
    void testCreateUser_OrganisatorWithoutEmail_IsRejected() {
        NutzerDto dto = new NutzerDto("ORGANISATOR", null, "Ohne", "Email", true);
        dto.loginName = "ohne.email.rest";

        given().contentType(ContentType.JSON)
            .body(dto)
            .when().post("/nutzer")
            .then()
            .statusCode(BAD_REQUEST.getStatusCode());

        assertThat(Nutzer.findByLoginName("ohne.email.rest")).isNull();
    }


    @Test
    void testDeleteUser() {
        NutzerDto dto = NutzerDto.teilnehmer("todelete@test.de", null, null);
        dto.loginName = "todelete";

        NutzerDto created =
            given().contentType(ContentType.JSON)
                .body(dto)
                .when().post("/nutzer")
                .then()
                .statusCode(OK.getStatusCode())
                .extract().as(NutzerDto.class);

        given()
            .when()
            .delete("/nutzer/{id}", created.id)
            .then()
            .statusCode(NO_CONTENT.getStatusCode());

        Assertions.assertNull(Nutzer.findById(created.id));
    }


    @Test
    void testInviteUser() {
        Long[] userIdArray = {0L};
        Long[] vIdArray = {0L};

        QuarkusTransaction.requiringNew().run(() -> {
            Teilnehmer t1 = new Teilnehmer();
            t1.assignLoginName("invite");
            t1.setEmail("invite@test.de");
            t1.persist();
            userIdArray[0] = t1.getId();

            Veranstaltung v = new Veranstaltung();
            v.setName("Invite Event");
            v.setBeginntAm(LocalDateTime.now().plusDays(1));
            v.persist();
            vIdArray[0] = v.getId();

            Organisator orga = Organisator.findById(adminId);
            orga.addVeranstaltung(v);
        });

        Long userId = userIdArray[0];
        Long eventId = vIdArray[0];

        given()
            .contentType(ContentType.JSON)
            .when().post("/nutzer/{userId}/einladen/{eventId}", userId, eventId)
            .then()
            .statusCode(OK.getStatusCode());

        Teilnehmer invitedUser = Teilnehmer.findById(userId);
        assertThat(invitedUser.getVeranstaltungen()).hasSize(1);
    }


    @Test
    void testVerfuegbarkeitenEndpoints() {
        LocalDateTime now = LocalDateTime.now();
        final Long[] vId = {0L};
        final Long[] refId = {0L};
        final Long[] slotId = {0L};

        QuarkusTransaction.requiringNew().run(() -> {
            Veranstaltung v = new Veranstaltung();
            v.setName("Event " + System.currentTimeMillis());
            v.setBeginntAm(now);
            v.setEndetAm(now.plusHours(2));
            v.persist();
            vId[0] = v.getId();

            Slot slot = new Slot("Slot 1", now, now.plusHours(1), v);
            slot.persist();
            slotId[0] = slot.getId();
            v.addSlot(slot);
            v.persist();

            Referent referent = new Referent();
            referent.assignLoginName("ref");
            referent.setEmail("ref@test.de");

            referent.persist();
            refId[0] = referent.getId();

            referent.addVeranstaltung(v);
        });

        // GET Test
        given()
            .when().get("/veranstaltungen/{vid}/verfuegbarkeiten", vId[0])
            .then()
            .statusCode(OK.getStatusCode())
            .body("size()", is(1))
            .body("[0].nutzerId", is(refId[0].intValue()));

        // POST Test (Update)
        NutzerVerfuegbarkeitDto updateDto = new NutzerVerfuegbarkeitDto(refId[0], vId[0], emptySet());
        given()
            .contentType(ContentType.JSON)
            .body(updateDto)
            .when().post("/veranstaltungen/{vid}/verfuegbarkeiten", vId[0])
            .then()
            .statusCode(OK.getStatusCode());

        // Verifizieren
        NutzerVerfuegbarkeit nv = NutzerVerfuegbarkeit.findById(nvIdL(refId[0], vId[0]));

        assertThat(nv.isVerfuegbar(slotId[0])).isFalse();
    }


    @Test
    void testOptimisticLockingFuerTeilnehmerUpdate() {
        Long[] tnId = {0L};

        QuarkusTransaction.requiringNew().run(() -> {
            Teilnehmer t = new Teilnehmer();
            t.assignLoginName("teilnehmerexample");
            t.setEmail("teilnehmer@example.com");
            t.setFirstName("Original");
            t.setLastName("Name");
            t.persist();
            tnId[0] = t.getId();
        });

        // 2. Organisator 1 fetches teilnehmer data (initial version)
        NutzerDto adminFetchedUser1 = given()
            .when().get("/nutzer/{id}", tnId[0])
            .then()
            .statusCode(OK.getStatusCode())
            .extract().as(NutzerDto.class);

        // 3. Organisator 2 fetches the same participant again (simulating concurrent user/tab)
        NutzerDto adminFetchedUser2 = given()
            .when().get("/nutzer/{id}", tnId[0])
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
            .when().put("/nutzer/{id}", tnId[0])
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
            .when().put("/nutzer/{id}", tnId[0])
            .then()
            .statusCode(CONFLICT.getStatusCode()); // Expect conflict

        // 6. Verify data integrity: only the first update should be present
        NutzerDto finalUser = given()
            .when().get("/nutzer/{id}", tnId[0])
            .then()
            .statusCode(OK.getStatusCode())
            .extract().as(NutzerDto.class);

        assertThat(finalUser.firstName).isEqualTo("AdminUpdated");
        assertThat(finalUser.lastName).isEqualTo("Name"); // The second change should not be applied
        assertThat(finalUser.version).isEqualTo(adminFetchedUser1.version + 1); // Version should be the one after the first update
    }


    @Test
    @TestSecurity(user = "nutzer@example.com", roles = "USER")
    void testGlobalAccessForbidden() {
        given()
            .when().get("/nutzer")
            .then()
            .statusCode(FORBIDDEN.getStatusCode());
    }


    // --- GRUPPEN API TESTS ---
    @Test
    void testGruppenApiLifecycle() {
        Long vId = QuarkusTransaction.requiringNew().call(() -> {
            Veranstaltung v = new Veranstaltung();
            v.setName("Gruppen Test Event");
            v.setBeginntAm(LocalDateTime.now());
            v.setEndetAm(LocalDateTime.now().plusDays(1));
            v.persist();
            return v.getId();
        });

        // 1. Create a new group
        given()
            .contentType(ContentType.JSON)
            .body("Gruppe Alpha")
            .when().post("/veranstaltungen/{vid}/gruppen", vId)
            .then()
            .statusCode(CREATED.getStatusCode());

        // 2. Get all groups and verify
        given()
            .when().get("/veranstaltungen/{vid}/gruppen", vId)
            .then()
            .statusCode(OK.getStatusCode())
            .body("size()", is(1))
            .body("", containsInAnyOrder("Gruppe Alpha"));

        // 3. Rename the group
        given()
            .queryParam("alterName", "Gruppe Alpha")
            .queryParam("neuerName", "Gruppe Bravo")
            .when().put("/veranstaltungen/{vid}/gruppen", vId)
            .then()
            .statusCode(NO_CONTENT.getStatusCode());

        // 4. Verify the rename
        given()
            .when().get("/veranstaltungen/{vid}/gruppen", vId)
            .then()
            .statusCode(OK.getStatusCode())
            .body("size()", is(1))
            .body("", containsInAnyOrder("Gruppe Bravo"));

        // 5. Delete the group
        given()
            .when().delete("/veranstaltungen/{vid}/gruppen/{gruppenName}", vId, "Gruppe Bravo")
            .then()
            .statusCode(NO_CONTENT.getStatusCode());

        // 6. Verify the deletion
        given()
            .when().get("/veranstaltungen/{vid}/gruppen", vId)
            .then()
            .statusCode(OK.getStatusCode())
            .body("size()", is(0));
    }
}
