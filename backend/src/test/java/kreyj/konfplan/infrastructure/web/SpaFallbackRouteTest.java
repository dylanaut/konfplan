package kreyj.konfplan.infrastructure.web;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;

@QuarkusTest
class SpaFallbackRouteTest {

    @Test
    void hardNavigationZuVueRoute_liefertIndexHtml() {
        given()
            .when().get("/login")
            .then().statusCode(200)
            .contentType(containsString("text/html"))
            .body(containsString("<div id=\"app\">"));
    }

    @Test
    void tiefVerschachtelteVueRoute_liefertIndexHtml() {
        given()
            .when().get("/organisator/veranstaltung/1/vortrag/1/anmeldungen")
            .then().statusCode(200)
            .contentType(containsString("text/html"))
            .body(containsString("<div id=\"app\">"));
    }

    @Test
    void unbekannterApiPfad_bleibtEcht404() {
        given()
            .when().get("/api/does-not-exist")
            .then().statusCode(404);
    }

    @Test
    void unbekanntesAsset_bleibtEcht404_stattIndexHtml() {
        given()
            .when().get("/assets/does-not-exist.js")
            .then().statusCode(404);
    }
}
