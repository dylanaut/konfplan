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

@QuarkusTest
class UserPersistenceTest {

    Long testVid;

    @BeforeEach
    @Transactional
    void setup() {
        Prioritaet.deleteAll();
        Verfuegbarkeit.deleteAll();
        Vortrag.deleteAll();
        Gebaeude.deleteAll();
        EventSlot.deleteAll();
        User.deleteAll();
        Veranstaltung.deleteAll();

        // Gebäude für die Tests anlegen

        Gebaeude g = new Gebaeude();
        g.name = "Test Ort";
        g.typ = Gebaeude.Gebaeudetyp.SCHULE;

        // Basis-Veranstaltung für die Tests anlegen
        Veranstaltung v = new Veranstaltung();
        v.name = "Test Event";
        v.beginntAm = LocalDateTime.now();
        v.gebaeude = List.of(g);
        
        // Admin für die Veranstaltung (organisator)
        Admin admin = new Admin();
        admin.email = "organisator@test.de";
        admin.passwordHash = "hash";
        admin.persist();
        
        v.organisator = admin;
        v.persist();
        testVid = v.id;
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
        Assertions.assertEquals(testVid, ref.veranstaltung.id);
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
        Assertions.assertEquals(testVid, teil.veranstaltung.id);
    }
}
