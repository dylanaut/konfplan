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
import kreyj.konfplan.persistence.AbschlussTyp;
import kreyj.konfplan.persistence.Organisator;
import kreyj.konfplan.persistence.Gebaeude;
import kreyj.konfplan.persistence.Gebaeudetyp;
import kreyj.konfplan.persistence.Nutzer;
import kreyj.konfplan.persistence.Raum;
import kreyj.konfplan.persistence.Referent;
import kreyj.konfplan.persistence.Slot;
import kreyj.konfplan.persistence.Teilnehmer;
import kreyj.konfplan.persistence.Neigung;
import kreyj.konfplan.persistence.Veranstaltung;
import kreyj.konfplan.persistence.Wahlvortrag;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URL;
import java.time.LocalDateTime;
import java.util.List;

import static io.restassured.RestAssured.given;
import static jakarta.ws.rs.core.Response.Status.OK;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.CoreMatchers.containsString;

@QuarkusTest
@TestSecurity(user = "admin@test.de", roles = "ORGANISATOR")
@QuarkusTestResource(H2DatabaseTestResource.class)
class CsvImportTest extends DatabaseCleaner {

    @InjectMock
    KeycloakUserProvisioningService keycloakUserProvisioningService;
    @TestHTTPResource
    @TestHTTPEndpoint(GebaeudeResource.class)
    URL gebaeudeEndpoint;

    @TestHTTPResource
    @TestHTTPEndpoint(OrganisatorResource.class)
    URL adminEndpoint;

    Long testVid;


