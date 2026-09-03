package kreyj.konfplan.adapter.in.web;

import io.quarkus.test.InjectMock;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.common.http.TestHTTPEndpoint;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.h2.H2DatabaseTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import kreyj.konfplan.domain.service.KeycloakUserProvisioningService;
import jakarta.transaction.Transactional;
import kreyj.konfplan.persistence.Organisator;
import kreyj.konfplan.persistence.Gebaeude;
import kreyj.konfplan.persistence.Nutzer;
import kreyj.konfplan.persistence.Pflichtvortrag;
import kreyj.konfplan.persistence.Referent;
import kreyj.konfplan.persistence.Slot;
import kreyj.konfplan.persistence.Teilnehmer;
import kreyj.konfplan.persistence.Veranstaltung;
import kreyj.konfplan.persistence.Wahlvortrag;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.commons.util.ClassLoaderUtils;

import java.io.File;
import java.net.URL;
import java.util.Objects;

import static io.restassured.RestAssured.given;
import static jakarta.ws.rs.core.Response.Status.OK;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.CoreMatchers.containsString;

@QuarkusTest
@TestSecurity(user = "admin@test.de", roles = "ORGANISATOR")
@QuarkusTestResource(H2DatabaseTestResource.class)
@TestHTTPEndpoint(VeranstaltungResource.class)
class CsvFileImportTest extends DatabaseCleaner {

    @InjectMock
    KeycloakUserProvisioningService keycloakUserProvisioningService;
    public static final String CSV_DIR = "csv_import/import-test/";

    @TestHTTPResource
    @TestHTTPEndpoint(GebaeudeResource.class)
    URL gebaeudeEndpoint;

    @TestHTTPResource
    @TestHTTPEndpoint(OrganisatorResource.class)
    URL adminEndpoint;

    Long testVid;
    String orgEmail;


    @BeforeEach
    @Transactional
    void setupTransactional() {
        Organisator admin = new Organisator();
        admin.assignLoginName("admintest");
        admin.setEmail("admin@test.de");
        admin.persist();
    }


    @BeforeEach
    void setup() {
        setupVeranstalter();
        setupGebaeude();
        setupVeranstaltungen();
    }


    private void setupVeranstalter() {
        // import Veranstalter
        given()
            .baseUri(adminEndpoint.toString())
            .basePath("/organisatoren/import")
            .multiPart("file", getCsvFile("organisatoren.csv"))
            .when().post()
            .then()
            .statusCode(OK.getStatusCode());

        Organisator organisator = (Organisator) Nutzer.findByEmail("test.admin@rks-linz.de");
        assertThat(organisator).isNotNull();
        assertThat("Admin").isEqualTo(organisator.getFirstName());
        orgEmail = organisator.getEmail();
    }


    private void setupGebaeude() {
        given()
            .baseUri(gebaeudeEndpoint.toString())
            .basePath("/import")
            .multiPart("file", getCsvFile("gebaeude.csv"))
            .when().post()
            .then()
            .statusCode(OK.getStatusCode())
            .body(containsString("Import erfolgreich"));

        Gebaeude g = Gebaeude.find("name", "TestGebäude").firstResult();
        assertThat(g).isNotNull();
        assertThat(12).isEqualTo(g.getRaeume().size()).describedAs("Anzahl Räume sollte sein");
    }


    private void setupVeranstaltungen() {
        given()
            .multiPart("file", getCsvFile("veranstaltungen.csv"))
            .when()
            .post("/import")
            .then()
            .statusCode(OK.getStatusCode())
            .body(containsString("2 Veranstaltung(en) angelegt"));

        Veranstaltung v = Veranstaltung.find("name", "TV_1").firstResult();
        assertThat(v).isNotNull();
        testVid = v.getId();
    }


    @Test
    void testImportReferenten() {
        given()
            .multiPart("file", getCsvFile("referenten.csv"))
            .when().post("/{vid}/referenten/import", testVid)
            .then()
            .statusCode(OK.getStatusCode());

        Referent r = (Referent) Nutzer.findByEmail("juergenkreyalias-ref@yahoo.com");
        assertThat(r).isNotNull();
        assertThat("msg systems").isEqualTo(r.getOrganisation());
    }


    @Test
    void testImportTeilnehmer() {
        given()
            .multiPart("file", getCsvFile("teilnehmer.csv"))
            .when().post("/{vid}/teilnehmer/import", testVid)
            .then()
            .statusCode(OK.getStatusCode());

        Teilnehmer t = (Teilnehmer) Nutzer.findByEmail("hayal.yaldir@rks-linz.de");
        assertThat(t).isNotNull();
        assertThat(t.getGruppen()).contains("9.1");
    }


    @Test
    void testImportSlots() {
        given()
            .multiPart("file", getCsvFile("slots.csv"))
            .when().post("/{vid}/slots/import", testVid)
            .then()
            .statusCode(OK.getStatusCode());

        assertThat(Slot.count()).isEqualTo(12);
    }


    @Test
    void testImportVortraege() {
        testImportReferenten();
        testImportSlots();

        given()
            .multiPart("file", getCsvFile("wahl_vortraege.csv"))
            .when().post("/{vid}/vortraege/import", testVid)
            .then()
            .statusCode(OK.getStatusCode());

        assertThat(Wahlvortrag.count()).isEqualTo(16);
        Wahlvortrag wv = Wahlvortrag.find("titel", "Traumberuf Polizei?").firstResult();
        assertThat(wv).describedAs("Wahlvortrag sollte importiert worden sein").isNotNull();
        assertThat(wv.isWiederholbar()).isTrue();

        assertThat(0).isEqualTo(Pflichtvortrag.count());

        given()
            .multiPart("file", getCsvFile("pflicht_vortraege.csv"))
            .when().post("/{vid}/vortraege/import", testVid)
            .then()
            .statusCode(OK.getStatusCode());

        assertThat(18)
            .describedAs("Pflichtvortraege sollten importiert worden sein")
            .isEqualTo(Pflichtvortrag.count());
        Pflichtvortrag pv = Pflichtvortrag.find("titel", "Vortrag Arbeitsagentur für 10.5").firstResult();
        assertThat(pv).describedAs("Pflichtvortrag sollte importiert worden sein").isNotNull();
        assertThat("A-2.04").isEqualTo(pv.getPflichtraum().getName());
        assertThat("9").isEqualTo(pv.getPflichtslot().getDescription());
    }


    private static File getCsvFile(String fileName) {
        ClassLoader classLoader = ClassLoaderUtils.getClassLoader(CsvFileImportTest.class);
        URL resource = Objects.requireNonNull(classLoader.getResource(CSV_DIR + fileName));

        return new File(resource.getFile());
    }
}
