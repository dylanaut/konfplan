package kreyj.konfplan.adapter.in.web;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.h2.H2DatabaseTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.transaction.Transactional;
import kreyj.konfplan.persistence.Gruppenkategorie;
import kreyj.konfplan.persistence.GruppenkategorieWert;
import kreyj.konfplan.persistence.Organisator;
import kreyj.konfplan.persistence.Teilnehmer;
import kreyj.konfplan.persistence.Veranstaltung;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.Matchers.anEmptyMap;
import static org.hamcrest.Matchers.containsInAnyOrder;

@QuarkusTest
@QuarkusTestResource(H2DatabaseTestResource.class)
class OnboardingStatusResourceTest extends DatabaseCleaner {

    @BeforeEach
    @Transactional
    void setup() {
        Veranstaltung veranstaltung = new Veranstaltung();
        veranstaltung.setName("Onboarding-Status-Test");
        veranstaltung.setBeginntAm(LocalDateTime.now());
        veranstaltung.persist();

        Gruppenkategorie klasse = new Gruppenkategorie(veranstaltung, "Klasse", true, true);
        klasse.persist();
        GruppenkategorieWert anton = new GruppenkategorieWert(klasse, "Anton");
        anton.persist();
        GruppenkategorieWert zebra = new GruppenkategorieWert(klasse, "Zebra");
        zebra.persist();

        Teilnehmer t = new Teilnehmer();
        t.assignLoginName("gruppen.teilnehmer");
        t.setEmail("gruppen.teilnehmer@test.de");
        t.persist();
        t.addVeranstaltung(veranstaltung);
        t.addGruppenwert(zebra);
        t.addGruppenwert(anton);

        Organisator a = new Organisator();
        a.assignLoginName("ohne.gruppen.admin");
        a.setEmail("ohne.gruppen.admin@test.de");
        a.persist();
    }


    @Test
    @TestSecurity(user = "admin", roles = "ORGANISATOR")
    void getOnboardingStatus_teilnehmerHatGruppenwerteByKategorie_adminHatKeine() {
        given()
            .when().get("/api/organisator/onboarding-status")
            .then().statusCode(200)
            .body("find { it.loginName == 'gruppen.teilnehmer' }.gruppenwerteByKategorie.Klasse",
                containsInAnyOrder("Anton", "Zebra"))
            .body("find { it.loginName == 'ohne.gruppen.admin' }.gruppenwerteByKategorie", anEmptyMap())
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
