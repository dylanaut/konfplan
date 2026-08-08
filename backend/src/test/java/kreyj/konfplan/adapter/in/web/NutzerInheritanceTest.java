package kreyj.konfplan.adapter.in.web;

import io.quarkus.test.InjectMock;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.common.http.TestHTTPEndpoint;
import io.quarkus.test.h2.H2DatabaseTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import kreyj.konfplan.domain.service.KeycloakUserProvisioningService;
import io.restassured.http.ContentType;
import jakarta.transaction.Transactional;
import kreyj.konfplan.persistence.Admin;
import kreyj.konfplan.persistence.Nutzer;
import kreyj.konfplan.persistence.Referent;
import kreyj.konfplan.persistence.Teilnehmer;
import kreyj.konfplan.persistence.Veranstaltung;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static io.restassured.RestAssured.given;
import static jakarta.ws.rs.core.Response.Status.CREATED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@QuarkusTest
@QuarkusTestResource(H2DatabaseTestResource.class)
@TestHTTPEndpoint(VeranstaltungResource.class)
class NutzerInheritanceTest extends DatabaseCleaner {

    @InjectMock
    KeycloakUserProvisioningService keycloakUserProvisioningService;

    Long testVid;


    @BeforeEach
    @Transactional
    void setup() {
        Admin admin = new Admin();
        admin.assignLoginName("org");
        admin.setEmail("org@test.de");
        admin.persist();

        Veranstaltung v = new Veranstaltung();
        v.setName("Inheritance Test Event " + System.currentTimeMillis());
        v.setBeginntAm(LocalDateTime.now());
        v.persist();
        testVid = v.getId();

        admin.addVeranstaltung(v);
        admin.persist();
    }


    @Test
    @TestSecurity(user = "admin@test.de", roles = "ADMIN")
    public void testPersistReferentViaJson() {
        String json = """
                {
                    "role": "REFERENT",
                    "loginName": "expert",
                    "email": "expert@konfplan.de",
                    "firstName": "Max",
                    "lastName": "Mustermann",
                    "jobRole": "Software Architekt",
                    "biography": "Langjährige Erfahrung in Java.",
                    "isActive": true
                }""";

        given()
                .contentType(ContentType.JSON)
                .body(json)
                .when().post("/{vid}/nutzer", testVid)
                .then()
                .statusCode(CREATED.getStatusCode());

        Referent ref = (Referent) Nutzer.findByEmail("expert@konfplan.de");
        assertNotNull(ref, "Referent sollte in der DB existieren");
        assertThat(ref.getVeranstaltungen())
                .describedAs("Veranstaltung des Referenten sollte nicht leer sein").isNotEmpty();
        assertThat(ref.getVeranstaltungen().iterator().next().getId()).isEqualTo(testVid);
    }


    @Test
    @TestSecurity(user = "admin@test.de", roles = "ADMIN")
    public void testPersistTeilnehmerViaJson() {
        String json = """
                {
                    "role": "TEILNEHMER",
                    "loginName": "student",
                    "email": "student@konfplan.de",
                    "firstName": "Lukas",
                    "lastName": "Lernbereit",
                    "role": "TEILNEHMER",
                    "gruppen": ["10.3"],
                    "isActive": true
                }""";

        given()
                .contentType(ContentType.JSON)
                .body(json)
                .when().post("/{vid}/nutzer", testVid)
                .then()
                .statusCode(CREATED.getStatusCode());

        Teilnehmer tn = (Teilnehmer) Nutzer.findByEmail("student@konfplan.de");
        assertNotNull(tn, "Teilnehmer sollte in der DB existieren");
        assertNotNull(Veranstaltung.findById(testVid), "Veranstaltung %d sollte in der DB existieren".formatted(testVid));
        assertNotNull(tn.getVeranstaltungen(), "Veranstaltung des Teilnehmers sollte nicht leer sein");
        assertThat(tn.getVeranstaltungen().iterator().next().getId()).isEqualTo(testVid);
        assertThat(tn.getGruppen()).contains("10.3");
    }
}
