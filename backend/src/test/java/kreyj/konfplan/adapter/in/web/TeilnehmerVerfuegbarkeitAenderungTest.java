package kreyj.konfplan.adapter.in.web;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.common.http.TestHTTPEndpoint;
import io.quarkus.test.h2.H2DatabaseTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.oidc.Claim;
import io.quarkus.test.security.oidc.OidcSecurity;
import io.restassured.http.ContentType;
import jakarta.transaction.Transactional;
import kreyj.konfplan.adapter.in.web.dto.NutzerVerfuegbarkeitDto;
import kreyj.konfplan.persistence.Teilnehmer;
import kreyj.konfplan.persistence.Veranstaltung;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Set;

import static io.restassured.RestAssured.given;
import static jakarta.ws.rs.core.Response.Status.FORBIDDEN;
import static jakarta.ws.rs.core.Response.Status.OK;

@QuarkusTest
@QuarkusTestResource(H2DatabaseTestResource.class)
@TestHTTPEndpoint(TeilnehmerResource.class)
class TeilnehmerVerfuegbarkeitAenderungTest extends DatabaseCleaner {

    Long gesperrtesEventId;
    Long freigegebenesEventId;
    Long teilnehmerId;


    @BeforeEach
    @Transactional
    void setup() {
        Veranstaltung gesperrt = new Veranstaltung();
        gesperrt.setName("Event ohne Freischaltung");
        gesperrt.setBeginntAm(LocalDateTime.now().plusDays(10));
        gesperrt.persist();
        gesperrtesEventId = gesperrt.getId();

        Veranstaltung freigegeben = new Veranstaltung();
        freigegeben.setName("Event mit Freischaltung");
        freigegeben.setBeginntAm(LocalDateTime.now().plusDays(10));
        freigegeben.setTeilnehmerAendernVerfuegbarkeit(true);
        freigegeben.persist();
        freigegebenesEventId = freigegeben.getId();

        Teilnehmer t = new Teilnehmer();
        t.assignLoginName("teilnehmer.verf@test.de");
        t.setEmail("teilnehmer.verf@test.de");
        t.persist();
        t.addVeranstaltung(gesperrt);
        t.addVeranstaltung(freigegeben);
        teilnehmerId = t.getId();
    }


    @Test
    @TestSecurity(user = "teilnehmer.verf@test.de", roles = "TEILNEHMER")
    @OidcSecurity(claims = {@Claim(key = "preferred_username", value = "teilnehmer.verf@test.de")})
    void updateVerfuegbarkeit_verboten_wennVeranstaltungNichtFreigeschaltetHat() {
        NutzerVerfuegbarkeitDto dto = new NutzerVerfuegbarkeitDto(teilnehmerId, gesperrtesEventId, Set.of());

        given()
            .contentType(ContentType.JSON)
            .body(dto)
            .when().post("/veranstaltungen/{vid}/verfuegbarkeiten", gesperrtesEventId)
            .then()
            .statusCode(FORBIDDEN.getStatusCode());
    }


    @Test
    @TestSecurity(user = "teilnehmer.verf@test.de", roles = "TEILNEHMER")
    @OidcSecurity(claims = {@Claim(key = "preferred_username", value = "teilnehmer.verf@test.de")})
    void updateVerfuegbarkeit_erlaubt_wennVeranstaltungFreigeschaltetHat() {
        NutzerVerfuegbarkeitDto dto = new NutzerVerfuegbarkeitDto(teilnehmerId, freigegebenesEventId, Set.of());

        given()
            .contentType(ContentType.JSON)
            .body(dto)
            .when().post("/veranstaltungen/{vid}/verfuegbarkeiten", freigegebenesEventId)
            .then()
            .statusCode(OK.getStatusCode());
    }
}
