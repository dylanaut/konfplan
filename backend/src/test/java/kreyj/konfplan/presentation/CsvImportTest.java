package kreyj.konfplan.presentation;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.h2.H2DatabaseTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.transaction.Transactional;
import kreyj.konfplan.persistence.Admin;
import kreyj.konfplan.persistence.Gebaeude;
import kreyj.konfplan.persistence.Gebaeudetyp;
import kreyj.konfplan.persistence.Nutzer;
import kreyj.konfplan.persistence.Raum;
import kreyj.konfplan.persistence.Referent;
import kreyj.konfplan.persistence.Slot;
import kreyj.konfplan.persistence.Teilnehmer;
import kreyj.konfplan.persistence.Veranstaltung;
import kreyj.konfplan.persistence.Wahlvortrag;
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
class CsvImportTest extends DatabaseCleaner {

    Long testVid;

    @BeforeEach
    @Transactional
    void setup() {
        Admin admin = new Admin();
        admin.setEmail("admin@test.de");
        admin.setPasswordHash("hash");
        admin.persistAndFlush();

        Referent r = new Referent();
        r.setEmail("vortrag@ref.de");
        r.setFirstName("Max");
        r.setLastName("Ref");
        r.setPasswordHash("hash");
        r.persistAndFlush();

        Gebaeude gebaeude = setupGebaeude("RKS_LINZ");
        gebaeude.addRaum(new Raum("A101", 30));

        testVid = setupVeranstaltung(admin, List.of(gebaeude));

        Veranstaltung veranstaltung = Veranstaltung.findById(testVid);

        r.addVeranstaltung(veranstaltung);
        Slot slot1 = new Slot("Slot 1", veranstaltung.getBeginntAm(),
                veranstaltung.getBeginntAm().plusHours(1), veranstaltung);
        slot1.persistAndFlush();
        veranstaltung.addSlot(slot1);
    }

    private Gebaeude setupGebaeude(String gebaeudeName) {
        Gebaeude gebaeude = new Gebaeude();

        gebaeude.setName(gebaeudeName);
        gebaeude.setTyp(Gebaeudetyp.SCHULE);
        gebaeude.setStrasse("Alte Str.");
        gebaeude.setHausnummer("10");
        gebaeude.setPostleitzahl("12345");
        gebaeude.setOrt("Stadt");

        return gebaeude;
    }

    private Long setupVeranstaltung(Admin admin, List<Gebaeude> gebaeudeList) {
        Veranstaltung v = new Veranstaltung();
        v.setName("Basis Event " + System.currentTimeMillis());
        v.setBeginntAm(LocalDateTime.of(2025, 10, 10, 9, 0));
        v.setEndetAm(LocalDateTime.of(2025, 10, 10, 17, 0));
        gebaeudeList.forEach(v::addGebaeude);
        v.persistAndFlush();

        admin.addVeranstaltung(v);
        admin.persistAndFlush();

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
        final String gebaeudeName = "Altbau";
        String csv = "Name;Typ;Strasse;Hausnummer;PLZ;Ort;Räume\n" +
                gebaeudeName + ";SCHULE;Alte Str.;10;12345;Stadt;A101:30:1.OG|Lab:20:EG";

        given()
                .multiPart("file", "gebaeude.csv", csv.getBytes())
                .when().post("/api/gebaeude/import")
                .then()
                .statusCode(OK.getStatusCode())
                .body(containsString("Import erfolgreich"));

        Gebaeude g = Gebaeude.find("name", gebaeudeName).firstResult();
        assertThat(g).isNotNull();
        assertThat(g.getRaeume().size()).describedAs("Anzahl Räume sollte 2 sein").isEqualTo(2);
    }

    @Test
    void testImportVeranstalter() {
        String adminEmail = "kathrin.jessen@rks-linz.de";
        String csv = "Email;Nachname;Vorname\n" + adminEmail + ";Jessen;Kathrin";

        given()
                .multiPart("file", "veranstalter.csv", csv.getBytes())
                .when().post("/api/admin/admins/import")
                .then()
                .statusCode(OK.getStatusCode());

        Admin organisator = (Admin) Nutzer.findByEmail(adminEmail);
        assertThat(organisator).isNotNull();
        assertThat(organisator.getFirstName()).isEqualTo("Kathrin");
    }

    @Test
    void testImportReferenten() {
        String refEmail = "max@ref.de";
        String csv = "Vorname;Nachname;Email;Position;Organisation;Slogan;Biografie\n" +
                "Max;Referent;" + refEmail + ";Experte;TechCorp;Think Big;Bio Text";

        given()
                .multiPart("file", "referenten.csv", csv.getBytes())
                .when().post("/api/veranstaltungen/{vid}/referenten/import", testVid)
                .then()
                .statusCode(OK.getStatusCode());

        Referent r = (Referent) Nutzer.findByEmail(refEmail);
        assertThat(r).isNotNull();
        assertThat(r.getOrganisation()).isEqualTo("TechCorp");
    }

    @Test
    void testImportTeilnehmer() {
        String tnEmail = "tom@stud.de";
        String csv = "Vorname;Nachname;Email;Gruppe\n" +
                "Tom;Student;" + tnEmail + ";10b";

        given()
                .multiPart("file", "teilnehmer.csv", csv.getBytes())
                .when().post("/api/veranstaltungen/{vid}/teilnehmer/import", testVid)
                .then()
                .statusCode(OK.getStatusCode());

        Teilnehmer t = (Teilnehmer) Nutzer.findByEmail(tnEmail);
        assertThat(t).isNotNull();
        assertThat(t.getGruppen()).contains("10b");
    }

    @Test
    void testImportSlots() {
        String csv = "Bezeichnung;Tag;Beginn;Ende\n" +
                "Slot 2;2025-10-10;11:00;11:45";

        given()
                .multiPart("file", "slots_2026.csv", csv.getBytes())
                .when().post("/api/veranstaltungen/{vid}/slots/import", testVid)
                .then()
                .statusCode(OK.getStatusCode());

        assertThat(Slot.count()).isEqualTo(2);
    }

    @Test
    void testImportVortraege() {
        String wvTitel = "Java Kurs";
        String pvTitel = "Berufsorientierung";
        String csv = "istPflicht;Titel;Referent_Email;Inhalt;Pflichtgruppe;wiederholbar;maxWiederholungen;Pflichtraum;Pflichtslot\n" +
                "false;" + wvTitel + ";vortrag@ref.de;Inhalt Text;;true;2;;\n" +
                "true;" + pvTitel + ";vortrag@ref.de;Inhalt Text;LeereGruppe;false;1;A101;Slot 1";

        given()
                .multiPart("file", "vortraege.csv", csv.getBytes())
                .when().post("/api/veranstaltungen/{vid}/vortraege/import", testVid)
                .then()
                .statusCode(OK.getStatusCode());

        Wahlvortrag wv = Wahlvortrag.find("titel", wvTitel).firstResult();
        assertThat(wv).describedAs("Wahlvortrag '" +
                wvTitel + "' sollte importiert worden sein").isNotNull();
        assertThat(wv.isWiederholbar()).isTrue();
        assertThat(wv.getMaxWiederholungen()).isEqualTo(2);
    }
}
