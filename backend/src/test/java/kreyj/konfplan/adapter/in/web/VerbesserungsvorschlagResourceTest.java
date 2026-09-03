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
import kreyj.konfplan.adapter.in.web.dto.VerbesserungsvorschlagDto;
import kreyj.konfplan.persistence.Organisator;
import kreyj.konfplan.persistence.Nutzer;
import kreyj.konfplan.persistence.Teilnehmer;
import kreyj.konfplan.persistence.Verbesserungsvorschlag;
import kreyj.konfplan.persistence.VorschlagStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static io.restassured.RestAssured.given;
import static jakarta.ws.rs.core.Response.Status.CREATED;
import static jakarta.ws.rs.core.Response.Status.FORBIDDEN;
import static jakarta.ws.rs.core.Response.Status.NO_CONTENT;
import static jakarta.ws.rs.core.Response.Status.OK;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;

@QuarkusTest
@QuarkusTestResource(H2DatabaseTestResource.class)
@TestHTTPEndpoint(VerbesserungsvorschlagResource.class)
class VerbesserungsvorschlagResourceTest extends DatabaseCleaner {

    @BeforeEach
    @Transactional
    void setup() {
        Teilnehmer t = new Teilnehmer();
        t.assignLoginName("tom.teilnehmer");
        t.setEmail("tom@test.de");
        t.setFirstName("Tom");
        t.setLastName("Teilnehmer");
        t.persist();

        Organisator a = new Organisator();
        a.assignLoginName("anna.admin");
        a.setEmail("anna@test.de");
        a.setFirstName("Anna");
        a.setLastName("Organisator");
        a.persist();
    }

    @Test
    @TestSecurity(user = "tom.teilnehmer", roles = "TEILNEHMER")
    @OidcSecurity(claims = {@Claim(key = "preferred_username", value = "tom.teilnehmer")})
    void create_persistsVorschlagMitErstellerUndOffenemStatus() {
        VerbesserungsvorschlagDto dto = new VerbesserungsvorschlagDto();
        dto.titel = "Dunkelmodus";
        dto.beschreibung = "Bitte einen Dark Mode ergänzen.";

        given()
            .contentType(ContentType.JSON)
            .body(dto)
            .when().post()
            .then()
            .statusCode(CREATED.getStatusCode())
            .body("titel", org.hamcrest.Matchers.equalTo("Dunkelmodus"))
            .body("status", org.hamcrest.Matchers.equalTo("OFFEN"))
            .body("erstellerName", org.hamcrest.Matchers.equalTo("Teilnehmer, Tom"));

        List<Verbesserungsvorschlag> alle = Verbesserungsvorschlag.listAll();
        assertThat(alle).hasSize(1);
        assertThat(alle.get(0).getErsteller().getLoginName()).isEqualTo("tom.teilnehmer");
    }

    @Test
    @TestSecurity(user = "tom.teilnehmer", roles = "TEILNEHMER")
    @OidcSecurity(claims = {@Claim(key = "preferred_username", value = "tom.teilnehmer")})
    void getAll_istNurFuerAdminErlaubt() {
        given()
            .when().get()
            .then()
            .statusCode(FORBIDDEN.getStatusCode());
    }

    @Test
    @TestSecurity(user = "anna.admin", roles = "ORGANISATOR")
    @OidcSecurity(claims = {@Claim(key = "preferred_username", value = "anna.admin")})
    void getAll_zeigtEingereichteVorschlaegeMitErstellerkontext() {
        erstelleVorschlagAlsTeilnehmer("Feature X", "Beschreibung");

        given()
            .when().get()
            .then()
            .statusCode(OK.getStatusCode())
            .body("$", hasSize(1))
            .body("[0].erstellerName", org.hamcrest.Matchers.equalTo("Teilnehmer, Tom"))
            .body("[0].erstellerRolle", org.hamcrest.Matchers.equalTo("TEILNEHMER"));
    }

    @Test
    @TestSecurity(user = "anna.admin", roles = "ORGANISATOR")
    @OidcSecurity(claims = {@Claim(key = "preferred_username", value = "anna.admin")})
    void updateStatus_markiertAlsErledigt() {
        Long id = erstelleVorschlagAlsTeilnehmer("Feature Y", "Beschreibung").getId();

        given()
            .contentType(ContentType.JSON)
            .body("\"ERLEDIGT\"")
            .when().put("/" + id + "/status")
            .then()
            .statusCode(OK.getStatusCode())
            .body("status", org.hamcrest.Matchers.equalTo("ERLEDIGT"));

        assertThat(Verbesserungsvorschlag.<Verbesserungsvorschlag>findById(id).getStatus()).isEqualTo(VorschlagStatus.ERLEDIGT);
    }

    @Test
    @TestSecurity(user = "anna.admin", roles = "ORGANISATOR")
    @OidcSecurity(claims = {@Claim(key = "preferred_username", value = "anna.admin")})
    void delete_entferntDenVorschlagEndgueltig() {
        Long id = erstelleVorschlagAlsTeilnehmer("Feature Z", "Beschreibung").getId();

        given()
            .when().delete("/" + id)
            .then()
            .statusCode(NO_CONTENT.getStatusCode());

        assertThat(Verbesserungsvorschlag.listAll()).isEmpty();
    }

    @Transactional
    Verbesserungsvorschlag erstelleVorschlagAlsTeilnehmer(String titel, String beschreibung) {
        Verbesserungsvorschlag v = new Verbesserungsvorschlag();
        v.setTitel(titel);
        v.setBeschreibung(beschreibung);
        v.setErsteller(Nutzer.findByLoginName("tom.teilnehmer"));
        v.setErstelltAm(LocalDateTime.now());
        v.setStatus(VorschlagStatus.OFFEN);
        v.persist();
        return v;
    }
}
