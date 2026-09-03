package kreyj.konfplan.adapter.in.web;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.common.http.TestHTTPEndpoint;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.h2.H2DatabaseTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import jakarta.transaction.Transactional;
import kreyj.konfplan.adapter.in.web.dto.RaumVerfuegbarkeitDto;
import kreyj.konfplan.persistence.Gebaeude;
import kreyj.konfplan.persistence.Gebaeudetyp;
import kreyj.konfplan.persistence.Raum;
import kreyj.konfplan.persistence.RaumVerfuegbarkeit;
import kreyj.konfplan.persistence.Slot;
import kreyj.konfplan.persistence.Veranstaltung;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URL;
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
@TestSecurity(user = "admin@test.de", roles = "ORGANISATOR")
@TestHTTPEndpoint(VeranstaltungResource.class)
class SlotUndRaumTest extends DatabaseCleaner {
    @TestHTTPResource
    @TestHTTPEndpoint(OrganisatorResource.class)
    URL adminEndpoint;

    Long v1_Id;
    Long v2_Id;
    Long raumId;


    @BeforeEach
    @Transactional
    void setup() {
        // Hauptveranstaltung
        Veranstaltung v1 = new Veranstaltung();
        v1.setName("Haupt Veranstaltung");
        v1.setBeginntAm(LocalDateTime.of(2025, 10, 1, 8, 0));
        v1.setEndetAm(LocalDateTime.of(2025, 10, 1, 18, 0));
        v1.persist();
        v1_Id = v1.getId();

        // Andere Veranstaltung (zeitgleich)
        Veranstaltung v2 = new Veranstaltung();
        v2.setName("Andere Veranstaltung");
        v2.setBeginntAm(LocalDateTime.of(2025, 10, 1, 8, 0));
        v2.setEndetAm(LocalDateTime.of(2025, 10, 1, 18, 0));
        v2.persist();
        v2_Id = v2.getId();

        Gebaeude g = new Gebaeude();
        g.setName("G1");
        g.setTyp(Gebaeudetyp.EXTERN);
        g.setPostleitzahl("53567");
        g.setStrasse("Wallroth");
        g.setOrt("Buchholz");
        g.persist();

        v1.addGebaeude(g);
        v2.addGebaeude(g);

        Raum raum = new Raum("R1", 20);
        raum.persist();

        g.addRaum(raum);
        raumId = raum.getId();
    }


    @Test
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
            .when().post("/{vid}/slots", v1_Id)
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
            .when().post("/{vid}/slots", v1_Id)
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
            .when().post("/{vid}/slots", v1_Id)
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
            .when().post("/{vid}/slots", v1_Id)
            .then()
            .statusCode(BAD_REQUEST.getStatusCode());
    }


    /**
     * Testet die Funktionalität der Raumverfügbarkeitshandhabung über verschiedene Veranstaltungen hinweg.
     * Die Methode verifiziert, dass ein Raum, der in einer Veranstaltung gebucht wurde, seine Verfügbarkeit
     * für überlappende Zeitfenster in einer anderen Veranstaltung blockiert.
     * <p>
     * Testablauf:
     * - Erstellt ein Zeitfenster in einer Veranstaltung und ein weiteres überlappendes Zeitfenster in einer anderen Veranstaltung.
     * - Markiert den Raum als belegt durch die zweite Veranstaltung.
     * - Verifiziert, dass der Raum für das Zeitfenster der ersten Veranstaltung korrekt als blockiert angezeigt wird
     * aufgrund der Überlappung mit der zweiten Veranstaltung.
     * <p>
     * Testerwartungen:
     * - Stellt sicher, dass der Raum für die erste Veranstaltung aufgrund der
     * konfliktierenden Buchung in der zweiten Veranstaltung als "blockiert" gekennzeichnet ist.
     * - Validiert, dass die Veranstaltung, die für die Blockierung des Raumes verantwortlich ist, korrekt
     * in der Antwort identifiziert wird.
     */

    @Test
    void testRaumVerfuegbarkeitCrossEvent() {
        final Long[] slotIdArray = {-1L, -1L};

        QuarkusTransaction.requiringNew().run(() -> {
            final Veranstaltung veranstaltung = Veranstaltung.findById(v1_Id);
            // Slot in Veranstaltung 1
            Slot s1 = new Slot("Slot E1",
                LocalDateTime.of(2025, 10, 1, 9, 0),
                LocalDateTime.of(2025, 10, 1, 10, 0), veranstaltung);
            s1.persist();
            slotIdArray[0] = s1.getId();
            veranstaltung.addSlot(s1);

            Veranstaltung otherV = Veranstaltung.findById(v2_Id);
            // Slot in Veranstaltung 2 (zeitlich überschneidend)
            Slot s2 = new Slot("Slot E2",
                LocalDateTime.of(2025, 10, 1, 9, 30),
                LocalDateTime.of(2025, 10, 1, 10, 30), otherV);
            s2.persist();
            slotIdArray[1] = s2.getId();
            otherV.addSlot(s2);

            // Raum in Veranstaltung 2 als belegt markieren
            RaumVerfuegbarkeit rv2 = RaumVerfuegbarkeit.findById(rvIdL(raumId, v2_Id));
            rv2.removeSlot(s2);
        });

        Long s1Id = slotIdArray[0];
        Long s2Id = slotIdArray[1];

        assertThat(isRaumVerfuegbar(raumId, s1Id, v1_Id)).isTrue();
        assertThat(isRaumVerfuegbar(raumId, s1Id, v2_Id)).isFalse();

        assertThat(isRaumVerfuegbar(raumId, s2Id, v1_Id)).isFalse();
        assertThat(isRaumVerfuegbar(raumId, s2Id, v2_Id)).isFalse();

        // Abfrage für Veranstaltung 1: Raum sollte für s1 als "blocked" markiert sein
        List<RaumVerfuegbarkeitDto> dtos = given()
            .baseUri(adminEndpoint.toString())
            .basePath("/veranstaltungen")
            .when().get("/{vid}/raeume/verfuegbarkeiten", v1_Id)
            .then()
            .statusCode(OK.getStatusCode())
            .extract()
            .body()
            .jsonPath()
            .getList(".", RaumVerfuegbarkeitDto.class);

        RaumVerfuegbarkeitDto target = dtos.stream()
            .filter(d -> d.verfuegbareSlotIds.contains(s1Id))
            .findFirst().orElseThrow();

        assertThat(target.isBlockedByOtherEvent).describedAs("Raum sollte durch andere Veranstaltung blockiert sein").isTrue();
        assertThat(target.blockingEventName).isEqualTo("Andere Veranstaltung");
    }
}
