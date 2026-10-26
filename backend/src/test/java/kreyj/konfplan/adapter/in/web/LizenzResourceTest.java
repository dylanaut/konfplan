package kreyj.konfplan.adapter.in.web;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.oidc.Claim;
import io.quarkus.test.security.oidc.OidcSecurity;
import io.restassured.http.ContentType;
import kreyj.konfplan.domain.lizenz.LizenzTestSupport;
import kreyj.konfplan.persistence.Lizenzzustand;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static io.restassured.RestAssured.given;
import static jakarta.ws.rs.core.Response.Status.BAD_REQUEST;
import static jakarta.ws.rs.core.Response.Status.FORBIDDEN;
import static jakarta.ws.rs.core.Response.Status.OK;
import static org.hamcrest.Matchers.hasLength;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

@QuarkusTest
class LizenzResourceTest {

    private static final String PATH = "/api/lizenz";

    @BeforeEach
    void cleanup() {
        QuarkusTransaction.requiringNew().run(() -> Lizenzzustand.deleteAll());
    }

    @Test
    @TestSecurity(user = "tom.teilnehmer", roles = "TEILNEHMER")
    @OidcSecurity(claims = {@Claim(key = "preferred_username", value = "tom.teilnehmer")})
    void alsTeilnehmer_istDieLizenzseiteGesperrt() {
        given().when().get(PATH).then().statusCode(FORBIDDEN.getStatusCode());
        given().when().get(PATH + "/anforderung").then().statusCode(FORBIDDEN.getStatusCode());
        given().contentType(ContentType.TEXT).body("x").when().put(PATH).then().statusCode(FORBIDDEN.getStatusCode());
    }

    @Test
    @TestSecurity(user = "orga", roles = "ORGANISATOR")
    @OidcSecurity(claims = {@Claim(key = "preferred_username", value = "orga")})
    void alsOrganisator_istDieLizenzseiteGesperrt() {
        given().when().get(PATH).then().statusCode(FORBIDDEN.getStatusCode());
    }

    @Test
    @TestSecurity(user = "admin", roles = "ADMINISTRATOR")
    @OidcSecurity(claims = {@Claim(key = "preferred_username", value = "admin")})
    void ohneLizenz_istDerStatusUnlizenziert() {
        given().when().get(PATH).then()
            .statusCode(OK.getStatusCode())
            .body("zustand", equalTo("UNLIZENZIERT"))
            .body("durchsetzung", equalTo(false))
            .body("grund", nullValue())
            .body("lizenzId", nullValue());
    }

    @Test
    @TestSecurity(user = "admin", roles = "ADMINISTRATOR")
    @OidcSecurity(claims = {@Claim(key = "preferred_username", value = "admin")})
    void anforderung_enthaeltInstanzIdUndPublicKey() {
        String id1 = given().when().get(PATH + "/anforderung").then()
            .statusCode(OK.getStatusCode())
            .body("instanzId", hasLength(36))
            .body("publicKey", containsString("MCow"))
            .body("thumbprint", hasLength(43))
            .extract().path("instanzId");

        // Die Identität bleibt über Aufrufe stabil.
        given().when().get(PATH + "/anforderung").then().body("instanzId", equalTo(id1));
    }

    @Test
    @TestSecurity(user = "admin", roles = "ADMINISTRATOR")
    @OidcSecurity(claims = {@Claim(key = "preferred_username", value = "admin")})
    void gueltigeLizenzWirdInstalliert() {
        String jkt = given().when().get(PATH + "/anforderung").then().extract().path("thumbprint");
        String lizenz = LizenzTestSupport.token(LizenzTestSupport.PRIVATE_1, LizenzTestSupport.header(),
            LizenzTestSupport.claims(jkt, Instant.now().getEpochSecond()));

        given().contentType(ContentType.TEXT).body(lizenz)
            .when().put(PATH)
            .then()
            .statusCode(OK.getStatusCode())
            .body("zustand", equalTo("LIZENZIERT"))
            .body("lizenzId", equalTo("LIC-1"))
            .body("maxInstanzen", equalTo(3))
            .body("typ", equalTo("OFFLINE"))
            .body("features", hasSize(1));

        given().when().get(PATH).then().body("zustand", equalTo("LIZENZIERT"));
    }

    @Test
    @TestSecurity(user = "admin", roles = "ADMINISTRATOR")
    @OidcSecurity(claims = {@Claim(key = "preferred_username", value = "admin")})
    void ungueltigeLizenzWirdMit400AbgelehntUndNichtInstalliert() {
        given().contentType(ContentType.TEXT).body("das.ist.keine-lizenz")
            .when().put(PATH)
            .then()
            .statusCode(BAD_REQUEST.getStatusCode())
            .body("error", containsString("Lizenz"));

        given().when().get(PATH).then().body("zustand", equalTo("UNLIZENZIERT"));
    }
}
