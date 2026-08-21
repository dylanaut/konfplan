package kreyj.konfplan.adapter.in.web;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.contains;

@QuarkusTest
class LaufbandResourceTest {

    // Muss zu %test.konfplan.laufband.path in application.properties passen.
    private static final Path LAUFBAND_PATH = Path.of("target/laufband-test/laufband.txt");


    @AfterEach
    void aufraeumen() throws IOException {
        Files.deleteIfExists(LAUFBAND_PATH);
    }


    @Test
    void getNews_ohneDatei_liefertLeereListe() {
        given()
            .when().get("/api/laufband")
            .then().statusCode(200)
            .body("news", empty());
    }


    @Test
    void getNews_mitLeererErsterZeile_liefertLeereListe() throws IOException {
        schreibeLaufbandDatei("\nSpaeterer Text");

        given()
            .when().get("/api/laufband")
            .then().statusCode(200)
            .body("news", empty());
    }


    @Test
    void getNews_mitInhalt_liefertNichtLeereZeilenOhneAuthentifizierung() throws IOException {
        schreibeLaufbandDatei("Erste News\n\n  Zweite News  \n");

        given()
            .when().get("/api/laufband")
            .then().statusCode(200)
            .body("news", contains("Erste News", "Zweite News"));
    }


    private void schreibeLaufbandDatei(String inhalt) throws IOException {
        Files.createDirectories(LAUFBAND_PATH.getParent());
        Files.writeString(LAUFBAND_PATH, inhalt, StandardCharsets.UTF_8);
    }
}
