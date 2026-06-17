package kreyj.konfplan.presentation;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.h2.H2DatabaseTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import jakarta.transaction.Transactional;
import kreyj.konfplan.persistence.Admin;
import kreyj.konfplan.persistence.Gebaeude;
import kreyj.konfplan.persistence.Gebaeudetyp;
import kreyj.konfplan.persistence.Nutzer;
import kreyj.konfplan.persistence.Referent;
import kreyj.konfplan.persistence.Teilnehmer;
import kreyj.konfplan.persistence.Veranstaltung;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static io.restassured.RestAssured.given;
import static jakarta.ws.rs.core.Response.Status.CREATED;
import static jakarta.ws.rs.core.Response.Status.OK;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.matchesPattern;

@QuarkusTest
@QuarkusTestResource(H2DatabaseTestResource.class)
class NutzerPersistenceTest extends DatabaseCleaner {

    public static final String TEST_VERANSTALTUNG = "Test Veranstaltung";
    Long testVid;

    @BeforeEach
    @Transactional
    void setup() {
        Gebaeude g = new Gebaeude();
        g.setName("Test Gebäude");
        g.setStrasse("Teststraße");
        g.setOrt("Testort");
        g.setPostleitzahl("12345");
        g.setTyp(Gebaeudetyp.SCHULE);
        g.persist();

        Admin admin = new Admin();
        admin.setEmail("organisator@test.de");
        admin.setPasswordHash("hash");
        admin.persist();

        Veranstaltung v = new Veranstaltung();
        v.setName(TEST_VERANSTALTUNG + "_" + System.currentTimeMillis());
        v.setBeginntAm(LocalDateTime.now());
        v.addGebaeude(g);
        v.persist();
        testVid = v.getId();

        admin.addVeranstaltung(v);
        admin.persist();
    }

    @Test
    @TestSecurity(user = "admin@test.de", roles = "ADMIN")
    void testVeranstaltungPresent() {
        given()
                .when().get("/api/veranstaltungen/{vid}", testVid)
                .then()
                .statusCode(OK.getStatusCode())
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
                .when().post("/api/veranstaltungen/{vid}/nutzer", testVid)
                .then()
                .statusCode(CREATED.getStatusCode())
                .body("role", is("REFERENT"));

        Referent ref = (Referent) Nutzer.findByEmail("referent@test.de");
        assertThat(ref).isNotNull();
        assertThat(ref.getVeranstaltungen()).describedAs("Veranstaltungen sollten nicht leer sein").isNotNull();
        assertThat(testVid).isEqualTo(ref.getVeranstaltungen().iterator().next().getId());
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
                    "gruppen": ["10a"]
                }
                """;

        given()
                .contentType(ContentType.JSON)
                .body(json)
                .when().post("/api/veranstaltungen/{vid}/nutzer", testVid)
                .then()
                .statusCode(CREATED.getStatusCode())
                .body("role", is("TEILNEHMER"));

        Teilnehmer tn = (Teilnehmer) Nutzer.findByEmail("schueler@test.de");
        assertThat(tn).isNotNull();
        assertThat(tn.getGruppen().contains("10a"));
        assertThat(tn.getVeranstaltungen().isEmpty()).describedAs("Veranstaltung sollten nicht leer sein").isFalse();
        assertThat(testVid).isEqualTo(tn.getVeranstaltungen().iterator().next().getId());
    }
}
