package kreyj.vortragsmanager.resource;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.transaction.Transactional;
import kreyj.vortragsmanager.entity.*;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.commons.util.ClassLoaderUtils;

import java.io.File;
import java.net.URL;
import java.util.Objects;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;

@QuarkusTest
@TestSecurity(user = "admin@test.de", roles = "ADMIN")
class CsvFileImportTest {

    public static final String CSV_DIR = "csv_import/bo_26_09/";
    Long testVid;
    String orgEmail;

    @BeforeEach
    @Transactional
    void setupTransactional() {
        Zuweisung.deleteAll();
        Prioritaet.deleteAll();
        Verfuegbarkeit.deleteAll();
        Vortrag.deleteAll();
        EventSlot.deleteAll();

        Veranstaltung.deleteAll();
        User.deleteAll();

        Raum.deleteAll();
        Gebaeude.deleteAll();

        Admin admin = new Admin();
        admin.email = "admin@test.de";
        admin.passwordHash = "hash";
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
                .multiPart("file", getCsvFile("test-organisatoren.csv"))
                .when().post("/api/admin/admins/import")
                .then()
                .statusCode(200);

        Admin organisator = (Admin) User.findByEmail("test.admin@rks-linz.de");
        Assertions.assertNotNull(organisator);
        Assertions.assertEquals("Admin", organisator.firstName);
        orgEmail = organisator.email;
    }

    private static void setupGebaeude() {
        given()
                .multiPart("file", getCsvFile("test-gebaeude.csv"))
                .when().post("/api/gebaeude/import")
                .then()
                .statusCode(200)
                .body(containsString("Import erfolgreich"));

        Gebaeude g = Gebaeude.find("name", "TestGebäude").firstResult();
        Assertions.assertNotNull(g);
        Assertions.assertEquals(12, g.raeume.size(), "Anzahl Räume sollte sein");
    }

    private void setupVeranstaltungen() {
        given()
                .multiPart("file", getCsvFile("test-veranstaltungen.csv"))
                .when().post("/api/veranstaltungen/import")
                .then()
                .statusCode(200)
                .body(containsString("2 Veranstaltung(en) angelegt"));

        Veranstaltung v = Veranstaltung.find("name", "TV_1").firstResult();
        Assertions.assertNotNull(v);
        testVid = v.id;
    }

    @Test
    void testImportReferenten() {
        given()
                .multiPart("file", getCsvFile("referenten.csv"))
                .when().post("/api/veranstaltungen/{vid}/referenten/import", testVid)
                .then()
                .statusCode(200);

        Referent r = (Referent) User.findByEmail("juergenkreyalias-ref@yahoo.com");
        Assertions.assertNotNull(r);
        Assertions.assertEquals("msg systems", r.organisation);
    }

    @Test
    void testImportTeilnehmer() {
        given()
                .multiPart("file", getCsvFile("teilnehmer.csv"))
                .when().post("/api/veranstaltungen/{vid}/teilnehmer/import", testVid)
                .then()
                .statusCode(200);

        Teilnehmer t = (Teilnehmer) User.findByEmail("naja.alfter@rks-linz.de");
        Assertions.assertNotNull(t);
        Assertions.assertEquals("10.3", t.gruppe);
    }

    @Test
    void testImportEventSlots() {
        given()
                .multiPart("file", getCsvFile("slots.csv"))
                .when().post("/api/veranstaltungen/{vid}/slots/import", testVid)
                .then()
                .statusCode(200);

        Assertions.assertEquals(12, EventSlot.count());
    }

    @Test
    void testImportVortraege() {
        testImportReferenten();
        testImportEventSlots();

        given()
                .multiPart("file", getCsvFile("wahl_vortraege.csv"))
                .when().post("/api/veranstaltungen/{vid}/vortraege/import", testVid)
                .then()
                .statusCode(200);

        Assertions.assertEquals(16, Wahlvortrag.count());
        Wahlvortrag wv = Wahlvortrag.find("titel", "Traumberuf Polizei?").firstResult();
        Assertions.assertNotNull(wv, "Wahlvortrag sollte importiert worden sein");
        Assertions.assertTrue(wv.wiederholbar);

        Assertions.assertEquals(0, Pflichtvortrag.count());

        given()
                .multiPart("file", getCsvFile("pflicht_vortraege.csv"))
                .when().post("/api/veranstaltungen/{vid}/vortraege/import", testVid)
                .then()
                .statusCode(200);

        Assertions.assertEquals(18, Pflichtvortrag.count());
        Pflichtvortrag pv = Pflichtvortrag.find("titel", "Vortrag Arbeitsagentur für 10.5").firstResult();
        Assertions.assertNotNull(pv, "Pflichtvortrag sollte importiert worden sein");
        Assertions.assertEquals(pv.pflichtraum.name, "A-2.04");
        Assertions.assertEquals(pv.pflichtslot.description, "9");
    }

    private static File getCsvFile(String fileName) {
        ClassLoader classLoader = ClassLoaderUtils.getClassLoader(CsvFileImportTest.class);
        URL resource = Objects.requireNonNull(classLoader.getResource(CSV_DIR + fileName));

        return new File(resource.getFile());
    }
}
