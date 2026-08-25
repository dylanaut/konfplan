package kreyj.konfplan.adapter.in.web;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.InjectMock;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.common.http.TestHTTPEndpoint;
import io.quarkus.test.h2.H2DatabaseTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import kreyj.konfplan.adapter.in.web.dto.TeilnehmerPasswortPdfRequestDto;
import kreyj.konfplan.domain.exception.KeycloakProvisioningException;
import kreyj.konfplan.domain.service.KeycloakUserProvisioningService;
import kreyj.konfplan.persistence.Admin;
import kreyj.konfplan.persistence.Teilnehmer;
import kreyj.konfplan.persistence.Veranstaltung;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static io.restassured.RestAssured.given;
import static jakarta.ws.rs.core.Response.Status.BAD_REQUEST;
import static jakarta.ws.rs.core.Response.Status.OK;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;

@QuarkusTest
@TestSecurity(user = "admin@example.com", roles = "ADMIN")
@QuarkusTestResource(H2DatabaseTestResource.class)
@TestHTTPEndpoint(AdminResource.class)
class AdminResourceTeilnehmerPasswortPdfTest extends DatabaseCleaner {

    @InjectMock
    KeycloakUserProvisioningService keycloakUserProvisioningService;

    Long veranstaltungId;
    Long teilnehmer1Id;
    Long teilnehmer2Id;


    @BeforeEach
    void setup() {
        QuarkusTransaction.requiringNew().run(() -> {
            Admin admin = new Admin();
            admin.assignLoginName("admin@example.com");
            admin.setEmail("admin@example.com");
            admin.persist();

            Veranstaltung v = new Veranstaltung();
            v.setName("Passwort-PDF-Test-Event");
            v.setBeginntAm(LocalDateTime.now().plusDays(1));
            v.persist();
            veranstaltungId = v.getId();
            admin.addVeranstaltung(v);

            Teilnehmer t1 = new Teilnehmer();
            t1.assignLoginName("pdf.teilnehmer1");
            t1.setFirstName("Erika");
            t1.setLastName("Musterfrau");
            t1.persist();
            t1.addVeranstaltung(v);
            teilnehmer1Id = t1.getId();

            Teilnehmer t2 = new Teilnehmer();
            t2.assignLoginName("pdf.teilnehmer2");
            t2.setFirstName("Max");
            t2.setLastName("Mustermann");
            t2.persist();
            t2.addVeranstaltung(v);
            teilnehmer2Id = t2.getId();
        });
    }


    @Test
    void happyPath_returnsEncryptedPdf() {
        byte[] pdf = given()
            .contentType("application/json")
            .body(new TeilnehmerPasswortPdfRequestDto(List.of(teilnehmer1Id, teilnehmer2Id), "geheimgeheim"))
            .when().post("/veranstaltungen/{vid}/teilnehmer/passwoerter/pdf", veranstaltungId)
            .then()
            .statusCode(OK.getStatusCode())
            .contentType("application/pdf")
            .header("X-KonfPlan-Failed-Teilnehmer", org.hamcrest.Matchers.nullValue())
            .extract().asByteArray();

        assertThat(pdf).isNotEmpty();
    }


    @Test
    void partialFailure_stillReturnsPdfAndListsFailedLogin() {
        Teilnehmer t2 = Teilnehmer.findById(teilnehmer2Id);
        doThrow(new KeycloakProvisioningException("Keycloak nicht erreichbar"))
            .when(keycloakUserProvisioningService).resetPassword(eq(t2), any());

        byte[] pdf = given()
            .contentType("application/json")
            .body(new TeilnehmerPasswortPdfRequestDto(List.of(teilnehmer1Id, teilnehmer2Id), "geheimgeheim"))
            .when().post("/veranstaltungen/{vid}/teilnehmer/passwoerter/pdf", veranstaltungId)
            .then()
            .statusCode(OK.getStatusCode())
            .contentType("application/pdf")
            .header("X-KonfPlan-Failed-Teilnehmer", org.hamcrest.Matchers.equalTo("pdf.teilnehmer2"))
            .extract().asByteArray();

        assertThat(pdf).isNotEmpty();
    }


    @Test
    void totalFailure_returnsBadRequest() {
        Teilnehmer t1 = Teilnehmer.findById(teilnehmer1Id);
        Teilnehmer t2 = Teilnehmer.findById(teilnehmer2Id);
        doThrow(new KeycloakProvisioningException("Keycloak nicht erreichbar"))
            .when(keycloakUserProvisioningService).resetPassword(eq(t1), any());
        doThrow(new KeycloakProvisioningException("Keycloak nicht erreichbar"))
            .when(keycloakUserProvisioningService).resetPassword(eq(t2), any());

        given()
            .contentType("application/json")
            .body(new TeilnehmerPasswortPdfRequestDto(List.of(teilnehmer1Id, teilnehmer2Id), "geheimgeheim"))
            .when().post("/veranstaltungen/{vid}/teilnehmer/passwoerter/pdf", veranstaltungId)
            .then()
            .statusCode(BAD_REQUEST.getStatusCode());
    }


    @Test
    void teilnehmerNotInVeranstaltung_returnsBadRequest() {
        Long[] otherTeilnehmerId = {0L};
        QuarkusTransaction.requiringNew().run(() -> {
            Teilnehmer t = new Teilnehmer();
            t.assignLoginName("pdf.fremd");
            t.persist();
            otherTeilnehmerId[0] = t.getId();
        });

        given()
            .contentType("application/json")
            .body(new TeilnehmerPasswortPdfRequestDto(List.of(otherTeilnehmerId[0]), "geheimgeheim"))
            .when().post("/veranstaltungen/{vid}/teilnehmer/passwoerter/pdf", veranstaltungId)
            .then()
            .statusCode(BAD_REQUEST.getStatusCode());
    }


    @Test
    void shortPdfPassword_returnsBadRequest() {
        given()
            .contentType("application/json")
            .body(new TeilnehmerPasswortPdfRequestDto(List.of(teilnehmer1Id), "zu kurz"))
            .when().post("/veranstaltungen/{vid}/teilnehmer/passwoerter/pdf", veranstaltungId)
            .then()
            .statusCode(BAD_REQUEST.getStatusCode());
    }
}
