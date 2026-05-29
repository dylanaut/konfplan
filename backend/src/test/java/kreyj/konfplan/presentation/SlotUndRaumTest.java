package kreyj.konfplan.presentation;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.h2.H2DatabaseTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import jakarta.transaction.Transactional;
import kreyj.konfplan.persistence.Gebaeude;
import kreyj.konfplan.persistence.Gebaeudetyp;
import kreyj.konfplan.persistence.Raum;
import kreyj.konfplan.persistence.RaumVerfuegbarkeit;
import kreyj.konfplan.persistence.Slot;
import kreyj.konfplan.persistence.Veranstaltung;
import kreyj.konfplan.presentation.dto.RaumVerfuegbarkeitDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static io.restassured.RestAssured.given;
import static jakarta.ws.rs.core.Response.Status.BAD_REQUEST;
import static jakarta.ws.rs.core.Response.Status.CREATED;
import static jakarta.ws.rs.core.Response.Status.OK;
import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
@QuarkusTestResource(H2DatabaseTestResource.class)
class SlotUndRaumTest extends DatabaseCleaner {

    Long vid;
    Long otherVid;
    Long raumId;

    @BeforeEach
    @Transactional
    void setup() {
        // Hauptveranstaltung
        Veranstaltung v = new Veranstaltung();
        v.setName("Haupt Event");
        v.setBeginntAm(LocalDateTime.of(2025, 10, 1, 8, 0));
        v.setEndetAm(LocalDateTime.of(2025, 10, 1, 18, 0));
        v.persistAndFlush();
        vid = v.getId();

        // Andere Veranstaltung (zeitgleich)
        Veranstaltung v2 = new Veranstaltung();
        v2.setName("Anderes Event");
        v2.setBeginntAm(LocalDateTime.of(2025, 10, 1, 8, 0));
        v2.setEndetAm(LocalDateTime.of(2025, 10, 1, 18, 0));
        v2.persistAndFlush();
        otherVid = v2.getId();

        Gebaeude g = new Gebaeude();
        g.setName("G1");
        g.setTyp(Gebaeudetyp.EXTERN);
        g.setPostleitzahl("53567");
        g.setStrasse("Wallroth");
        g.setOrt("Buchholz");
        g.persistAndFlush();
        v.addGebaeude(g);
        v.persistAndFlush();

        Raum r = new Raum("R1", 20);
        r.persistAndFlush();

        g.addRaum(r);
        raumId = r.getId();
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
                .statusCode(BAD_REQUEST.getStatusCode());

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
                .statusCode(BAD_REQUEST.getStatusCode());

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
                .statusCode(CREATED.getStatusCode())
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
                .statusCode(BAD_REQUEST.getStatusCode());
    }

    @Test
    @TestSecurity(user = "admin@test.de", roles = "ADMIN")
    void testRaumVerfuegbarkeitCrossEvent() {
        final Long[] s1Id = new Long[1];

        // Daten in einer eigenen Transaktion vorbereiten und committen
        QuarkusTransaction.requiringNew().run(() -> {
            // Slot in Event 1
            Slot s1 = new Slot();
            s1.setDescription("Slot E1");
            s1.setStartTime(LocalDateTime.of(2025, 10, 1, 9, 0));
            s1.setEndTime(LocalDateTime.of(2025, 10, 1, 10, 0));
            s1.persistAndFlush();

            Veranstaltung.<Veranstaltung>findById(vid).addSlot(s1);

            s1Id[0] = s1.getId();

            // Slot in Event 2 (zeitlich überschneidend)
            Slot s2 = new Slot();
            s2.setDescription("Slot E2");
            s2.setStartTime(LocalDateTime.of(2025, 10, 1, 9, 30));
            s2.setEndTime(LocalDateTime.of(2025, 10, 1, 10, 30));
            s2.persistAndFlush();

            Veranstaltung otherV = Veranstaltung.findById(otherVid);
            otherV.addSlot(s2);


            Raum r = Raum.findById(raumId);

            // Raum in Event 2 als belegt markieren
            RaumVerfuegbarkeit rv2 = new RaumVerfuegbarkeit(r, otherV, Set.of(s2.getId()));

            rv2.persistAndFlush();
        });

        // Abfrage für Event 1: Raum sollte für s1 als "blocked" markiert sein
        List<RaumVerfuegbarkeitDto> dtos = given()
                .when().get("/api/admin/veranstaltungen/{vid}/raeume/verfuegbarkeiten", vid)
                .then()
                .statusCode(OK.getStatusCode())
                .extract().body().jsonPath().getList(".", RaumVerfuegbarkeitDto.class);

        RaumVerfuegbarkeitDto target = dtos.stream()
                .filter(d -> d.getVerfuegbareSlotIds().contains(s1Id[0]))
                .findFirst().orElseThrow();

        assertThat(target.isBlockedByOtherEvent).describedAs("Raum sollte durch anderes Event blockiert sein").isTrue();
        assertThat(target.blockingEventName).isEqualTo("Anderes Event");
    }
}
