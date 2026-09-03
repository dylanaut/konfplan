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
import kreyj.konfplan.persistence.Nutzer;
import kreyj.konfplan.persistence.NutzerVerfuegbarkeit;
import kreyj.konfplan.persistence.Referent;
import kreyj.konfplan.persistence.Slot;
import kreyj.konfplan.persistence.Veranstaltung;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static io.restassured.RestAssured.given;
import static jakarta.ws.rs.core.Response.Status.CREATED;
import static kreyj.konfplan.persistence.NutzerVerfuegbarkeitId.nvId;
import static kreyj.konfplan.persistence.NutzerVerfuegbarkeitId.nvIdL;
import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
@QuarkusTestResource(H2DatabaseTestResource.class)
@TestHTTPEndpoint(VeranstaltungResource.class)
class NutzerVerfuegbarkeitResourceTest extends DatabaseCleaner {

    @InjectMock
    KeycloakUserProvisioningService keycloakUserProvisioningService;

    Long testVid;
    Long slotId;


    @BeforeEach
    @Transactional
    void setup() {
        LocalDateTime now = LocalDateTime.now();

        Veranstaltung v = new Veranstaltung();
        v.setName("Test Event");
        v.setBeginntAm(now);
        v.persist();
        testVid = v.getId();

        Slot s = new Slot("Slot 1", now, now.plusHours(1), v);
        s.persist();

        v.addSlot(s);
        slotId = s.getId();
    }


    @Test
    @TestSecurity(user = "admin@test.de", roles = "ORGANISATOR")
    void testVerfuegbarkeitCreatedOnAddNutzer() {
        String jsonReferent = """
                {
                    "role": "REFERENT",
                    "loginName": "referentverf",
                    "email": "referent@verf.de",
                    "firstName": "Ref",
                    "lastName": "Erent"
                }
                """;

        // Referent hinzufügen
        given()
                .contentType(ContentType.JSON)
                .body(jsonReferent)
                .when().post("/{vid}/nutzer", testVid)
                .then()
                .statusCode(CREATED.getStatusCode());

        Nutzer ref = Nutzer.findByEmail("referent@verf.de");
        NutzerVerfuegbarkeit nv = NutzerVerfuegbarkeit.findById(nvIdL(ref.getId(), testVid));
        assertThat(nv.getVerfuegbareSlotIds())
                .describedAs("Verfügbarkeit für Referent sollte erstellt worden sein")
                .contains(slotId);

        String jsonTeilnehmer = """
                {
                    "role": "TEILNEHMER",
                    "loginName": "schuelerverf",
                    "email": "schueler@verf.de",
                    "firstName": "Schü",
                    "lastName": "Ler"
                }
                """;

        // Teilnehmer hinzufügen
        given()
                .contentType(ContentType.JSON)
                .body(jsonTeilnehmer)
                .when().post("/{vid}/nutzer", testVid)
                .then()
                .statusCode(CREATED.getStatusCode());

        Nutzer tn = Nutzer.findByEmail("schueler@verf.de");
        nv = NutzerVerfuegbarkeit.findById(nvIdL(tn.getId(), testVid));
        assertThat(nv).describedAs("Verfügbarkeit für Teilnehmer sollte erstellt worden sein")
                .isNotNull();
    }


    @Test
    @Transactional
    void testVerfuegbarkeitRemovedOnRemoveNutzer() {
        Veranstaltung v = Veranstaltung.findById(testVid);

        Referent r = new Referent();
        r.assignLoginName("delverf");
        r.setEmail("del@verf.de");
        r.persist();

        r.addVeranstaltung(v);

        NutzerVerfuegbarkeit nv = NutzerVerfuegbarkeit.findById(nvId(r, v));
        assertThat(nv).isNotNull();

        r.removeVeranstaltung(v);

        nv = NutzerVerfuegbarkeit.findById(nvId(r, v));
        assertThat(nv)
                .describedAs("Verfügbarkeit sollte nach Entfernen des Nutzers gelöscht worden sein")
                .isNull();
    }
}
