package kreyj.konfplan.presentation;

import io.quarkus.elytron.security.common.BcryptUtil;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.h2.H2DatabaseTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.jwt.Claim;
import io.quarkus.test.security.jwt.JwtSecurity;
import io.restassured.http.ContentType;
import jakarta.transaction.Transactional;
import kreyj.konfplan.persistence.Referent;
import kreyj.konfplan.persistence.Teilnehmer;
import kreyj.konfplan.persistence.Veranstaltung;
import kreyj.konfplan.persistence.Wahlvortrag;
import kreyj.konfplan.presentation.dto.PrioritaetRequest;
import kreyj.konfplan.presentation.dto.VeranstaltungDto;
import kreyj.konfplan.presentation.dto.VortragDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static io.restassured.RestAssured.given;
import static jakarta.ws.rs.core.Response.Status.FORBIDDEN;
import static jakarta.ws.rs.core.Response.Status.OK;
import static org.hamcrest.CoreMatchers.is;

@QuarkusTest
@QuarkusTestResource(H2DatabaseTestResource.class)
class DeadlineResourceTest extends DatabaseCleaner {

    Long pastEventId;
    Long refId;
    Long tnId;
    Long wahlvortragId;


    @BeforeEach
    @Transactional
    void setup() {
        // Veranstaltung mit abgelaufenen Deadlines
        Veranstaltung v = new Veranstaltung();
        v.setName("Abgelaufenes Event");
        v.setBeginntAm(LocalDateTime.now().plusDays(10));
        v.setDeadlineReferenten(LocalDateTime.now().minusDays(1));
        v.setDeadlineTeilnehmer(LocalDateTime.now().minusDays(1));
        v.persist();
        pastEventId = v.getId();

        // Referent
        Referent r = new Referent();
        r.setEmail("referent@test.de");
        r.setPasswordHash(BcryptUtil.bcryptHash("test"));
        r.persist();
        refId = r.getId();

        // Teilnehmer
        Teilnehmer t = new Teilnehmer();
        t.setEmail("teilnehmer@test.de");
        t.setPasswordHash(BcryptUtil.bcryptHash("test"));
        t.persist();
        t.addVeranstaltung(v);
        tnId = t.getId();

        // Vortrag für das Event (damit man was ändern/löschen könnte)
        Wahlvortrag wv = new Wahlvortrag();
        wv.setTitel("Testvortrag");
        wv.setVeranstaltung(v);
        wv.setReferent(r);
        wv.persist();
        wahlvortragId = wv.getId();
    }


    @Test
    @TestSecurity(user = "referent@test.de", roles = "REFERENT")
    @JwtSecurity(claims = {
            @Claim(key = "upn", value = "referent@test.de")
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
                .statusCode(FORBIDDEN.getStatusCode());

        // Update verboten
        dto.titel = "Geänderter Titel";
        dto.version = 0L;
        given()
                .contentType(ContentType.JSON)
                .body(dto)
                .when().put("/api/referenten/vortraege/{id}", wahlvortragId)
                .then()
                .statusCode(FORBIDDEN.getStatusCode());

        // Löschen verboten
        given()
                .when().delete("/api/referenten/vortraege/{id}", wahlvortragId)
                .then()
                .statusCode(FORBIDDEN.getStatusCode());
    }


    @Test
    @TestSecurity(user = "teilnehmer@test.de", roles = "TEILNEHMER")
    @JwtSecurity(claims = {
            @Claim(key = "upn", value = "teilnehmer@test.de")
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
                .statusCode(FORBIDDEN.getStatusCode());
    }


    @Test
    @TestSecurity(user = "admin@test.de", roles = "ADMIN")
    void testAdminCanStillEditEvenIfDeadlineExceeded() {
        VeranstaltungDto dto = new VeranstaltungDto();
        dto.id = pastEventId;
        dto.setName("Abgelaufenes Event (Admin Update)");
        dto.setBeginntAm(LocalDateTime.now().plusDays(10));
        dto.setDeadlineReferenten(LocalDateTime.now().minusDays(1));
        dto.setDeadlineTeilnehmer(LocalDateTime.now().minusDays(1));

        given()
                .contentType(ContentType.JSON)
                .body(dto)
                .when().put("/api/veranstaltungen/{id}", pastEventId)
                .then()
                .statusCode(OK.getStatusCode())
                .body("name", is("Abgelaufenes Event (Admin Update)"));
    }
}
