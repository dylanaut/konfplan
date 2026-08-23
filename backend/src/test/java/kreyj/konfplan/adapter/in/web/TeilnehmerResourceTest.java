package kreyj.konfplan.adapter.in.web;

import io.quarkus.mailer.MockMailbox;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.common.http.TestHTTPEndpoint;
import io.quarkus.test.h2.H2DatabaseTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.oidc.Claim;
import io.quarkus.test.security.oidc.OidcSecurity;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import kreyj.konfplan.adapter.in.web.dto.NutzerDto;
import kreyj.konfplan.persistence.Neigung;
import kreyj.konfplan.persistence.Nutzer;
import kreyj.konfplan.persistence.Teilnehmer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static io.restassured.RestAssured.given;
import static jakarta.ws.rs.core.Response.Status.BAD_REQUEST;
import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
@QuarkusTestResource(H2DatabaseTestResource.class)
@TestHTTPEndpoint(TeilnehmerResource.class)
class TeilnehmerResourceTest extends DatabaseCleaner {

    @Inject
    MockMailbox mailbox;

    Long teilnehmerId;


    @BeforeEach
    @Transactional
    void setup() {
        Teilnehmer t = new Teilnehmer();
        t.assignLoginName("tom.teilnehmer");
        t.setEmail("tom.alt@test.de");
        t.setFirstName("Tom");
        t.setLastName("Teilnehmer");
        t.persist();
        teilnehmerId = t.getId();
    }


    @AfterEach
    void afterEach() {
        mailbox.clear();
    }


    @Test
    @TestSecurity(user = "tom.teilnehmer", roles = "TEILNEHMER")
    @OidcSecurity(claims = {@Claim(key = "preferred_username", value = "tom.teilnehmer")})
    void updateProfile_stillRejectsDirectEmailChange() {
        NutzerDto dto = new NutzerDto();
        dto.email = "versuchter.direkter.wechsel@test.de";
        dto.firstName = "Tom";
        dto.lastName = "Teilnehmer";
        dto.gruppen = List.of();
        dto.version = 0L;

        given()
            .contentType(ContentType.JSON)
            .body(dto)
            .when().put("/profile")
            .then()
            .statusCode(BAD_REQUEST.getStatusCode());

        assertThat(Nutzer.<Nutzer>findById(teilnehmerId).getEmail()).isEqualTo("tom.alt@test.de");
    }


    @Test
    @TestSecurity(user = "tom.teilnehmer", roles = "TEILNEHMER")
    @OidcSecurity(claims = {@Claim(key = "preferred_username", value = "tom.teilnehmer")})
    void updateProfile_setsNeigungen() {
        NutzerDto dto = new NutzerDto();
        dto.email = "tom.alt@test.de";
        dto.firstName = "Tom";
        dto.lastName = "Teilnehmer";
        dto.gruppen = List.of();
        dto.neigungen = Set.of(Neigung.SOZIAL, Neigung.ORGANISATORISCH);
        dto.isActive = true;
        dto.version = 0L;

        given()
            .contentType(ContentType.JSON)
            .body(dto)
            .when().put("/profile")
            .then()
            .statusCode(200);

        assertThat(Teilnehmer.<Teilnehmer>findById(teilnehmerId).getNeigungen())
            .containsExactlyInAnyOrder(Neigung.SOZIAL, Neigung.ORGANISATORISCH);
    }
}
