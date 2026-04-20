package kreyj.vortragsmanager.resource;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import jakarta.transaction.Transactional;
import kreyj.vortragsmanager.entity.*;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static io.restassured.RestAssured.given;
import static jakarta.ws.rs.core.Response.Status.CREATED;
import static jakarta.ws.rs.core.Response.Status.OK;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.matchesPattern;

@QuarkusTest
class UserPersistenceTest {

    public static final String TEST_VERANSTALTUNG = "Test Veranstaltung";
    Long testVid;

    @BeforeEach
    @Transactional
    void setup() {
        // 1. Abhängige Tabellen löschen
        Zuweisung.deleteAll();
        Prioritaet.deleteAll();
        Verfuegbarkeit.deleteAll();
        Vortrag.deleteAll();
        EventSlot.deleteAll();
        Raum.deleteAll();

        // 2. Jetzt können wir alles löschen
        Veranstaltung.deleteAll();
        User.deleteAll();
        Gebaeude.deleteAll();

        // 3. Test-Daten neu aufbauen
        Gebaeude g = new Gebaeude();
        g.name = "Test Gebäude";
        g.strasse = "Teststraße";
        g.ort = "Testort";
        g.postleitzahl = "12345";
        g.typ = Gebaeude.Gebaeudetyp.SCHULE;
        g.persist();

        Admin admin = new Admin();
        admin.email = "organisator@test.de";
        admin.passwordHash = "hash";
        admin.persist();

        Veranstaltung v = new Veranstaltung();
        v.name = TEST_VERANSTALTUNG + "_" + System.currentTimeMillis();
        v.beginntAm = LocalDateTime.now();
        v.gebaeude = List.of(g);
        v.persist();
        admin.addVeranstaltung(v);
        admin.persist();
        testVid = v.id;
    }

    @Test
    @TestSecurity(user = "admin@test.de", roles = "ADMIN")
    void testVeranstaltungPresent() {
        given()
                .when().get("/api/veranstaltungen/{vid}", testVid)
                .then()
                .statusCode(200)
                .body("name", matchesPattern(TEST_VERANSTALTUNG + "_\\d+"))
                .log().all();
    }

    @Test
    @TestSecurity(user = "admin@test.de", roles = "ADMIN")
    void testPersistReferentHierarchical() {
        String json = """
                {
                    "role": "REFERENT",
                    "email": "referent@test.de",
                    "firstName": "Jens",
                    "lastName": "Riewa",
                    "jobRole": "Nachrichtensprecher",
                    "biography": "Lange Erfahrung im TV.",
                    "veranstaltungIds": %s
                }
                """.formatted(List.of(testVid));

        given()
                .contentType(ContentType.JSON)
                .body(json)
                .when().post("/api/admin/benutzer")
                .then()
                .statusCode(CREATED.getStatusCode())
                .body("role", is("REFERENT"));

        Referent ref = (Referent) User.findByEmail("referent@test.de");
        Assertions.assertNotNull(ref);
        Assertions.assertNotNull(ref.veranstaltungen, "Veranstaltung sollte gesetzt sein");
        Assertions.assertEquals(testVid, ref.veranstaltungen.iterator().next().id);
    }

    @Test
    @TestSecurity(user = "admin@test.de", roles = "ADMIN")
    void testPersistTeilnehmerHierarchical() {
        String json = """
                {
                    "role": "TEILNEHMER",
                    "email": "schueler@test.de",
                    "firstName": "Peter",
                    "lastName": "Müller",
                    "gruppe": "10a",
                    "veranstaltungIds": %s
                }
                """.formatted(List.of(testVid));

        given()
                .contentType(ContentType.JSON)
                .body(json)
                .when().post("/api/admin/benutzer")
                .then()
                .statusCode(CREATED.getStatusCode())
                .body("role", is("TEILNEHMER"));

        Teilnehmer teil = (Teilnehmer) User.findByEmail("schueler@test.de");
        Assertions.assertNotNull(teil);
        Assertions.assertEquals("10a", teil.gruppe);
        Assertions.assertNotNull(teil.veranstaltungen, "Veranstaltung sollte gesetzt sein");
        Assertions.assertEquals(testVid, teil.veranstaltungen.iterator().next().id);
    }
}