    @BeforeEach
    @Transactional
    void setup() {
        Organisator admin = new Organisator();
        admin.assignLoginName("admin");
        admin.setEmail("admin@test.de");
        admin.persist();

        Referent r = new Referent();
        r.assignLoginName("vortrag");
        r.setEmail("vortrag@ref.de");
        r.setFirstName("Max");
        r.setLastName("Ref");
        r.persist();

        Gebaeude gebaeude = setupGebaeude("RKS_LINZ");
        gebaeude.addRaum(new Raum("A101", 30));

        testVid = setupVeranstaltung(admin, List.of(gebaeude));

        Veranstaltung veranstaltung = Veranstaltung.findById(testVid);

        r.addVeranstaltung(veranstaltung);
        Slot slot1 = new Slot("Slot 1", veranstaltung.getBeginntAm(),
            veranstaltung.getBeginntAm().plusHours(1), veranstaltung);
        slot1.persist();
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


    private Long setupVeranstaltung(Organisator admin, List<Gebaeude> gebaeudeList) {
        Veranstaltung v = new Veranstaltung();
        v.setName("Basis Event " + System.currentTimeMillis());
        v.setBeginntAm(LocalDateTime.of(2025, 10, 10, 9, 0));
        v.setEndetAm(LocalDateTime.of(2025, 10, 10, 17, 0));
        v.addGruppe("Pflichtgruppe");
        gebaeudeList.forEach(v::addGebaeude);
        v.persist();

        admin.addVeranstaltung(v);
        admin.persist();

        return v.getId();
    }


    @Test
    @TestHTTPEndpoint(VeranstaltungResource.class)
    void testImportVeranstaltungen() {
        String csv = "Name;Beginn;Ende;Organisatoren_LoginNames;Gebaeude_Namen;Logo;Logo_link\n" +
            "CSV Event;2026-10-01 07:00;2026-10-01 17:00;admin;RKS_LINZ;assets/RKS_Logo.png;https://realschuleplus-linz.de/home/home.html";

        given()
            .multiPart("file", "veranstaltungen.csv", csv.getBytes())
            .when().post("/import")
            .then()
            .statusCode(OK.getStatusCode())
            .body(containsString("1 Veranstaltung(en) angelegt"));

        Veranstaltung v = Veranstaltung.find("name", "CSV Event").firstResult();
        assertThat(v).isNotNull();
        assertThat(v.getDeadlineReferenten()).isEqualTo(LocalDateTime.of(2026, 9, 24, 7, 0));
        assertThat(v.getDeadlineTeilnehmer()).isEqualTo(LocalDateTime.of(2026, 9, 28, 7, 0));
    }


    @Test
    @TestHTTPEndpoint(VeranstaltungResource.class)
    void testImportVeranstaltungenMitExplizitenDeadlines() {
        String csv = "Name;Beginn;Ende;Deadline_Referenten;Deadline_Teilnehmer;Organisatoren_LoginNames;Gebaeude_Namen;Logo;Logo_link\n" +
            "CSV Event Deadlines;2026-10-01 07:00;2026-10-01 17:00;2026-09-01 12:00;2026-09-20 12:00;admin;RKS_LINZ;assets/RKS_Logo.png;https://realschuleplus-linz.de/home/home.html";

        given()
            .multiPart("file", "veranstaltungen.csv", csv.getBytes())
            .when().post("/import")
            .then()
            .statusCode(OK.getStatusCode())
            .body(containsString("1 Veranstaltung(en) angelegt"));

        Veranstaltung v = Veranstaltung.find("name", "CSV Event Deadlines").firstResult();
        assertThat(v).isNotNull();
        assertThat(v.getDeadlineReferenten()).isEqualTo(LocalDateTime.of(2026, 9, 1, 12, 0));
        assertThat(v.getDeadlineTeilnehmer()).isEqualTo(LocalDateTime.of(2026, 9, 20, 12, 0));
    }


    @Test
    @TestHTTPEndpoint(VeranstaltungResource.class)
    void testImportVeranstaltungenMitMaxPrioritaeten() {
        String csv = "Name;Beginn;Ende;Max_Prioritaeten;Organisatoren_LoginNames;Gebaeude_Namen;Logo;Logo_link\n" +
            "CSV Event MaxPrio;2026-10-01 07:00;2026-10-01 17:00;3;admin;RKS_LINZ;assets/RKS_Logo.png;https://realschuleplus-linz.de/home/home.html";

        given()
            .multiPart("file", "veranstaltungen.csv", csv.getBytes())
            .when().post("/import")
            .then()
            .statusCode(OK.getStatusCode())
            .body(containsString("1 Veranstaltung(en) angelegt"));

        Veranstaltung v = Veranstaltung.find("name", "CSV Event MaxPrio").firstResult();
        assertThat(v).isNotNull();
        assertThat(v.getMaxPrioritaeten()).isEqualTo(3);
    }


    @Test
    void testImportGebaeudeMitRaeumen() {
        final String gebaeudeName = "Altbau";
        String csv = "Name;Typ;Strasse;Hausnummer;PLZ;Ort;Räume\n" +
            gebaeudeName + ";SCHULE;Alte Str.;10;12345;Stadt;A101:30:1.OG|Lab:20:EG";

        given()
            .baseUri(gebaeudeEndpoint.toString())
            .basePath("/import")
            .multiPart("file", "gebaeude.csv", csv.getBytes())
            .when().post()
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
        String csv = "Email;Nachname;Vorname;LoginName\n" + adminEmail + ";Jessen;Kathrin;kathrin.jessen";

        given()
            .baseUri(adminEndpoint.toString())
            .basePath("/organisatoren/import")
            .multiPart("file", "veranstalter.csv", csv.getBytes())
            .when().post()
            .then()
            .statusCode(OK.getStatusCode());

        Organisator organisator = (Organisator) Nutzer.findByEmail(adminEmail);
        assertThat(organisator).isNotNull();
        assertThat(organisator.getFirstName()).isEqualTo("Kathrin");
    }


    @Test
    @TestHTTPEndpoint(VeranstaltungResource.class)
    void testImportReferenten() {
        String refEmail = "max@ref.de";
        String csv = "Vorname;Nachname;Email;Position;Organisation;LoginName\n" +
            "Max;Referent;" + refEmail + ";Experte;TechCorp;max";

        given()
            .multiPart("file", "referenten.csv", csv.getBytes())
            .when().post("/{vid}/referenten/import", testVid)
            .then()
            .statusCode(OK.getStatusCode());

        Referent r = (Referent) Nutzer.findByEmail(refEmail);
        assertThat(r).isNotNull();
        assertThat(r.getOrganisation()).isEqualTo("TechCorp");
    }


    @Test
    @TestHTTPEndpoint(VeranstaltungResource.class)
    void testImportTeilnehmer() {
        String tnEmail = "tom@stud.de";
        String csv = "Vorname;Nachname;Email;Gruppen;LoginName\n" +
            "Tom;Student;" + tnEmail + ";10b;tom";

        given()
            .multiPart("file", "teilnehmer.csv", csv.getBytes())
            .when().post("/{vid}/teilnehmer/import", testVid)
            .then()
            .statusCode(OK.getStatusCode());

        Teilnehmer t = (Teilnehmer) Nutzer.findByEmail(tnEmail);
        assertThat(t).isNotNull();
        assertThat(t.getGruppen()).contains("10b");
    }


    @Test
    @TestHTTPEndpoint(VeranstaltungResource.class)
    void testImportSlots() {
        String csv = "Bezeichnung;Tag;Beginn;Ende\n" +
            "Slot 2;2025-10-10;11:00;11:45";

        given()
            .multiPart("file", "slots.csv", csv.getBytes())
            .when().post("/{vid}/slots/import", testVid)
            .then()
            .statusCode(OK.getStatusCode());

        assertThat(Slot.count()).isEqualTo(2);
    }


    @Test
    @TestHTTPEndpoint(VeranstaltungResource.class)
    void testImportVortraege() {
        String wvTitel = "Java Kurs";
        String pvTitel = "Berufsorientierung";
        String csv = "istPflicht;Titel;Referent_LoginName;Inhalt;Pflichtgruppe;wiederholbar;maxWiederholungen;Pflichtraum;" +
            "Pflichtslot;Ausstattung\n" +
            "false;" + wvTitel + ";vortrag;Wahlinhalt;;true;2;;;Beamer\n" +
            "true;" + pvTitel + ";vortrag;Pflichtinhalt;Pflichtgruppe;false;1;A101;Slot 1;";

        given()
            .multiPart("file", "vortraege.csv", csv.getBytes())
            .when().post("/{vid}/vortraege/import", testVid)
            .then()
            .statusCode(OK.getStatusCode());

        Wahlvortrag wv = Wahlvortrag.find("titel", wvTitel).firstResult();
        assertThat(wv).describedAs("Wahlvortrag '" +
            wvTitel + "' sollte importiert worden sein").isNotNull();
        assertThat(wv.getAusstattung()).describedAs("Wahlvortrag '" +
            wvTitel + "' sollte Beamer als Ausstattung haben").isEqualTo("Beamer");
        assertThat(wv.isWiederholbar()).isTrue();
        assertThat(wv.getMaxWiederholungen()).isEqualTo(2);
    }


    @Test
    @TestHTTPEndpoint(VeranstaltungResource.class)
    void testImportVortraege_mitAbschluss() {
        String wvTitel = "Vortrag mit Abschluss";
        String csv = "istPflicht;Titel;Referent_LoginName;Inhalt;Pflichtgruppe;wiederholbar;maxWiederholungen;Pflichtraum;" +
            "Pflichtslot;Ausstattung;Abschluss\n" +
            "false;" + wvTitel + ";vortrag;Wahlinhalt;;true;2;;;Beamer;Allgemeine\n";

        given()
            .multiPart("file", "vortraege.csv", csv.getBytes())
            .when().post("/{vid}/vortraege/import", testVid)
            .then()
            .statusCode(OK.getStatusCode());

        Wahlvortrag wv = Wahlvortrag.find("titel", wvTitel).firstResult();
        assertThat(wv).describedAs("Wahlvortrag '" + wvTitel + "' sollte importiert worden sein").isNotNull();
        assertThat(wv.getAbschluss()).describedAs("Wahlvortrag '" +
            wvTitel + "' sollte Allgemeine Hochschulreife als Abschluss haben").isEqualTo(AbschlussTyp.ALLGEMEINE_HOCHSCHULREIFE);
    }


    @Test
    @TestHTTPEndpoint(VeranstaltungResource.class)
    void testImportVortraege_ohneAbschluss_bleibtLeer() {
        String wvTitel = "Vortrag ohne Abschluss";
        String csv = "istPflicht;Titel;Referent_LoginName;Inhalt;Pflichtgruppe;wiederholbar;maxWiederholungen;Pflichtraum;" +
            "Pflichtslot;Ausstattung;Abschluss\n" +
            "false;" + wvTitel + ";vortrag;Wahlinhalt;;true;2;;;Beamer;\n";

        given()
            .multiPart("file", "vortraege.csv", csv.getBytes())
            .when().post("/{vid}/vortraege/import", testVid)
            .then()
            .statusCode(OK.getStatusCode());

        Wahlvortrag wv = Wahlvortrag.find("titel", wvTitel).firstResult();
        assertThat(wv).describedAs("Wahlvortrag '" + wvTitel + "' sollte importiert worden sein").isNotNull();
        assertThat(wv.getAbschluss()).describedAs("Abschluss darf beim CSV-Import leer bleiben").isNull();
    }


    @Test
    @TestHTTPEndpoint(VeranstaltungResource.class)
    void testImportVortraege_mitNeigungen() {
        String wvTitel = "Vortrag mit Neigungen";
        String csv = "istPflicht;Titel;Referent_LoginName;Inhalt;Pflichtgruppe;wiederholbar;maxWiederholungen;Pflichtraum;" +
            "Pflichtslot;Ausstattung;Neigungen\n" +
            "false;" + wvTitel + ";vortrag;Wahlinhalt;;true;2;;;Beamer;sozial|technisch\n";

        given()
            .multiPart("file", "vortraege.csv", csv.getBytes())
            .when().post("/{vid}/vortraege/import", testVid)
            .then()
            .statusCode(OK.getStatusCode());

        Wahlvortrag wv = Wahlvortrag.find("titel", wvTitel).firstResult();
        assertThat(wv).describedAs("Wahlvortrag '" + wvTitel + "' sollte importiert worden sein").isNotNull();
        assertThat(wv.getNeigungen()).describedAs("Wahlvortrag '" +
            wvTitel + "' sollte sozial und technisch als Neigungen haben").containsExactlyInAnyOrder(Neigung.SOZIAL, Neigung.TECHNISCH);
    }


    @Test
    @TestHTTPEndpoint(VeranstaltungResource.class)
    void testImportVortraege_ohneNeigungen_bleibtLeer() {
        String wvTitel = "Vortrag ohne Neigungen";
        String csv = "istPflicht;Titel;Referent_LoginName;Inhalt;Pflichtgruppe;wiederholbar;maxWiederholungen;Pflichtraum;" +
            "Pflichtslot;Ausstattung;Neigungen\n" +
            "false;" + wvTitel + ";vortrag;Wahlinhalt;;true;2;;;Beamer;\n";

        given()
            .multiPart("file", "vortraege.csv", csv.getBytes())
            .when().post("/{vid}/vortraege/import", testVid)
            .then()
            .statusCode(OK.getStatusCode());

        Wahlvortrag wv = Wahlvortrag.find("titel", wvTitel).firstResult();
        assertThat(wv).describedAs("Wahlvortrag '" + wvTitel + "' sollte importiert worden sein").isNotNull();
        assertThat(wv.getNeigungen()).describedAs("Neigungen dürfen beim CSV-Import leer bleiben").isEmpty();
    }


    @Test
    @TestHTTPEndpoint(VeranstaltungResource.class)
    void testImportVortraege_kuerztLangenTitelUndSpeichertVollenTitelAlsInhalt() {
        String wort = "Ausbildungsberuf"; // 16 Zeichen, um eine eindeutige Wortgrenze zu erzeugen
        String langerTitel = (wort + " ").repeat(10).trim(); // 169 Zeichen, > 120
        assertThat(langerTitel.length()).isGreaterThan(120);

        String csv = "istPflicht;Titel;Referent_LoginName;Inhalt;wiederholbar;maxWiederholungen\n" +
            "false;" + langerTitel + ";vortrag;Kurzer CSV-Inhalt;true;2";

        given()
            .multiPart("file", "vortraege.csv", csv.getBytes())
            .when().post("/{vid}/vortraege/import", testVid)
            .then()
            .statusCode(OK.getStatusCode());

        List<Wahlvortrag> alle = Wahlvortrag.listAll();
        assertThat(alle).hasSize(1);
        Wahlvortrag wv = alle.get(0);

        String erwarteterGekuerzterTitel = (wort + " ").repeat(7).trim(); // 118 Zeichen, exakt an Wortgrenze
        assertThat(wv.getTitel()).describedAs("Titel sollte an der Wortgrenze auf 118 Zeichen gekürzt sein").isEqualTo(erwarteterGekuerzterTitel);
        assertThat(wv.getTitel().length()).describedAs("Titel sollte auf unter 120 Zeichen gekürzt sein").isLessThan(120);
        assertThat(wv.getInhalt()).describedAs("Voller Titel sollte als Inhalt gespeichert werden").isEqualTo(langerTitel);
    }
}
