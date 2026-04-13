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

        // 2. Zyklus User <-> Veranstaltung aufbrechen
        User.update("veranstaltung = null");
//        Veranstaltung.update("organisator = null");

        // 3. Jetzt können wir alles löschen
        Veranstaltung.deleteAll();
        User.deleteAll();
        Gebaeude.deleteAll();

        // 4. Test-Daten neu aufbauen
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
        v.organisator = admin;
        v.persist();
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
                    "biography": "Lange Erfahrung im TV."
                }
                """;

        given()
                .contentType(ContentType.JSON)
                .body(json)
                .when().post("/api/veranstaltungen/{vid}/benutzer", testVid)
                .then()
                .statusCode(201)
                .body("role", is("REFERENT"));

        Referent ref = (Referent) User.findByEmail("referent@test.de");
        Assertions.assertNotNull(ref);
        Assertions.assertNotNull(ref.getVeranstaltung(), "Veranstaltung sollte gesetzt sein");
        Assertions.assertEquals(testVid, ref.getVeranstaltung().id);
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
                    "gruppe": "10a"
                }
                """;

        given()
                .contentType(ContentType.JSON)
                .body(json)
                .when().post("/api/veranstaltungen/{vid}/benutzer", testVid)
                .then()
                .statusCode(201)
                .body("role", is("TEILNEHMER"));

        Teilnehmer teil = (Teilnehmer) User.findByEmail("schueler@test.de");
        Assertions.assertNotNull(teil);
        Assertions.assertEquals("10a", teil.gruppe);
        Assertions.assertNotNull(teil.getVeranstaltung(), "Veranstaltung sollte gesetzt sein");
        Assertions.assertEquals(testVid, teil.getVeranstaltung().id);
    }
}
