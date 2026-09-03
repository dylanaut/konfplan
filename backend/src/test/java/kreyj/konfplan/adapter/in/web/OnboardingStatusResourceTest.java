package kreyj.konfplan.adapter.in.web;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.h2.H2DatabaseTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.transaction.Transactional;
import kreyj.konfplan.persistence.Organisator;
import kreyj.konfplan.persistence.Teilnehmer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.Matchers.empty;

@QuarkusTest
@QuarkusTestResource(H2DatabaseTestResource.class)
class OnboardingStatusResourceTest extends DatabaseCleaner {

    @BeforeEach
    @Transactional
    void setup() {
        Teilnehmer t = new Teilnehmer();
        t.assignLoginName("gruppen.teilnehmer");
        t.setEmail("gruppen.teilnehmer@test.de");
        t.setGruppen(List.of("Zebra", "Anton"));
        t.persist();

        Organisator a = new Organisator();
        a.assignLoginName("ohne.gruppen.admin");
        a.setEmail("ohne.gruppen.admin@test.de");
        a.persist();
    }


    @Test
    @TestSecurity(user = "admin", roles = "ORGANISATOR")
    void getOnboardingStatus_teilnehmerHatSortierteGruppen_adminHatKeine() {
        given()
            .when().get("/api/organisator/onboarding-status")
            .then().statusCode(200)
            .body("find { it.loginName == 'gruppen.teilnehmer' }.gruppen", equalTo(List.of("Anton", "Zebra")))
            .body("find { it.loginName == 'ohne.gruppen.admin' }.gruppen", empty())
            .body("loginName", hasItems("gruppen.teilnehmer", "ohne.gruppen.admin"));
    }


    @Test
    @TestSecurity(user = "teilnehmer", roles = "TEILNEHMER")
    void getOnboardingStatus_alsNichtAdmin_verboten() {
        given()
            .when().get("/api/organisator/onboarding-status")
            .then().statusCode(403);
    }
}
