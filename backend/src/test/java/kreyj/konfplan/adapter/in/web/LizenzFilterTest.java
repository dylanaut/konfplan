package kreyj.konfplan.adapter.in.web;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.oidc.Claim;
import io.quarkus.test.security.oidc.OidcSecurity;
import io.restassured.http.ContentType;
import kreyj.konfplan.domain.lizenz.LizenzTestSupport;
import kreyj.konfplan.persistence.Lizenzzustand;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;

/**
 * Ein einziger Testlauf mit festem Ablauf (ohne Lizenz gesperrt, nach Installation frei): der Status
 * wird im Service kurz gecached, die Installation verwirft den Cache - daher keine einzelnen Tests,
 * die sich gegenseitig den Cache-Zustand hinterlassen.
 */
@QuarkusTest
@TestProfile(LizenzDurchsetzungTestProfile.class)
class LizenzFilterTest {

    @Test
    @TestSecurity(user = "admin", roles = "ADMINISTRATOR")
    @OidcSecurity(claims = {@Claim(key = "preferred_username", value = "admin")})
    void schreibsperreGiltNurOhneGueltigeLizenz() {
        QuarkusTransaction.requiringNew().run(() -> Lizenzzustand.deleteAll());

        // Ohne Lizenz: gesperrte Endpunkte antworten mit 402 und strukturierter Meldung ...
        given().contentType(ContentType.JSON).body("{}")
            .when().post("/api/organisator/nutzer")
            .then().statusCode(402)
            .body("code", equalTo("LIZENZ_ERFORDERLICH"))
            .body("zustand", equalTo("UNLIZENZIERT"))
            .body("error", containsString("Lizenz"));
        given().contentType(ContentType.JSON).body("{}")
            .when().post("/api/planungen/1")
            .then().statusCode(402);
        given().contentType(ContentType.JSON).body("{}")
            .when().post("/api/veranstaltungen")
            .then().statusCode(402);

        // ... Lesen und die Lizenzseite selbst bleiben erreichbar.
        given().when().get("/api/wartungshinweis").then().statusCode(200);
        given().when().get("/api/lizenz").then().statusCode(200).body("durchsetzung", equalTo(true));

        // Gültige Lizenz installieren: die Sperre fällt.
        String jkt = given().when().get("/api/lizenz/anforderung").then().extract().path("thumbprint");
        String lizenz = LizenzTestSupport.token(LizenzTestSupport.PRIVATE_1, LizenzTestSupport.header(),
            LizenzTestSupport.claims(jkt, Instant.now().getEpochSecond()));
        given().contentType(ContentType.TEXT).body(lizenz).when().put("/api/lizenz").then().statusCode(200);

        int status = given().contentType(ContentType.JSON).body("{}")
            .when().post("/api/planungen/1").then().extract().statusCode();
        assertThat(status).isNotEqualTo(402);
    }

    @Test
    void ohneAnmeldungBleibtEsBei401UndGibtDenLizenzstatusNichtPreis() {
        given().contentType(ContentType.JSON).body("{}")
            .when().post("/api/organisator/nutzer")
            .then().statusCode(401);
    }
}
