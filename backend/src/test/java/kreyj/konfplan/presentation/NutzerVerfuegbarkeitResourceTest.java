package kreyj.konfplan.presentation;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.h2.H2DatabaseTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
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
import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
@QuarkusTestResource(H2DatabaseTestResource.class)
class NutzerVerfuegbarkeitResourceTest extends DatabaseCleaner {

    Long testVid;
    Long slotId;

    @BeforeEach
    @Transactional
    void setup() {
        Veranstaltung v = new Veranstaltung();
        v.setName("Test Event");
        v.setBeginntAm(LocalDateTime.now());
        v.persistAndFlush();
        testVid = v.getId();

        Slot s = new Slot();
        s.setDescription("Slot 1");
        s.setStartTime(LocalDateTime.now());
        s.setEndTime(LocalDateTime.now().plusHours(1));
        s.persistAndFlush();
        v.addSlot(s);
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
        long countRef = NutzerVerfuegbarkeit.count("nutzer = ?1 and slot.id = ?2", ref, slotId);
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
        long countTeil = NutzerVerfuegbarkeit.count("nutzer = ?1 and slot.id = ?2", teil, slotId);
        assertThat(1).isEqualTo(countTeil).describedAs("Verfügbarkeit für Teilnehmer sollte erstellt worden sein");
    }

    @Test
    @Transactional
    void testVerfuegbarkeitRemovedOnRemoveNutzer() {
        Veranstaltung v = Veranstaltung.findById(testVid);

        Referent r = new Referent();
        r.setEmail("del@verf.de");
        r.persistAndFlush();

        r.addVeranstaltung(v);

        long countBefore = NutzerVerfuegbarkeit.count("nutzer = ?1", r);
        assertThat(1).isEqualTo(countBefore);

        r.removeVeranstaltung(v);

        long countAfter = NutzerVerfuegbarkeit.count("nutzer = ?1", r);
        assertThat(0).isEqualTo(countAfter).describedAs("Verfügbarkeit sollte nach Entfernen des Nutzers gelöscht worden sein");
    }
}
