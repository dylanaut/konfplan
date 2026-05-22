package kreyj.konfplan.resource;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.h2.H2DatabaseTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.transaction.Transactional;
import kreyj.konfplan.persistence.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static io.restassured.RestAssured.given;
import static jakarta.ws.rs.core.Response.Status.OK;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.CoreMatchers.containsString;

@QuarkusTest
@TestSecurity(user = "admin@test.de", roles = "ADMIN")
@QuarkusTestResource(H2DatabaseTestResource.class)
class CsvImportTest {

    Long testVid;

    @BeforeEach
    @Transactional
    void setup() {
        Zuweisung.deleteAll();
        RaumBelegbarkeit.deleteAll();
        Verfuegbarkeit.deleteAll();
        Vortrag.deleteAll();
        Nutzer.deleteAll();
        Raum.deleteAll();
        Gebaeude.deleteAll();
        EventSlot.deleteAll();
        Veranstaltung.deleteAll();

        Admin admin = new Admin();
        admin.setEmail("admin@test.de");
        admin.setPasswordHash("hash");
        admin.persist();

        Gebaeude gebaeude = setupGebaeude("RKS_LINZ");
        testVid = setupVeranstaltung(admin, List.of(gebaeude));
    }

    private Gebaeude setupGebaeude(String gebaeudeName) {
        Gebaeude gebaeude = new Gebaeude();

        gebaeude.setName(gebaeudeName);
        gebaeude.setTyp(Gebaeudetyp.SCHULE);
        gebaeude.setStrasse("Alte Str.");
        gebaeude.setHausnummer("10");
        gebaeude.setPostleitzahl("12345");
        gebaeude.setOrt("Stadt");
        gebaeude.persist();

        return gebaeude;
    }

    private Long setupVeranstaltung(Admin admin, List<Gebaeude> gebaeudeList) {
        Veranstaltung v = new Veranstaltung();
        v.setName("Basis Event " + System.currentTimeMillis());
        v.setBeginntAm(LocalDateTime.of(2025, 10, 10, 9, 0));
        v.setEndetAm(LocalDateTime.of(2025, 10, 10, 17, 0));
        gebaeudeList.forEach(v::addGebaeude);
        v.persist();

        admin.addVeranstaltung(v);
        admin.persist();

        return v.getId();
    }

    @Test
    void testImportVeranstaltungen() {
        String csv = "Name;Beginn;Ende;Organisatoren_Emails;Gebaeude_Namen;Logo;Logo_link\n" +
                "CSV Event;2026-10-01 07:00;2026-10-01 17:00;admin@test.de;RKS_LINZ;assets/RKS_Logo.png;https://realschuleplus-linz.de/home/home.html";

        given()
                .multiPart("file", "veranstaltungen.csv", csv.getBytes())
                .when().post("/api/veranstaltungen/import")
                .then()
                .statusCode(OK.getStatusCode())
                .body(containsString("1 Veranstaltung(en) angelegt"));
    }

    @Test
    void testImportGebaeudeMitRaeumen() {
        String csv = "Name;Typ;Strasse;Hausnummer;PLZ;Ort;Räume\n" +
                "Altbau;SCHULE;Alte Str.;10;12345;Stadt;A101:30:1.OG|Lab:20:EG";

        given()
                .multiPart("file", "gebaeude.csv", csv.getBytes())
                .when().post("/api/gebaeude/import")
                .then()
                .statusCode(OK.getStatusCode())
                .body(containsString("Import erfolgreich"));

        Gebaeude g = Gebaeude.find("name", "Altbau").firstResult();
        assertThat(g).isNotNull();
        assertThat(g.getRaeume().size()).describedAs("Anzahl Räume sollte 2 sein").isEqualTo(2);
    }

    @Test
    void testImportVeranstalter() {
        String csv = "Email;Nachname;Vorname\n" +
                "kathrin.jessen@rks-linz.de;Jessen;Kathrin";

        given()
                .multiPart("file", "veranstalter.csv", csv.getBytes())
                .when().post("/api/admin/admins/import")
                .then()
                .statusCode(OK.getStatusCode());

        Admin organisator = (Admin) Nutzer.findByEmail("kathrin.jessen@rks-linz.de");
        assertThat(organisator).isNotNull();
        assertThat(organisator.getFirstName()).isEqualTo("Kathrin");
    }

    @Test
    void testImportReferenten() {
        String csv = "Vorname;Nachname;Email;Position;Organisation;Slogan;Biografie\n" +
                "Max;Referent;max@ref.de;Experte;TechCorp;Think Big;Bio Text";

        given()
                .multiPart("file", "referenten.csv", csv.getBytes())
                .when().post("/api/veranstaltungen/{vid}/referenten/import", testVid)
                .then()
                .statusCode(OK.getStatusCode());

        Referent r = (Referent) Nutzer.findByEmail("max@ref.de");
        assertThat(r).isNotNull();
        
        assertThat(r.getOrganisation()).isEqualTo("TechCorp");
    }

    @Test
    void testImportTeilnehmer() {
        String csv = "Vorname;Nachname;Email;Gruppe\n" +
                "Tom;Student;tom@stud.de;10b";

        given()
                .multiPart("file", "teilnehmer.csv", csv.getBytes())
                .when().post("/api/veranstaltungen/{vid}/teilnehmer/import", testVid)
                .then()
                .statusCode(OK.getStatusCode());

        Teilnehmer t = (Teilnehmer) Nutzer.findByEmail("tom@stud.de");
        assertThat(t).isNotNull();
        assertThat(t.getGruppe()).isEqualTo("10b");
    }

    @Test
    void testImportEventSlots() {
        String csv = "Bezeichnung;Tag;Beginn;Ende\n" +
                "Slot 1;2025-10-10;09:00;09:45";

        given()
                .multiPart("file", "slots.csv", csv.getBytes())
                .when().post("/api/veranstaltungen/{vid}/slots/import", testVid)
                .then()
                .statusCode(OK.getStatusCode());

        assertThat(EventSlot.count()).isEqualTo(1);
    }

    @Test
    void testImportVortraege() {
        QuarkusTransaction.requiringNew().run(() -> {
            Referent r = new Referent();
            r.setEmail("vortrag@ref.de");
            r.setFirstName("Max");
            r.setLastName("Ref");
            r.setPasswordHash("hash");
            r.addVeranstaltung(Veranstaltung.findById(testVid));
            r.persist();
        });

        String csv = "istPflicht;Titel;Referent_Email;Inhalt;Pflichtgruppe;wiederholbar;maxWiederholungen;Pflichtraum;Pflichtslot\n" +
                "false;Java Kurs;vortrag@ref.de;Inhalt Text;Alle;true;2;;";

        given()
                .multiPart("file", "vortraege.csv", csv.getBytes())
                .when().post("/api/veranstaltungen/{vid}/vortraege/import", testVid)
                .then()
                .statusCode(OK.getStatusCode());

        Wahlvortrag wv = Wahlvortrag.find("titel", "Java Kurs").firstResult();
        assertThat(wv).describedAs("Wahlvortrag sollte importiert worden sein").isNotNull();
        assertThat(wv.isWiederholbar()).isTrue();
    }
}
