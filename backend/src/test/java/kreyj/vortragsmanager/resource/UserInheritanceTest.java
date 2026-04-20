package kreyj.vortragsmanager.resource;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.h2.H2DatabaseTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import jakarta.transaction.Transactional;
import kreyj.vortragsmanager.entity.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Collections;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
@QuarkusTestResource(H2DatabaseTestResource.class)
class UserInheritanceTest {

    Long testVid;

    @BeforeEach
    @Transactional
    void setup() {
        Vortrag.deleteAll();
        User.deleteAll();
        Raum.deleteAll();
        Gebaeude.deleteAll();
        EventSlot.deleteAll();
        Veranstaltung.deleteAll();

        Admin admin = new Admin();
        admin.email = "org@test.de";
        admin.passwordHash = "hash";
        admin.persist();

        Veranstaltung v = new Veranstaltung();
        v.name = "Inheritance Test Event " + System.currentTimeMillis();
        v.beginntAm = LocalDateTime.now();
        v.persist();
        testVid = v.id;

        admin.addVeranstaltung(v);
        admin.persist();
    }

    @Test
    @TestSecurity(user = "admin@test.de", roles = "ADMIN")
    public void testPersistReferentViaJson() {
        String json = """
                {
                    "role": "REFERENT",
                    "email": "expert@vortragsmanager.de",
                    "firstName": "Max",
                    "lastName": "Mustermann",
                    "jobRole": "Software Architekt",
                    "biography": "Langjährige Erfahrung in Java.",
                    "isActive": true
                }""";

        given()
                .contentType(ContentType.JSON)
                .body(json)
                .when().post("/api/veranstaltungen/{vid}/benutzer", testVid)
                .then()
                .statusCode(201);

        Referent ref = (Referent) User.findByEmail("expert@vortragsmanager.de");
        assertNotNull(ref, "Referent sollte in der DB existieren");
        assertNotEquals(Collections.emptyList(), ref.veranstaltungen, "Veranstaltung des Referenten sollte nicht leer sein");
        assertEquals(testVid, ref.veranstaltungen.iterator().next().id);
    }

    @Test
    @TestSecurity(user = "admin@test.de", roles = "ADMIN")
    public void testPersistTeilnehmerViaJson() {
        String json = """
                {
                    "role": "TEILNEHMER",
                    "email": "student@vortragsmanager.de",
                    "firstName": "Lukas",
                    "lastName": "Lernbereit",
                    "role": "TEILNEHMER",
                    "gruppe": "10.3",
                    "isActive": true
                }""";

        given()
                .contentType(ContentType.JSON)
                .body(json)
                .when().post("/api/veranstaltungen/{vid}/benutzer", testVid)
                .then()
                .log().ifStatusCodeIsEqualTo(201)
                .statusCode(201);

        Teilnehmer tn = (Teilnehmer) User.findByEmail("student@vortragsmanager.de");
        assertNotNull(tn, "Teilnehmer sollte in der DB existieren");
        assertNotNull(Veranstaltung.findById(testVid), "Veranstaltung %d sollte in der DB existieren".formatted(testVid));
        assertNotNull(tn.veranstaltungen, "Veranstaltung des Teilnehmers sollte nicht leer sein");
        assertEquals(testVid, tn.veranstaltungen.iterator().next().id);
        assertEquals("10.3", tn.gruppe);
    }
}
