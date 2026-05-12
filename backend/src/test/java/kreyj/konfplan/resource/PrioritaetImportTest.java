package kreyj.konfplan.resource;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.transaction.Transactional;
import kreyj.konfplan.persistence.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@QuarkusTest
@TestSecurity(user = "admin@test.de", roles = "ADMIN")
public class PrioritaetImportTest {

    Long testVid;
    Long wv1Id;
    Long wv2Id;
    Long teilnehmer1Id;

    @BeforeEach
    @Transactional
    void setup() {
        Zuweisung.deleteAll();
        Prioritaet.deleteAll();
        Verfuegbarkeit.deleteAll();
        Vortrag.deleteAll();
        RaumBelegbarkeit.deleteAll();
        EventSlot.deleteAll();
        Veranstaltung.deleteAll();
        Nutzer.deleteAll();
        Raum.deleteAll();
        Gebaeude.deleteAll();

        Admin admin = new Admin();
        admin.email = "admin@test.de";
        admin.passwordHash = "hash";
        admin.persist();

        Veranstaltung v = new Veranstaltung();
        v.name = "Test Event " + System.currentTimeMillis();
        v.beginntAm = LocalDateTime.of(2025, 10, 10, 9, 0);
        v.endetAm = LocalDateTime.of(2025, 10, 10, 17, 0);
        v.persist();
        testVid = v.id;

        admin.addVeranstaltung(v);
        admin.persist();

        Teilnehmer t1 = new Teilnehmer();
        t1.email = "teilnehmer1@test.de";
        t1.passwordHash = "hash";
        t1.addVeranstaltung(v);
        t1.persist();
        teilnehmer1Id = t1.id;

        Referent r1 = new Referent();
        r1.email = "referent1@test.de";
        r1.passwordHash = "hash";
        r1.addVeranstaltung(v);
        r1.persist();

        Wahlvortrag wv1 = new Wahlvortrag();
        wv1.titel = "Wahlvortrag 1";
        wv1.veranstaltung = v;
        wv1.referent = r1;
        wv1.persist();
        wv1Id = wv1.id;

        Wahlvortrag wv2 = new Wahlvortrag();
        wv2.titel = "Wahlvortrag 2";
        wv2.veranstaltung = v;
        wv2.referent = r1; // same referent for simplicity
        wv2.persist();
        wv2Id = wv2.id;
    }

    @Test
    void testImportPrioritaeten() {
        // New CSV format
        String csv = String.format("# Legende: %d=%s, %d=%s\n", wv1Id, "Wahlvortrag 1", wv2Id, "Wahlvortrag 2") +
                "Teilnehmer E-Mail;Prioritäten\n" +
                String.format("teilnehmer1@test.de;%d :5,%d: 3 \n", wv1Id, wv2Id);

        given()
                .multiPart("file", "prioritaeten.csv", csv.getBytes())
                .when()
                .post("/api/admin/veranstaltungen/{vid}/prioritaeten/import", testVid)
                .then()
                .statusCode(200)
                .body(is("Import erfolgreich: 2 Prioritäten importiert/aktualisiert."));

        assertEquals(2, Prioritaet.count());

        Prioritaet p1 = Prioritaet.find("teilnehmer.id = ?1 and vortrag.id = ?2", teilnehmer1Id, wv1Id).firstResult();
        assertNotNull(p1);
        assertEquals(5, p1.prioWert);

        Prioritaet p2 = Prioritaet.find("teilnehmer.id = ?1 and vortrag.id = ?2", teilnehmer1Id, wv2Id).firstResult();
        assertNotNull(p2);
        assertEquals(3, p2.prioWert);
    }
}
