package kreyj.vortragsmanager.resource;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.h2.H2DatabaseTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import jakarta.transaction.Transactional;
import kreyj.vortragsmanager.dto.UserDto;
import kreyj.vortragsmanager.entity.*;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static io.restassured.RestAssured.given;
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
        QuarkusTransaction.run(() -> {
            createWahlvortrag("Test Vortrag");
        });
        
        given()
                .when().get("/api/veranstaltungen/{vid}/vortraege", testVid)
                .then()
                .statusCode(200)
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
        QuarkusTransaction.run(() -> {
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
                .statusCode(200)
                .body("size()", is(1))
                .body("[0].description", is("Slot A"));
    }

    @Test
    void testGetStatsHierarchical() {
        QuarkusTransaction.run(() -> {
            createWahlvortrag("Vortrag 1");
        });

        given()
                .when().get("/api/veranstaltungen/{vid}/stats", testVid)
                .then()
                .statusCode(200)
                .body("size()", is(1))
                .body("[0].titel", is("Vortrag 1"));
    }

    @Test
    @Transactional
    void testCreateNutzerHierarchical() {
        UserDto t = new UserDto();
        t.email = "new@test.de";
        t.role = "TEILNEHMER";
        t.firstName = "Neu";
        t.lastName = "Nutzer";

        given()
                .contentType(ContentType.JSON)
                .body(t)
                .when().post("/api/veranstaltungen/{vid}/nutzer", testVid)
                .then()
                .statusCode(201)
                .body("email", is("new@test.de"));
        
        Assertions.assertNotNull(Nutzer.findByEmail("new@test.de"));
    }
}
