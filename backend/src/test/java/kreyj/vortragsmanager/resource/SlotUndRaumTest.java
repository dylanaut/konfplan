package kreyj.vortragsmanager.resource;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.h2.H2DatabaseTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.transaction.Transactional;
import kreyj.vortragsmanager.dto.RaumBelegbarkeitDto;
import kreyj.vortragsmanager.entity.*;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static io.restassured.RestAssured.given;

@QuarkusTest
@QuarkusTestResource(H2DatabaseTestResource.class)
class SlotUndRaumTest {

    Long vid;
    Long otherVid;
    Long raumId;

    @BeforeEach
    @Transactional
    void setup() {
        Zuweisung.deleteAll();
        RaumBelegbarkeit.deleteAll();
        Verfuegbarkeit.deleteAll();
        Vortrag.deleteAll();
        EventSlot.deleteAll();
        Nutzer.deleteAll();
        Raum.deleteAll();
        Gebaeude.deleteAll();
        Veranstaltung.deleteAll();

        // Hauptveranstaltung
        Veranstaltung v = new Veranstaltung();
        v.name = "Haupt Event";
        v.beginntAm = LocalDateTime.of(2025, 10, 1, 8, 0);
        v.persist();
        vid = v.id;

        // Andere Veranstaltung (zeitgleich)
        Veranstaltung v2 = new Veranstaltung();
        v2.name = "Anderes Event";
        v2.beginntAm = LocalDateTime.of(2025, 10, 1, 8, 0);
        v2.persist();
        otherVid = v2.id;

        Gebaeude g = new Gebaeude();
        g.name = "G1";
        g.typ = Gebaeude.Gebaeudetyp.EXTERN;
        g.postleitzahl = "53567";
        g.strasse = "Wallroth";
        g.ort = "Buchholz";
        g.persist();
        v.gebaeude = List.of(g);
        v.persist();

        Raum r = new Raum();
        r.name = "R1";
        r.kapazitaet = 20;
        r.gebaeude = g;
        r.persist();
        raumId = r.id;
    }

    @Test
    @TestSecurity(user = "admin@test.de", roles = "ADMIN")
    void testSlotValidation() {
        // 1. Ende vor Beginn
        String jsonInvalid = """
                {
                    "description": "Ungültig",
                    "startTime": "2025-10-01T10:00:00",
                    "endTime": "2025-10-01T09:00:00"
                }
                """;
        given()
                .contentType(ContentType.JSON)
                .body(jsonInvalid)
                .when().post("/api/veranstaltungen/{vid}/slots", vid)
                .then()
                .statusCode(400);

        // 2. Vor Veranstaltungsbeginn
        String jsonEarly = """
                {
                    "description": "Zu früh",
                    "startTime": "2025-09-30T10:00:00",
                    "endTime": "2025-09-30T11:00:00"
                }
                """;
        given()
                .contentType(ContentType.JSON)
                .body(jsonEarly)
                .when().post("/api/veranstaltungen/{vid}/slots", vid)
                .then()
                .statusCode(400);

        // 3. Korrekter Slot
        String jsonOk = """
                {
                    "description": "Slot 1",
                    "startTime": "2025-10-01T09:00:00",
                    "endTime": "2025-10-01T10:00:00"
                }
                """;
        Integer slotId = given()
                .contentType(ContentType.JSON)
                .body(jsonOk)
                .when().post("/api/veranstaltungen/{vid}/slots", vid)
                .then()
                .statusCode(201)
                .extract().path("id");

        // 4. Überschneidung
        String jsonOverlap = """
                {
                    "description": "Überlappend",
                    "startTime": "2025-10-01T09:30:00",
                    "endTime": "2025-10-01T10:30:00"
                }
                """;
        given()
                .contentType(ContentType.JSON)
                .body(jsonOverlap)
                .when().post("/api/veranstaltungen/{vid}/slots", vid)
                .then()
                .statusCode(400);
    }

    @Test
    @TestSecurity(user = "admin@test.de", roles = "ADMIN")
    void testRaumVerfuegbarkeitCrossEvent() {
        final Long[] s1Id = new Long[1];

        // Daten in einer eigenen Transaktion vorbereiten und committen
        QuarkusTransaction.requiringNew().run(() -> {
            // Slot in Event 1
            EventSlot s1 = new EventSlot();
            s1.description = "Slot E1";
            s1.startTime = LocalDateTime.of(2025, 10, 1, 9, 0);
            s1.endTime = LocalDateTime.of(2025, 10, 1, 10, 0);
            s1.veranstaltung = Veranstaltung.findById(vid);
            s1.persist();
            s1Id[0] = s1.id;

            // Slot in Event 2 (zeitlich überschneidend)
            EventSlot s2 = new EventSlot();
            s2.description = "Slot E2";
            s2.startTime = LocalDateTime.of(2025, 10, 1, 9, 30);
            s2.endTime = LocalDateTime.of(2025, 10, 1, 10, 30);
            s2.veranstaltung = Veranstaltung.findById(otherVid);
            s2.persist();

            Raum r = Raum.findById(raumId);

            // Raum in Event 2 als belegt markieren
            RaumBelegbarkeit rv2 = new RaumBelegbarkeit();
            rv2.raum = r;
            rv2.slot = s2;
            rv2.isBelegt = true;
            rv2.persist();
        });

        // Abfrage für Event 1: Raum sollte für s1 als "blocked" markiert sein
        List<RaumBelegbarkeitDto> dtos = given()
                .when().get("/api/admin/veranstaltung/{vid}/raeume/verfuegbarkeiten", vid)
                .then()
                .statusCode(200)
                .extract().body().jsonPath().getList(".", RaumBelegbarkeitDto.class);

        RaumBelegbarkeitDto target = dtos.stream()
                .filter(d -> d.slotId.equals(s1Id[0]))
                .findFirst().orElseThrow();

        Assertions.assertTrue(target.isBlockedByOtherEvent, "Raum sollte durch anderes Event blockiert sein");
        Assertions.assertEquals("Anderes Event", target.blockingEventName);
    }
}
