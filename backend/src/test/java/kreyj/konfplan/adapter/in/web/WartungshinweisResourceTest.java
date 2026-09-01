package kreyj.konfplan.adapter.in.web;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.common.http.TestHTTPEndpoint;
import io.quarkus.test.h2.H2DatabaseTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.oidc.Claim;
import io.quarkus.test.security.oidc.OidcSecurity;
import io.restassured.http.ContentType;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static jakarta.ws.rs.core.Response.Status.BAD_REQUEST;
import static jakarta.ws.rs.core.Response.Status.FORBIDDEN;
import static jakarta.ws.rs.core.Response.Status.NO_CONTENT;
import static jakarta.ws.rs.core.Response.Status.OK;
import static org.hamcrest.Matchers.nullValue;

@QuarkusTest
@QuarkusTestResource(H2DatabaseTestResource.class)
@TestHTTPEndpoint(WartungshinweisResource.class)
class WartungshinweisResourceTest extends DatabaseCleaner {

    @Test
    @TestSecurity(user = "tom.teilnehmer", roles = "TEILNEHMER")
    @OidcSecurity(claims = {@Claim(key = "preferred_username", value = "tom.teilnehmer")})
    void get_ohneGesetzteAnkuendigung_liefertLeereFelder() {
        given()
            .when().get()
            .then()
            .statusCode(OK.getStatusCode())
            .body("startZeitpunkt", nullValue())
            .body("endeZeitpunkt", nullValue());
    }

    @Test
    @TestSecurity(user = "tom.teilnehmer", roles = "TEILNEHMER")
    @OidcSecurity(claims = {@Claim(key = "preferred_username", value = "tom.teilnehmer")})
    void setzen_istNurFuerAdminErlaubt() {
        given()
            .contentType(ContentType.JSON)
            .body("{\"startZeitpunkt\": \"2026-01-01T10:00:00\", \"endeZeitpunkt\": \"2026-01-01T10:30:00\"}")
            .when().put()
            .then()
            .statusCode(FORBIDDEN.getStatusCode());
    }

    @Test
    @TestSecurity(user = "anna.admin", roles = "ADMIN")
    @OidcSecurity(claims = {@Claim(key = "preferred_username", value = "anna.admin")})
    void setzen_undWiederAbrufen_liefertGesetzteZeiten() {
        LocalDateTime start = LocalDateTime.now().plusMinutes(10);
        LocalDateTime ende = LocalDateTime.now().plusMinutes(40);

        given()
            .contentType(ContentType.JSON)
            .body("{\"startZeitpunkt\": \"" + start + "\", \"endeZeitpunkt\": \"" + ende + "\"}")
            .when().put()
            .then()
            .statusCode(OK.getStatusCode());

        given()
            .when().get()
            .then()
            .statusCode(OK.getStatusCode())
            .body("startZeitpunkt", org.hamcrest.Matchers.notNullValue())
            .body("endeZeitpunkt", org.hamcrest.Matchers.notNullValue());
    }

    @Test
    @TestSecurity(user = "anna.admin", roles = "ADMIN")
    @OidcSecurity(claims = {@Claim(key = "preferred_username", value = "anna.admin")})
    void setzen_bereitsAbgelaufen_liefertBeimAbrufenLeereFelder() {
        LocalDateTime start = LocalDateTime.now().minusHours(2);
        LocalDateTime ende = LocalDateTime.now().minusHours(1);

        given()
            .contentType(ContentType.JSON)
            .body("{\"startZeitpunkt\": \"" + start + "\", \"endeZeitpunkt\": \"" + ende + "\"}")
            .when().put()
            .then()
            .statusCode(OK.getStatusCode());

        given()
            .when().get()
            .then()
            .statusCode(OK.getStatusCode())
            .body("startZeitpunkt", nullValue())
            .body("endeZeitpunkt", nullValue());
    }

    @Test
    @TestSecurity(user = "anna.admin", roles = "ADMIN")
    @OidcSecurity(claims = {@Claim(key = "preferred_username", value = "anna.admin")})
    void setzen_endeVorStart_wirdAbgelehnt() {
        LocalDateTime start = LocalDateTime.now().plusMinutes(30);
        LocalDateTime ende = LocalDateTime.now().plusMinutes(10);

        given()
            .contentType(ContentType.JSON)
            .body("{\"startZeitpunkt\": \"" + start + "\", \"endeZeitpunkt\": \"" + ende + "\"}")
            .when().put()
            .then()
            .statusCode(BAD_REQUEST.getStatusCode());
    }

    @Test
    @TestSecurity(user = "anna.admin", roles = "ADMIN")
    @OidcSecurity(claims = {@Claim(key = "preferred_username", value = "anna.admin")})
    void loeschen_entferntDieAnkuendigung() {
        LocalDateTime start = LocalDateTime.now().plusMinutes(10);
        LocalDateTime ende = LocalDateTime.now().plusMinutes(40);

        given()
            .contentType(ContentType.JSON)
            .body("{\"startZeitpunkt\": \"" + start + "\", \"endeZeitpunkt\": \"" + ende + "\"}")
            .when().put()
            .then()
            .statusCode(OK.getStatusCode());

        given()
            .when().delete()
            .then()
            .statusCode(NO_CONTENT.getStatusCode());

        given()
            .when().get()
            .then()
            .statusCode(OK.getStatusCode())
            .body("startZeitpunkt", nullValue())
            .body("endeZeitpunkt", nullValue());
    }
}
