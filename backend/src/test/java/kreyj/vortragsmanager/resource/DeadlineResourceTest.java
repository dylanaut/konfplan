package kreyj.vortragsmanager.resource;

import io.quarkus.elytron.security.common.BcryptUtil;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.h2.H2DatabaseTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.jwt.Claim;
import io.quarkus.test.security.jwt.JwtSecurity;
import io.restassured.http.ContentType;
import jakarta.transaction.Transactional;
import kreyj.vortragsmanager.dto.PrioritaetRequest;
import kreyj.vortragsmanager.dto.VortragDto;
import kreyj.vortragsmanager.dto.VeranstaltungDto;
import kreyj.vortragsmanager.entity.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;

@QuarkusTest
@QuarkusTestResource(H2DatabaseTestResource.class)
class DeadlineResourceTest {

    Long pastEventId;
    Long refId;
    Long tnId;
    Long wahlvortragId;

    @BeforeEach
    @Transactional
    void setup() {
        // Aufräumen
        Zuweisung.deleteAll();
        RaumBelegbarkeit.deleteAll();
        Verfuegbarkeit.deleteAll();
        Prioritaet.deleteAll();
        Vortrag.deleteAll();
        Nutzer.deleteAll();
        Veranstaltung.deleteAll();

        // Veranstaltung mit abgelaufenen Deadlines
        Veranstaltung v = new Veranstaltung();
        v.name = "Abgelaufenes Event";
        v.beginntAm = LocalDateTime.now().plusDays(10);
        v.deadlineReferenten = LocalDateTime.now().minusDays(1);
        v.deadlineTeilnehmer = LocalDateTime.now().minusDays(1);
        v.persist();
        pastEventId = v.id;

        // Referent
        Referent r = new Referent();
        r.email = "referent@test.de";
        r.passwordHash = BcryptUtil.bcryptHash("test");
        r.persist();
        refId = r.id;

        // Teilnehmer
        Teilnehmer t = new Teilnehmer();
        t.email = "teilnehmer@test.de";
        t.passwordHash = BcryptUtil.bcryptHash("test");
        t.addVeranstaltung(v);
        t.persist();
        tnId = t.id;

        // Vortrag für das Event (damit man was ändern/löschen könnte)
        Wahlvortrag wv = new Wahlvortrag();
        wv.titel = "Testvortrag";
        wv.veranstaltung = v;
        wv.referent = r;
        wv.persist();
        wahlvortragId = wv.id;
    }

    @Test
    @TestSecurity(user = "referent@test.de", roles = "REFERENT")
    @JwtSecurity(claims = {
            @Claim(key = "sub", value = "referent@test.de")
    })
    void testReferentDeadlineExceeded() {
        VortragDto dto = new VortragDto();
        dto.titel = "Neuer Vortrag";
        dto.veranstaltungId = pastEventId;

        // Erstellen verboten
        given()
                .contentType(ContentType.JSON)
                .body(dto)
                .when().post("/api/referenten/vortraege")
                .then()
                .statusCode(403);

        // Update verboten
        dto.titel = "Geänderter Titel";
        given()
                .contentType(ContentType.JSON)
                .body(dto)
                .when().put("/api/referenten/vortraege/{id}", wahlvortragId)
                .then()
                .statusCode(403);

        // Löschen verboten
        given()
                .when().delete("/api/referenten/vortraege/{id}", wahlvortragId)
                .then()
                .statusCode(403);
    }

    @Test
    @TestSecurity(user = "teilnehmer@test.de", roles = "TEILNEHMER")
    @JwtSecurity(claims = {
            @Claim(key = "sub", value = "teilnehmer@test.de")
    })
    void testTeilnehmerDeadlineExceeded() {
        PrioritaetRequest req = new PrioritaetRequest();
        req.vortragId = wahlvortragId;
        req.prioWert = 1;

        given()
                .contentType(ContentType.JSON)
                .body(List.of(req))
                .when().post("/api/teilnehmer/prios")
                .then()
                .statusCode(403);
    }

    @Test
    @TestSecurity(user = "admin@test.de", roles = "ADMIN")
    void testAdminCanStillEditEvenIfDeadlineExceeded() {
        VeranstaltungDto dto = new VeranstaltungDto();
        dto.id = pastEventId;
        dto.name = "Abgelaufenes Event (Admin Update)";
        dto.beginntAm = LocalDateTime.now().plusDays(10);
        dto.deadlineReferenten = LocalDateTime.now().minusDays(1);
        dto.deadlineTeilnehmer = LocalDateTime.now().minusDays(1);

        given()
                .contentType(ContentType.JSON)
                .body(dto)
                .when().put("/api/veranstaltungen/{id}", pastEventId)
                .then()
                .statusCode(200)
                .body("name", is("Abgelaufenes Event (Admin Update)"));
    }
}
