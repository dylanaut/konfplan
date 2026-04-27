package kreyj.vortragsmanager.resource;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.h2.H2DatabaseTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import jakarta.transaction.Transactional;
import kreyj.vortragsmanager.dto.UserDto;
import kreyj.vortragsmanager.dto.VerfuegbarkeitDto;
import kreyj.vortragsmanager.entity.*;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;

@QuarkusTest
@TestSecurity(user = "admin@example.com", roles = "ADMIN")
@QuarkusTestResource(H2DatabaseTestResource.class)
class AdminResourceTest {

    @BeforeEach
    @Transactional
    void setup() {
        Zuweisung.deleteAll();
        Verfuegbarkeit.deleteAll();
        RaumVerfuegbarkeit.deleteAll();
        Vortrag.deleteAll();
        Nutzer.deleteAll();
        Raum.deleteAll();
        Gebaeude.deleteAll();
        EventSlot.deleteAll();
        Veranstaltung.deleteAll();
        
        Admin admin = new Admin();
        admin.email = "admin@example.com";
        admin.passwordHash = "hash";
        admin.persist();
    }

    @Test
    void testGetAllUsersGlobal() {
        QuarkusTransaction.run(() -> {
            Admin a = new Admin();
            a.email = "admin1@example.com";
            a.persist();
        });

        given()
                .when().get("/api/admin/nutzer")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("size()", is(2)); // setup admin + admin1
    }

    @Test
    void testCreateUser() {
        UserDto dto = new UserDto();
        dto.email = "new@test.de";
        dto.role = "REFERENT";
        dto.firstName = "Max";
        dto.lastName = "Mustermann";

        given()
                .contentType(ContentType.JSON)
                .body(dto)
                .when().post("/api/admin/nutzer")
                .then()
                .statusCode(200)
                .body("email", is("new@test.de"));
        
        Assertions.assertNotNull(Nutzer.findByEmail("new@test.de"));
    }

    @Test
    void testUpdateUser() {
        final Long[] userId = new Long[1];
        QuarkusTransaction.run(() -> {
            Teilnehmer t = new Teilnehmer();
            t.email = "old@test.de";
            t.persist();
            userId[0] = t.id;
        });

        UserDto dto = new UserDto();
        dto.email = "updated@test.de";
        dto.role = "TEILNEHMER";
        dto.firstName = "Max";
        dto.lastName = "Mustermann";

        given()
                .contentType(ContentType.JSON)
                .body(dto)
                .when().put("/api/admin/nutzer/{id}", userId[0])
                .then()
                .statusCode(200)
                .body("email", is("updated@test.de"));
        
        Assertions.assertNotNull(Nutzer.findByEmail("updated@test.de"));
    }

    @Test
    void testDeleteUser() {
        final Long[] userId = new Long[1];
        QuarkusTransaction.run(() -> {
            Teilnehmer t = new Teilnehmer();
            t.email = "todelete@test.de";
            t.persist();
            userId[0] = t.id;
        });

        given()
                .when().delete("/api/admin/nutzer/{id}", userId[0])
                .then()
                .statusCode(204);

        Assertions.assertNull(Nutzer.findById(userId[0]));
    }

    @Test
    void testInviteUser() {
        final Long[] userId = new Long[1];
        final Long[] eventId = new Long[1];

        QuarkusTransaction.run(() -> {
            Teilnehmer t = new Teilnehmer();
            t.email = "invite@test.de";
            t.persist();
            userId[0] = t.id;

            Veranstaltung v = new Veranstaltung();
            v.name = "Invite Event";
            v.beginntAm = LocalDateTime.now().plusDays(1);
            v.persist();
            eventId[0] = v.id;
        });

        given()
                .contentType(ContentType.JSON)
                .when().post("/api/admin/nutzer/{userId}/einladen/{eventId}", userId[0], eventId[0])
                .then()
                .statusCode(200);

        QuarkusTransaction.run(() -> {
            Teilnehmer t = Teilnehmer.findById(userId[0]);
            Assertions.assertEquals(1, t.veranstaltungen.size());
        });
    }

    @Test
    void testVerfuegbarkeitenEndpoints() {
        final Long[] vid = new Long[1];
        final Long[] rid = new Long[1];
        final Long[] sid = new Long[1];

        QuarkusTransaction.run(() -> {
            Veranstaltung v = new Veranstaltung();
            v.name = "Event " + System.currentTimeMillis();
            v.beginntAm = LocalDateTime.now();
            v.persist();
            vid[0] = v.id;

            EventSlot slot = new EventSlot();
            slot.description = "Slot 1";
            slot.startTime = LocalDateTime.now();
            slot.endTime = LocalDateTime.now().plusHours(1);
            slot.veranstaltung = v;
            slot.persist();
            sid[0] = slot.id;

            Referent r = new Referent();
            r.email = "ref@test.de";
            r.passwordHash = "hash";
            r.addVeranstaltung(v);
            r.persist();
            rid[0] = r.id;

            Verfuegbarkeit vf = new Verfuegbarkeit();
            vf.nutzer = r;
            vf.slot = slot;
            vf.isAvailable = true;
            vf.persist();
        });

        // GET Test
        given()
                .when().get("/api/admin/veranstaltung/{vid}/verfuegbarkeiten", vid[0])
                .then()
                .statusCode(200)
                .body("size()", is(1))
                .body("[0].userId", is(rid[0].intValue()))
                .body("[0].isAvailable", is(true));

        // POST Test (Update)
        VerfuegbarkeitDto updateDto = new VerfuegbarkeitDto(rid[0], sid[0], false);
        given()
                .contentType(ContentType.JSON)
                .body(updateDto)
                .when().post("/api/admin/veranstaltung/{vid}/verfuegbarkeiten", vid[0])
                .then()
                .statusCode(200);

        // Verifizieren
        QuarkusTransaction.run(() -> {
            Referent r = Referent.findById(rid[0]);
            EventSlot s = EventSlot.findById(sid[0]);
            Verfuegbarkeit updated = Verfuegbarkeit.find("nutzer = ?1 and slot = ?2", r, s).firstResult();
            Assertions.assertFalse(updated.isAvailable);
        });
    }

    @Test
    @TestSecurity(user = "nutzer@example.com", roles = "USER")
    void testGlobalAccessForbidden() {
        given()
                .when().get("/api/admin/nutzer")
                .then()
                .statusCode(403);
    }
}
