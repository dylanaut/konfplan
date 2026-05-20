package kreyj.konfplan.resource;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.h2.H2DatabaseTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import jakarta.transaction.Transactional;
import kreyj.konfplan.persistence.*;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static io.restassured.RestAssured.given;
import static jakarta.ws.rs.core.Response.Status.CREATED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.CoreMatchers.is;

@QuarkusTest
@QuarkusTestResource(H2DatabaseTestResource.class)
class VerfuegbarkeitResourceTest {

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
        v.setName("Test Event");
        v.setBeginntAm(LocalDateTime.now());
        v.persist();
        testVid = v.getId();

        EventSlot s = new EventSlot();
        s.setDescription("Slot 1");
        s.setStartTime(LocalDateTime.now());
        s.setEndTime(LocalDateTime.now().plusHours(1));
        s.setVeranstaltung(v);
        s.persist();
        slotId = s.getId();

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
                .statusCode(CREATED.getStatusCode());

        Nutzer ref = Nutzer.findByEmail("referent@verf.de");
        long countRef = Verfuegbarkeit.count("nutzer = ?1 and slot.getId() = ?2", ref, slotId);
        assertThat(1).isEqualTo(countRef).describedAs("Verfügbarkeit für Referent sollte erstellt worden sein");

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
                .statusCode(CREATED.getStatusCode());

        Nutzer teil = Nutzer.findByEmail("schueler@verf.de");
        long countTeil = Verfuegbarkeit.count("nutzer = ?1 and slot.getId() = ?2", teil, slotId);
        assertThat(1).isEqualTo(countTeil).describedAs("Verfügbarkeit für Teilnehmer sollte erstellt worden sein");
    }

    @Test
    @Transactional
    void testVerfuegbarkeitRemovedOnRemoveNutzer() {
        Veranstaltung v = Veranstaltung.findById(testVid);
        
        Referent r = new Referent();
        r.setEmail("del@verf.de");
        r.persist();
        
        r.addVeranstaltung(v);
        
        long countBefore = Verfuegbarkeit.count("nutzer = ?1", r);
        assertThat(1).isEqualTo(countBefore);
        
        r.removeVeranstaltung(v);
        
        long countAfter = Verfuegbarkeit.count("nutzer = ?1", r);
        assertThat(0).isEqualTo(countAfter).describedAs("Verfügbarkeit sollte nach Entfernen des Nutzers gelöscht worden sein");
    }
}
