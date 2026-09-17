package kreyj.konfplan.adapter.in.web;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.common.http.TestHTTPEndpoint;
import io.quarkus.test.h2.H2DatabaseTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.oidc.Claim;
import io.quarkus.test.security.oidc.OidcSecurity;
import jakarta.transaction.Transactional;
import kreyj.konfplan.persistence.Anwesenheit;
import kreyj.konfplan.persistence.Gebaeude;
import kreyj.konfplan.persistence.Gebaeudetyp;
import kreyj.konfplan.persistence.Raum;
import kreyj.konfplan.persistence.Slot;
import kreyj.konfplan.persistence.Teilnehmer;
import kreyj.konfplan.persistence.Veranstaltung;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static io.restassured.RestAssured.given;
import static jakarta.ws.rs.core.Response.Status.FORBIDDEN;
import static jakarta.ws.rs.core.Response.Status.NOT_FOUND;
import static jakarta.ws.rs.core.Response.Status.OK;
import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
@QuarkusTestResource(H2DatabaseTestResource.class)
@TestHTTPEndpoint(TeilnehmerResource.class)
class AnwesenheitCheckinResourceTest extends DatabaseCleaner {

    Long eigeneVeranstaltungId;
    Long fremdeVeranstaltungId;
    Long raumId;
    Long fremderRaumId;

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

        Gebaeude gebaeude = new Gebaeude();
        gebaeude.setName("Hauptgebäude");
        gebaeude.setTyp(Gebaeudetyp.SCHULE);
        gebaeude.setPostleitzahl("12345");
        gebaeude.setOrt("Testort");
        gebaeude.setStrasse("Teststraße");
        gebaeude.persist();

        Raum raum = new Raum();
        raum.setName("Raum 1");
        raum.setKapazitaet(30);
        raum.persist();
        gebaeude.addRaum(raum);
        eigene.addGebaeude(gebaeude);
        raumId = raum.getId();

        Gebaeude fremdesGebaeude = new Gebaeude();
        fremdesGebaeude.setName("Anderes Gebäude");
        fremdesGebaeude.setTyp(Gebaeudetyp.SCHULE);
        fremdesGebaeude.setPostleitzahl("54321");
        fremdesGebaeude.setOrt("Andernorts");
        fremdesGebaeude.setStrasse("Andere Straße");
        fremdesGebaeude.persist();
        Raum fremderRaum = new Raum();
        fremderRaum.setName("Fremder Raum");
        fremderRaum.setKapazitaet(10);
        fremderRaum.persist();
        fremdesGebaeude.addRaum(fremderRaum);
        fremdeRaumZuweisen(fremdesGebaeude);
        fremderRaumId = fremderRaum.getId();

        Slot aktiverSlot = new Slot("Aktueller Slot", LocalDateTime.now().minusMinutes(20), LocalDateTime.now().plusMinutes(20), eigene);
        aktiverSlot.persist();
        eigene.addSlot(aktiverSlot);

        Teilnehmer teilnehmer = new Teilnehmer();
        teilnehmer.assignLoginName("checkin.teilnehmer@test.de");
        teilnehmer.setEmail("checkin.teilnehmer@test.de");
        teilnehmer.persist();
        teilnehmer.addVeranstaltung(Veranstaltung.findById(eigeneVeranstaltungId));
    }


    private void fremdeRaumZuweisen(Gebaeude fremdesGebaeude) {
        Veranstaltung.<Veranstaltung>findById(fremdeVeranstaltungId).addGebaeude(fremdesGebaeude);
    }


    @Test
    @TestSecurity(user = "checkin.teilnehmer@test.de", roles = "TEILNEHMER")
    @OidcSecurity(claims = {@Claim(key = "preferred_username", value = "checkin.teilnehmer@test.de")})
    void checkIn_mitEigenerVeranstaltungUndRaum_registriertAnwesenheit() {
        given()
            .contentType("application/json")
            .when().post("/veranstaltungen/{vid}/anwesenheit?raumId=" + raumId, eigeneVeranstaltungId)
            .then()
            .statusCode(OK.getStatusCode())
            .body("aktiverTermin", org.hamcrest.Matchers.is(true));

        assertThat(Anwesenheit.count()).isEqualTo(1);
    }


    @Test
    @TestSecurity(user = "checkin.teilnehmer@test.de", roles = "TEILNEHMER")
    @OidcSecurity(claims = {@Claim(key = "preferred_username", value = "checkin.teilnehmer@test.de")})
    void checkIn_mitRaumEinerAnderenVeranstaltung_liefert404() {
        given()
            .contentType("application/json")
            .when().post("/veranstaltungen/{vid}/anwesenheit?raumId=" + fremderRaumId, eigeneVeranstaltungId)
            .then()
            .statusCode(NOT_FOUND.getStatusCode());

        assertThat(Anwesenheit.count()).isZero();
    }


    @Test
    @TestSecurity(user = "checkin.teilnehmer@test.de", roles = "TEILNEHMER")
    @OidcSecurity(claims = {@Claim(key = "preferred_username", value = "checkin.teilnehmer@test.de")})
    void checkIn_beiVeranstaltungOhneEigeneTeilnahme_liefert403() {
        given()
            .contentType("application/json")
            .when().post("/veranstaltungen/{vid}/anwesenheit?raumId=" + fremderRaumId, fremdeVeranstaltungId)
            .then()
            .statusCode(FORBIDDEN.getStatusCode());

        assertThat(Anwesenheit.count()).isZero();
    }


    @Test
    @TestSecurity(user = "anderer.referent@test.de", roles = "REFERENT")
    @OidcSecurity(claims = {@Claim(key = "preferred_username", value = "anderer.referent@test.de")})
    void checkIn_alsAndereRolle_liefert403() {
        given()
            .contentType("application/json")
            .when().post("/veranstaltungen/{vid}/anwesenheit?raumId=" + raumId, eigeneVeranstaltungId)
            .then()
            .statusCode(FORBIDDEN.getStatusCode());
    }
}
