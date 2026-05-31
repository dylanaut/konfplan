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

import static io.restassured.RestAssured.given;
import static jakarta.ws.rs.core.Response.Status.BAD_REQUEST;
import static jakarta.ws.rs.core.Response.Status.CREATED;
import static jakarta.ws.rs.core.Response.Status.OK;
import static kreyj.konfplan.persistence.RaumVerfuegbarkeitId.rvIdL;
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
        v.setName("Haupt Veranstaltung");
        v.setBeginntAm(LocalDateTime.of(2025, 10, 1, 8, 0));
        v.setEndetAm(LocalDateTime.of(2025, 10, 1, 18, 0));
        v.persistAndFlush();
        vid = v.getId();

        // Andere Veranstaltung (zeitgleich)
        Veranstaltung v2 = new Veranstaltung();
        v2.setName("Andere Veranstaltung");
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

    /**
     * Testet die Funktionalität der Raumverfügbarkeitshandhabung über verschiedene Veranstaltungen hinweg.
     * Die Methode verifiziert, dass ein Raum, der in einer Veranstaltung gebucht wurde, seine Verfügbarkeit
     * für überlappende Zeitfenster in einer anderen Veranstaltung blockiert.
     *
     * Testablauf:
     * - Erstellt ein Zeitfenster in einer Veranstaltung und ein weiteres überlappendes Zeitfenster in einer anderen Veranstaltung.
     * - Markiert den Raum als belegt durch die zweite Veranstaltung.
     * - Verifiziert, dass der Raum für das Zeitfenster der ersten Veranstaltung korrekt als blockiert angezeigt wird
     *   aufgrund der Überlappung mit der zweiten Veranstaltung.
     *
     * Testerwartungen:
     * - Stellt sicher, dass der Raum für die erste Veranstaltung aufgrund der
     *   konfliktierenden Buchung in der zweiten Veranstaltung als "blockiert" gekennzeichnet ist.
     * - Validiert, dass die Veranstaltung, die für die Blockierung des Raumes verantwortlich ist, korrekt
     *   in der Antwort identifiziert wird.
     */

    @Test
    @TestSecurity(user = "admin@test.de", roles = "ADMIN")
    @Transactional
    void testRaumVerfuegbarkeitCrossEvent() {
        final Veranstaltung veranstaltung = Veranstaltung.findById(vid);
        final Long[] s1IdArray = {0L};

        QuarkusTransaction.requiringNew().run(() -> {
            // Slot in Veranstaltung 1
            Slot s1 = new Slot("Slot E1",
                    LocalDateTime.of(2025, 10, 1, 9, 0),
                    LocalDateTime.of(2025, 10, 1, 10, 0), veranstaltung);
            s1.persistAndFlush();
            s1IdArray[0] = s1.getId();
            veranstaltung.addSlot(s1);

            Veranstaltung otherV = Veranstaltung.findById(otherVid);
            // Slot in Veranstaltung 2 (zeitlich überschneidend)
            Slot s2 = new Slot("Slot E2",
                    LocalDateTime.of(2025, 10, 1, 9, 30),
                    LocalDateTime.of(2025, 10, 1, 10, 30), otherV);
            s2.persistAndFlush();

            otherV.addSlot(s2);
            otherV.persist();
        });

        Long s1Id = s1IdArray[0];
        
        // Raum in Veranstaltung 2 als belegt markieren
//        RaumVerfuegbarkeit rv2 = new RaumVerfuegbarkeit(r, otherV, List.of(s2.getId()));
//        rv2.persistAndFlush();

        RaumVerfuegbarkeit rv1 = RaumVerfuegbarkeit.findById(rvIdL(raumId, vid));
        RaumVerfuegbarkeit rv2 = RaumVerfuegbarkeit.findById(rvIdL(raumId, otherVid));
        System.out.println(rv2);

        // Abfrage für Veranstaltung 1: Raum sollte für s1 als "blocked" markiert sein
        List<RaumVerfuegbarkeitDto> dtos = given()
                .when().get("/api/admin/veranstaltungen/{vid}/raeume/verfuegbarkeiten", vid)
                .then()
                .statusCode(OK.getStatusCode())
                .extract().body().jsonPath().getList(".", RaumVerfuegbarkeitDto.class);

        RaumVerfuegbarkeitDto target = dtos.stream()
                .filter(d -> d.verfuegbareSlotIds.contains(s1Id))
                .findFirst().orElseThrow();

        assertThat(target.isBlockedByOtherEvent).describedAs("Raum sollte durch andere Veranstaltung blockiert sein").isTrue();
        assertThat(target.blockingEventName).isEqualTo("Andere Veranstaltung");
    }
}
