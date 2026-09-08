package kreyj.konfplan.adapter.in.web;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import kreyj.konfplan.domain.exception.CollisionsException;
import kreyj.konfplan.domain.service.PlanErstellungService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static io.restassured.RestAssured.given;
import static jakarta.ws.rs.core.Response.Status.BAD_REQUEST;
import static org.hamcrest.CoreMatchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;

@QuarkusTest
@TestSecurity(user = "admin@test.de", roles = "ORGANISATOR")
class PlanungResourceTest {

    @InjectMock
    PlanErstellungService planErstellungService;

    /**
     * Regression: der /dzn-Endpunkt deklariert @Produces(TEXT_PLAIN) (für den Erfolgsfall, eine
     * .dzn-Datei zum Download). Ohne explizites .type(APPLICATION_JSON) im
     * BusinessExceptionMapper übernahm die Fehlerantwort dieses TEXT_PLAIN und die Map wurde
     * über deren toString() ("{error=...}") statt als valides JSON serialisiert - das Frontend
     * (JSON.parse) konnte die Konsistenzverstöße dadurch nicht anzeigen (siehe
     * OrganisatorDashboard.vue#extractBlobErrorMessage).
     */
    @Test
    void exportDzn_beiKollision_liefertValidesJson() {
        Mockito.when(planErstellungService.generiereDznVorschau(anyLong(), any(), anyString()))
            .thenThrow(new CollisionsException("Inkonsistente Daten:\nRaum X ist bereits belegt."));

        given()
            .contentType(ContentType.JSON)
            .body("{}")
            .when().post("/api/planungen/1/dzn")
            .then()
            .statusCode(BAD_REQUEST.getStatusCode())
            .contentType(ContentType.JSON)
            .body("error", is("Inkonsistente Daten:\nRaum X ist bereits belegt."));
    }
}
