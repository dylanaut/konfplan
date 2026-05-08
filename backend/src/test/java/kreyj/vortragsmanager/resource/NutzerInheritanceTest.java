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

import static io.restassured.RestAssured.given;
import static jakarta.ws.rs.core.Response.Status.CREATED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
@QuarkusTestResource(H2DatabaseTestResource.class)
class NutzerInheritanceTest extends ResourceTestBase {

    Long testVid;

    @BeforeEach
    @Transactional
    void setup() {
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
                .when().post("/api/veranstaltungen/{vid}/nutzer", testVid)
                .then()
                .statusCode(CREATED.getStatusCode());

        Referent ref = (Referent) Nutzer.findByEmail("expert@vortragsmanager.de");
        assertNotNull(ref, "Referent sollte in der DB existieren");
        assertThat(ref.veranstaltungen.isEmpty()).describedAs("Veranstaltung des Referenten sollte nicht leer sein").isFalse();
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
                .when().post("/api/veranstaltungen/{vid}/nutzer", testVid)
                .then()
                .statusCode(CREATED.getStatusCode());

        Teilnehmer tn = (Teilnehmer) Nutzer.findByEmail("student@vortragsmanager.de");
        assertNotNull(tn, "Teilnehmer sollte in der DB existieren");
        assertNotNull(Veranstaltung.findById(testVid), "Veranstaltung %d sollte in der DB existieren".formatted(testVid));
        assertNotNull(tn.veranstaltungen, "Veranstaltung des Teilnehmers sollte nicht leer sein");
        assertEquals(testVid, tn.veranstaltungen.iterator().next().id);
        assertEquals("10.3", tn.gruppe);
    }
}
