package kreyj.konfplan.adapter.in.web;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.common.http.TestHTTPEndpoint;
import io.quarkus.test.h2.H2DatabaseTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.oidc.Claim;
import io.quarkus.test.security.oidc.OidcSecurity;
import jakarta.transaction.Transactional;
import kreyj.konfplan.persistence.Nachricht;
import kreyj.konfplan.persistence.NachrichtKategorie;
import kreyj.konfplan.persistence.Nutzer;
import kreyj.konfplan.persistence.Teilnehmer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static io.restassured.RestAssured.given;
import static jakarta.ws.rs.core.Response.Status.FORBIDDEN;
import static jakarta.ws.rs.core.Response.Status.NOT_FOUND;
import static jakarta.ws.rs.core.Response.Status.NO_CONTENT;
import static jakarta.ws.rs.core.Response.Status.OK;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

@QuarkusTest
@QuarkusTestResource(H2DatabaseTestResource.class)
@TestHTTPEndpoint(NachrichtResource.class)
class NachrichtResourceTest extends DatabaseCleaner {

    @BeforeEach
    @Transactional
    void setup() {
        Teilnehmer tom = new Teilnehmer();
        tom.assignLoginName("tom.teilnehmer");
        tom.setEmail("tom@test.de");
        tom.setFirstName("Tom");
        tom.setLastName("Teilnehmer");
        tom.persist();

        Teilnehmer lisa = new Teilnehmer();
        lisa.assignLoginName("lisa.teilnehmer");
        lisa.setEmail("lisa@test.de");
        lisa.setFirstName("Lisa");
        lisa.setLastName("Teilnehmer");
        lisa.persist();
    }


    @Transactional
    Nachricht erstelleNachrichtFuer(String loginName, String titel) {
        Nachricht n = new Nachricht();
        n.setEmpfaenger(Nutzer.findByLoginName(loginName));
        n.setTitel(titel);
        n.setInhalt("Inhalt");
        n.setKategorie(NachrichtKategorie.VORTRAG_ZURUECKGEZOGEN);
        n.setErstelltAm(LocalDateTime.now());
        n.persist();
        return n;
    }


    @Test
    @TestSecurity(user = "tom.teilnehmer", roles = "TEILNEHMER")
    @OidcSecurity(claims = {@Claim(key = "preferred_username", value = "tom.teilnehmer")})
    void getEigene_liefertNurEigeneNachrichten() {
        erstelleNachrichtFuer("tom.teilnehmer", "Für Tom");
        erstelleNachrichtFuer("lisa.teilnehmer", "Für Lisa");

        given()
            .when().get()
            .then()
            .statusCode(OK.getStatusCode())
            .body("$", hasSize(1))
            .body("[0].titel", is("Für Tom"));
    }


    @Test
    @TestSecurity(user = "tom.teilnehmer", roles = "TEILNEHMER")
    @OidcSecurity(claims = {@Claim(key = "preferred_username", value = "tom.teilnehmer")})
    void getUngeleseneAnzahl_zaehltNurUngelesene() {
        Nachricht n1 = erstelleNachrichtFuer("tom.teilnehmer", "Eins");
        erstelleNachrichtFuer("tom.teilnehmer", "Zwei");

        given()
            .when().put("/" + n1.getId() + "/gelesen")
            .then()
            .statusCode(NO_CONTENT.getStatusCode());

        given()
            .when().get("/ungelesen-anzahl")
            .then()
            .statusCode(OK.getStatusCode())
            .body(is("1"));
    }


    @Test
    @TestSecurity(user = "lisa.teilnehmer", roles = "TEILNEHMER")
    @OidcSecurity(claims = {@Claim(key = "preferred_username", value = "lisa.teilnehmer")})
    void markiereAlsGelesen_fremdeNachricht_liefert403() {
        Nachricht n = erstelleNachrichtFuer("tom.teilnehmer", "Für Tom");

        given()
            .when().put("/" + n.getId() + "/gelesen")
            .then()
            .statusCode(FORBIDDEN.getStatusCode());
    }


    @Test
    @TestSecurity(user = "tom.teilnehmer", roles = "TEILNEHMER")
    @OidcSecurity(claims = {@Claim(key = "preferred_username", value = "tom.teilnehmer")})
    void markiereAlsGelesen_unbekannteNachricht_liefert404() {
        given()
            .when().put("/999999/gelesen")
            .then()
            .statusCode(NOT_FOUND.getStatusCode());
    }
}
