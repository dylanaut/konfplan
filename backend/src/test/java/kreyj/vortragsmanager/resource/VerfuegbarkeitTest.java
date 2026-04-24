package kreyj.vortragsmanager.resource;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.h2.H2DatabaseTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import jakarta.transaction.Transactional;
import kreyj.vortragsmanager.entity.*;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;

@QuarkusTest
@QuarkusTestResource(H2DatabaseTestResource.class)
class VerfuegbarkeitTest {

    Long testVid;
    Long slotId;

    @BeforeEach
    @Transactional
    void setup() {
        Prioritaet.deleteAll();
        Verfuegbarkeit.deleteAll();
        Vortrag.deleteAll();
        Nutzer.deleteAll();
        EventSlot.deleteAll();
        Veranstaltung.deleteAll();

        Veranstaltung v = new Veranstaltung();
        v.name = "Test Event";
        v.beginntAm = LocalDateTime.now();
        v.persist();
        testVid = v.id;

        EventSlot s = new EventSlot();
        s.description = "Slot 1";
        s.startTime = LocalDateTime.now();
        s.endTime = LocalDateTime.now().plusHours(1);
        s.veranstaltung = v;
        s.persist();
        slotId = s.id;

        v.addSlot(s);
    }

    @Test
    @TestSecurity(user = "admin@test.de", roles = "ADMIN")
    void testVerfuegbarkeitCreatedOnAddNutzer() {
        String jsonReferent = """
                {
                    "role": "REFERENT",
                    "email": "referent@verf.de",
                    "firstName": "Ref",
                    "lastName": "Ernt"
                }
                """;

        // Referent hinzufügen
        given()
                .contentType(ContentType.JSON)
                .body(jsonReferent)
                .when().post("/api/veranstaltungen/{vid}/nutzer", testVid)
                .then()
                .statusCode(201);

        Nutzer ref = Nutzer.findByEmail("referent@verf.de");
        long countRef = Verfuegbarkeit.count("nutzer = ?1 and slot.id = ?2", ref, slotId);
        Assertions.assertEquals(1, countRef, "Verfügbarkeit für Referent sollte erstellt worden sein");

        String jsonTeilnehmer = """
                {
                    "role": "TEILNEHMER",
                    "email": "schueler@verf.de",
                    "firstName": "Schü",
                    "lastName": "Ler"
                }
                """;

        // Teilnehmer hinzufügen
        given()
                .contentType(ContentType.JSON)
                .body(jsonTeilnehmer)
                .when().post("/api/veranstaltungen/{vid}/nutzer", testVid)
                .then()
                .statusCode(201);

        Nutzer teil = Nutzer.findByEmail("schueler@verf.de");
        long countTeil = Verfuegbarkeit.count("nutzer = ?1 and slot.id = ?2", teil, slotId);
        Assertions.assertEquals(1, countTeil, "Verfügbarkeit für Teilnehmer sollte erstellt worden sein");
    }

    @Test
    @Transactional
    void testVerfuegbarkeitRemovedOnRemoveNutzer() {
        Veranstaltung v = Veranstaltung.findById(testVid);
        
        Referent r = new Referent();
        r.email = "del@verf.de";
        r.persist();
        
        r.addVeranstaltung(v);
        
        long countBefore = Verfuegbarkeit.count("nutzer = ?1", r);
        Assertions.assertEquals(1, countBefore);
        
        r.removeVeranstaltung(v);
        
        long countAfter = Verfuegbarkeit.count("nutzer = ?1", r);
        Assertions.assertEquals(0, countAfter, "Verfügbarkeit sollte nach Entfernen des Nutzers gelöscht worden sein");
    }
}
