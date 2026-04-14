package kreyj.vortragsmanager.resource;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import jakarta.transaction.Transactional;
import kreyj.vortragsmanager.entity.*;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;

@QuarkusTest
@TestSecurity(user = "admin@test.de", roles = "ADMIN")
class CsvImportTest {

    Long testVid;

    @BeforeEach
    @Transactional
    void setup() {
        Zuweisung.deleteAll();
        Prioritaet.deleteAll();
        Verfuegbarkeit.deleteAll();
        Vortrag.deleteAll();
        EventSlot.deleteAll();
        
        User.update("veranstaltung = null");
        Veranstaltung.deleteAll();
        User.deleteAll();
        
        Raum.deleteAll();
        Gebaeude.deleteAll();

        Admin admin = new Admin();
        admin.email = "admin@test.de";
        admin.passwordHash = "hash";
        admin.persist();

        Veranstaltung v = new Veranstaltung();
        v.name = "Basis Event " + System.currentTimeMillis();
        v.beginntAm = LocalDateTime.of(2025, 10, 10, 9, 0);
        v.organisator = admin;
        v.persist();
        testVid = v.id;
    }

    @Test
    void testImportVeranstaltungen() {
        String csv = "Name;Beginn;Ende;Organisator_Email;Gebaeude_Namen;Logo;Logo_link\n" +
                     "CSV Event;2026-10-01 10:00;2026-10-01 17:00;admin@test.de;assets/RKS_Logo.png;https://realschuleplus-linz.de/home/home.html";

        given()
                .multiPart("file", "veranstaltungen.csv", csv.getBytes())
                .when().post("/api/veranstaltungen/import")
                .then()
                .statusCode(200)
                .body(containsString("1 Veranstaltungen angelegt"));
    }

    @Test
    void testImportGebaeudeMitRaeumen() {
        String csv = "Name;Typ;Strasse;Hausnummer;PLZ;Ort;Räume\n" +
                     "Altbau;SCHULE;Alte Str.;10;12345;Stadt;A101:30:1.OG|Lab:20:EG";

        given()
                .multiPart("file", "gebaeude.csv", csv.getBytes())
                .when().post("/api/gebaeude/import")
                .then()
                .statusCode(200)
                .body(containsString("Import erfolgreich"));

        Gebaeude g = Gebaeude.find("name", "Altbau").firstResult();
        Assertions.assertNotNull(g);
        Assertions.assertEquals(2, g.raeume.size(), "Anzahl Räume sollte 2 sein");
    }

    @Test
    void testImportVeranstalter() {
        String csv = "Email;Nachname;Vorname\n" +
                "kathrin.jessen@rks-linz.de;Jessen;Kathrin";

        given()
                .multiPart("file", "veranstalter.csv", csv.getBytes())
                .when().post("/api/admin/admins/import")
                .then()
                .statusCode(200);

        Admin organisator = (Admin) User.findByEmail("kathrin.jessen@rks-linz.de");
        Assertions.assertNotNull(organisator);
        Assertions.assertEquals("Kathrin", organisator.firstName);
    }

    @Test
    void testImportReferenten() {
        String csv = "Vorname;Nachname;Email;Position;Organisation;Slogan;Biografie\n" +
                     "Max;Referent;max@ref.de;Experte;TechCorp;Think Big;Bio Text";

        given()
                .multiPart("file", "referenten.csv", csv.getBytes())
                .when().post("/api/veranstaltungen/{vid}/referenten/import", testVid)
                .then()
                .statusCode(200);

        Referent r = (Referent) User.findByEmail("max@ref.de");
        Assertions.assertNotNull(r);
        Assertions.assertEquals("TechCorp", r.organisation);
    }

    @Test
    void testImportTeilnehmer() {
        String csv = "Vorname;Nachname;Email;Gruppe\n" +
                     "Tom;Student;tom@stud.de;10b";

        given()
                .multiPart("file", "teilnehmer.csv", csv.getBytes())
                .when().post("/api/veranstaltungen/{vid}/teilnehmer/import", testVid)
                .then()
                .statusCode(200);

        Teilnehmer t = (Teilnehmer) User.findByEmail("tom@stud.de");
        Assertions.assertNotNull(t);
        Assertions.assertEquals("10b", t.gruppe);
    }

    @Test
    void testImportEventSlots() {
        String csv = "Bezeichnung;Beginn;Ende\n" +
                     "Slot 1;2025-10-10 09:00;2025-10-10 09:45";

        given()
                .multiPart("file", "slots.csv", csv.getBytes())
                .when().post("/api/veranstaltungen/{vid}/slots/import", testVid)
                .then()
                .statusCode(200);

        Assertions.assertEquals(1, EventSlot.count());
    }

    @Test
    void testImportVortraege() {
        QuarkusTransaction.begin();       Referent r = new Referent();
        r.email = "vortrag@ref.de";
        r.firstName = "Max";
        r.lastName = "Ref";
        r.passwordHash = "hash";
        r.setVeranstaltung(Veranstaltung.findById(testVid));
        r.persist();
        QuarkusTransaction.commit();

        String csv = "istPflicht;Titel;Referent_Email;Inhalt;Zielgruppe;wiederholbar;maxWiederholungen;pflichtraumName;pflichtslotBeschreibung\n" +
                     "false;Java Kurs;vortrag@ref.de;Inhalt Text;Alle;true;2;;";

        given()
                .multiPart("file", "vortraege.csv", csv.getBytes())
                .when().post("/api/veranstaltungen/{vid}/vortraege/import", testVid)
                .then()
                .statusCode(200);

        Wahlvortrag wv = Wahlvortrag.find("titel", "Java Kurs").firstResult();
        Assertions.assertNotNull(wv, "Wahlvortrag sollte importiert worden sein");
        Assertions.assertTrue(wv.wiederholbar);
    }
}
