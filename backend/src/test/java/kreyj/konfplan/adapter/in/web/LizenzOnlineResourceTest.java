package kreyj.konfplan.adapter.in.web;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.oidc.Claim;
import io.quarkus.test.security.oidc.OidcSecurity;
import io.restassured.http.ContentType;
import kreyj.konfplan.persistence.Lizenzzustand;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

@QuarkusTest
@TestProfile(LizenzOnlineTestProfile.class)
class LizenzOnlineResourceTest {

    private static final String PATH = "/api/lizenz";

    @BeforeEach
    void cleanup() {
        QuarkusTransaction.requiringNew().run(() -> Lizenzzustand.deleteAll());
    }

    @Test
    @TestSecurity(user = "orga", roles = "ORGANISATOR")
    @OidcSecurity(claims = {@Claim(key = "preferred_username", value = "orga")})
    void onlineEndpunkteSindNurFuerAdministratoren() {
        given().contentType(ContentType.JSON).body("{\"lizenzschluessel\":\"x\"}").when().post(PATH + "/aktivierung").then().statusCode(403);
        given().when().post(PATH + "/revalidierung").then().statusCode(403);
        given().when().delete(PATH + "/aktivierung").then().statusCode(403);
    }

    @Test
    @TestSecurity(user = "admin", roles = "ADMINISTRATOR")
    @OidcSecurity(claims = {@Claim(key = "preferred_username", value = "admin")})
    void aktivierungRevalidierungGraceUndDeaktivierung() {
        given().when().get(PATH).then()
            .body("zustand", equalTo("UNLIZENZIERT"))
            .body("onlineMoeglich", equalTo(true));

        // Falscher Schlüssel: 400 mit Meldung des Servers, Zustand unverändert.
        given().contentType(ContentType.JSON).body("{\"lizenzschluessel\":\"FALSCH\"}")
            .when().post(PATH + "/aktivierung")
            .then().statusCode(400).body("error", containsString("Lizenzschlüssel"));
        given().when().get(PATH).then().body("zustand", equalTo("UNLIZENZIERT"));

        // Ohne Lizenz ist die Schreibsperre scharf.
        given().contentType(ContentType.JSON).body("{}").when().post("/api/planungen/1").then().statusCode(402);

        given().contentType(ContentType.JSON).body("{\"lizenzschluessel\":\"" + FakeLizenzServerResource.SCHLUESSEL + "\"}")
            .when().post(PATH + "/aktivierung")
            .then().statusCode(200)
            .body("zustand", equalTo("LIZENZIERT"))
            .body("typ", equalTo("ONLINE"))
            .body("lizenzId", equalTo("LIC-ONLINE"))
            .body("naechsteRevalidierung", notNullValue())
            .body("revalidierungFehler", nullValue());
        assertThat(given().contentType(ContentType.JSON).body("{}").when().post("/api/planungen/1").then().extract().statusCode())
            .isNotEqualTo(402);

        // Revalidierung mit sofort faelliger Frist: GRACE - die Schreibsperre greift dabei noch nicht.
        FakeLizenzServerResource.fake.revalidierungsintervall = Duration.ZERO;
        given().when().post(PATH + "/revalidierung")
            .then().statusCode(200).body("zustand", equalTo("GRACE"));
        assertThat(given().contentType(ContentType.JSON).body("{}").when().post("/api/planungen/1").then().extract().statusCode())
            .isNotEqualTo(402);

        // Abmelden gibt den Platz frei; danach ist wieder alles gesperrt.
        given().when().delete(PATH + "/aktivierung").then().statusCode(200).body("zustand", equalTo("UNLIZENZIERT"));
        given().contentType(ContentType.JSON).body("{}").when().post("/api/planungen/1").then().statusCode(402);

        // Nicht erreichbarer Server: 502 mit Code statt 400.
        FakeLizenzServerResource.fake.close();
        given().contentType(ContentType.JSON).body("{\"lizenzschluessel\":\"" + FakeLizenzServerResource.SCHLUESSEL + "\"}")
            .when().post(PATH + "/aktivierung")
            .then().statusCode(502).body("code", equalTo("NICHT_ERREICHBAR"));
    }
}
