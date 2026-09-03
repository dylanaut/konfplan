package kreyj.konfplan.adapter.in.web;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

@QuarkusTest
class AppInfoResourceTest extends DatabaseCleaner {

    @Test
    @TestSecurity(user = "irgendein.nutzer", roles = "ORGANISATOR")
    void getInfo_liefertAppNameUndVersion() {
        given()
            .when().get("/api/info")
            .then().statusCode(200)
            .body("name", equalTo("KonfPlan"))
            .body("version", notNullValue())
            .body("buildTime", notNullValue())
            .body("gitCommit", notNullValue());
    }

    @Test
    void getInfo_ohneAuthentifizierung_liefert401() {
        given()
            .when().get("/api/info")
            .then().statusCode(401);
    }
}
