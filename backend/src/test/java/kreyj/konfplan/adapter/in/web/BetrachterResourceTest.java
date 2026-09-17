package kreyj.konfplan.adapter.in.web;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.common.http.TestHTTPEndpoint;
import io.quarkus.test.h2.H2DatabaseTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.oidc.Claim;
import io.quarkus.test.security.oidc.OidcSecurity;
import jakarta.transaction.Transactional;
import kreyj.konfplan.persistence.Betrachter;
import kreyj.konfplan.persistence.Gruppenkategorie;
import kreyj.konfplan.persistence.GruppenkategorieWert;
import kreyj.konfplan.persistence.Referent;
import kreyj.konfplan.persistence.Teilnehmer;
import kreyj.konfplan.persistence.Veranstaltung;
import kreyj.konfplan.persistence.Wahlvortrag;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static io.restassured.RestAssured.given;
import static jakarta.ws.rs.core.Response.Status.FORBIDDEN;
import static jakarta.ws.rs.core.Response.Status.NOT_FOUND;
import static jakarta.ws.rs.core.Response.Status.OK;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;

@QuarkusTest
@QuarkusTestResource(H2DatabaseTestResource.class)
@TestHTTPEndpoint(BetrachterResource.class)
class BetrachterResourceTest extends DatabaseCleaner {

    Long eigeneVeranstaltungId;
    Long fremdeVeranstaltungId;
    Long sichtbarerTeilnehmerId;
    Long wahlvortragId;

    @BeforeEach
    @Transactional
    void setup() {
        Veranstaltung eigene = new Veranstaltung();
        eigene.setName("Eigene Veranstaltung");
        eigene.setBeginntAm(LocalDateTime.now());
        eigene.persist();
        eigeneVeranstaltungId = eigene.getId();

        Veranstaltung fremde = new Veranstaltung();
        fremde.setName("Fremde Veranstaltung");
        fremde.setBeginntAm(LocalDateTime.now());
        fremde.persist();
        fremdeVeranstaltungId = fremde.getId();

        Gruppenkategorie klasse = new Gruppenkategorie(eigene, "Klasse", false, false);
        klasse.persist();
        GruppenkategorieWert gruppeA = new GruppenkategorieWert(klasse, "Gruppe A");
        gruppeA.persist();
        GruppenkategorieWert gruppeB = new GruppenkategorieWert(klasse, "Gruppe B");
        gruppeB.persist();

        Betrachter betrachter = new Betrachter();
        betrachter.assignLoginName("betrachter.rest@test.de");
        betrachter.setEmail("betrachter.rest@test.de");
        betrachter.persist();
        betrachter.addVeranstaltung(eigene);
        betrachter.addGruppenwert(gruppeA);

        Teilnehmer sichtbar = new Teilnehmer();
        sichtbar.assignLoginName("tn-sichtbar@test.de");
        sichtbar.setEmail("tn-sichtbar@test.de");
        sichtbar.persist();
        sichtbar.addVeranstaltung(eigene);
        sichtbar.addGruppenwert(gruppeA);
        sichtbarerTeilnehmerId = sichtbar.getId();

        Referent referent = new Referent();
        referent.assignLoginName("referent.vortraege@test.de");
        referent.setEmail("referent.vortraege@test.de");
        referent.persist();
        Wahlvortrag wahlvortrag = Wahlvortrag.create("Einführung in die Robotik", "Inhalt", referent, false, 1, eigene);
        wahlvortragId = wahlvortrag.getId();

        Teilnehmer unsichtbar = new Teilnehmer();
        unsichtbar.assignLoginName("tn-unsichtbar@test.de");
        unsichtbar.setEmail("tn-unsichtbar@test.de");
        unsichtbar.persist();
        unsichtbar.addVeranstaltung(eigene);
        unsichtbar.addGruppenwert(gruppeB);
    }


    @Test
    @TestSecurity(user = "betrachter.rest@test.de", roles = "BETRACHTER")
    @OidcSecurity(claims = {@Claim(key = "preferred_username", value = "betrachter.rest@test.de")})
    void getTeilnehmer_liefertNurDieEigeneGruppe() {
        given()
            .when().get("/veranstaltungen/{vid}/teilnehmer", eigeneVeranstaltungId)
            .then()
            .statusCode(OK.getStatusCode())
            .body("loginName", hasSize(1))
            .body("loginName", contains("tn-sichtbar@test.de"));
    }


    @Test
    @TestSecurity(user = "betrachter.rest@test.de", roles = "BETRACHTER")
    @OidcSecurity(claims = {@Claim(key = "preferred_username", value = "betrachter.rest@test.de")})
    void getTeilnehmer_verweigertZugriffAufFremdeVeranstaltung() {
        given()
            .when().get("/veranstaltungen/{vid}/teilnehmer", fremdeVeranstaltungId)
            .then()
            .statusCode(NOT_FOUND.getStatusCode());
    }


    @Test
    @TestSecurity(user = "betrachter.rest@test.de", roles = "BETRACHTER")
    @OidcSecurity(claims = {@Claim(key = "preferred_username", value = "betrachter.rest@test.de")})
    void getVerfuegbarkeiten_liefertNurFuerSichtbareTeilnehmer() {
        given()
            .when().get("/veranstaltungen/{vid}/verfuegbarkeiten", eigeneVeranstaltungId)
            .then()
            .statusCode(OK.getStatusCode())
            .body("nutzerId", contains(sichtbarerTeilnehmerId.intValue()));
    }


    @Test
    @TestSecurity(user = "anderer.betrachter@test.de", roles = "BETRACHTER")
    @OidcSecurity(claims = {@Claim(key = "preferred_username", value = "anderer.betrachter@test.de")})
    void getVeranstaltungen_ohneEigeneZuordnung_istLeer() {
        given()
            .when().get("/veranstaltungen")
            .then()
            .statusCode(OK.getStatusCode())
            .body("$", hasSize(0));
    }


    @Test
    @TestSecurity(user = "teilnehmer.rest@test.de", roles = "TEILNEHMER")
    @OidcSecurity(claims = {@Claim(key = "preferred_username", value = "teilnehmer.rest@test.de")})
    void betrachterEndpunkte_sindFuerAndereRollenVerboten() {
        given()
            .when().get("/veranstaltungen/{vid}/teilnehmer", eigeneVeranstaltungId)
            .then()
            .statusCode(FORBIDDEN.getStatusCode());
    }


    @Test
    @TestSecurity(user = "betrachter.rest@test.de", roles = "BETRACHTER")
    @OidcSecurity(claims = {@Claim(key = "preferred_username", value = "betrachter.rest@test.de")})
    void getVortraege_liefertTitelDerWahlvortraege() {
        given()
            .when().get("/veranstaltungen/{vid}/vortraege", eigeneVeranstaltungId)
            .then()
            .statusCode(OK.getStatusCode())
            .body("id", contains(wahlvortragId.intValue()))
            .body("titel", contains("Einführung in die Robotik"));
    }


    @Test
    @TestSecurity(user = "betrachter.rest@test.de", roles = "BETRACHTER")
    @OidcSecurity(claims = {@Claim(key = "preferred_username", value = "betrachter.rest@test.de")})
    void getVortraege_verweigertZugriffAufFremdeVeranstaltung() {
        given()
            .when().get("/veranstaltungen/{vid}/vortraege", fremdeVeranstaltungId)
            .then()
            .statusCode(NOT_FOUND.getStatusCode());
    }
}
