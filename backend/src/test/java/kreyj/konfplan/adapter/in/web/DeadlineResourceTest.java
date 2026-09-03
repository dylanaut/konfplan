package kreyj.konfplan.adapter.in.web;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.common.http.TestHTTPEndpoint;
import io.quarkus.test.h2.H2DatabaseTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.oidc.Claim;
import io.quarkus.test.security.oidc.OidcSecurity;
import io.restassured.http.ContentType;
import jakarta.transaction.Transactional;
import kreyj.konfplan.persistence.Organisator;
import kreyj.konfplan.persistence.Referent;
import kreyj.konfplan.persistence.Teilnehmer;
import kreyj.konfplan.persistence.Veranstaltung;
import kreyj.konfplan.persistence.Wahlvortrag;
import kreyj.konfplan.adapter.in.web.dto.VortragPrioDto;
import kreyj.konfplan.adapter.in.web.dto.VeranstaltungDto;
import kreyj.konfplan.adapter.in.web.dto.VortragDto;
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

        // Organisator (jede Veranstaltung braucht mindestens einen)
        Organisator organisator = new Organisator();
        organisator.assignLoginName("organisator@test.de");
        organisator.setEmail("organisator@test.de");
        organisator.persist();
        organisator.addVeranstaltung(v);

        // Referent
        Referent r = new Referent();
        r.assignLoginName("referent@test.de");
        r.setEmail("referent@test.de");
        r.persist();
        refId = r.getId();

        // Teilnehmer
        Teilnehmer t = new Teilnehmer();
        t.assignLoginName("teilnehmer@test.de");
        t.setEmail("teilnehmer@test.de");
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
    @OidcSecurity(claims = {
            @Claim(key = "preferred_username", value = "referent@test.de")
    })
    @TestHTTPEndpoint(ReferentResource.class)
    void testReferentDeadlineExceeded() {
        VortragDto dto = new VortragDto();
        dto.titel = "Neuer Vortrag";
        dto.veranstaltungId = pastEventId;

        // Erstellen verboten
        given()
                .contentType(ContentType.JSON)
                .body(dto)
                .when().post("/vortraege")
                .then()
                .statusCode(FORBIDDEN.getStatusCode());

        // Update verboten
        dto.titel = "Geänderter Titel";
        dto.version = 0L;
        given()
                .contentType(ContentType.JSON)
                .body(dto)
                .when().put("/vortraege/{id}", wahlvortragId)
                .then()
                .statusCode(FORBIDDEN.getStatusCode());

        // Löschen verboten
        given()
                .when().delete("/vortraege/{id}", wahlvortragId)
                .then()
                .statusCode(FORBIDDEN.getStatusCode());
    }


    @Test
    @TestSecurity(user = "teilnehmer@test.de", roles = "TEILNEHMER")
    @OidcSecurity(claims = {
            @Claim(key = "preferred_username", value = "teilnehmer@test.de")
    })
    @TestHTTPEndpoint(PrioritaetenResource.class)
    void testTeilnehmerDeadlineExceeded() {
        VortragPrioDto req = new VortragPrioDto(wahlvortragId, 1);

        given()
                .contentType(ContentType.JSON)
                .body(List.of(req))
                .when().post()
                .then()
                .statusCode(FORBIDDEN.getStatusCode());
    }


    @Test
    @TestSecurity(user = "admin@test.de", roles = "ORGANISATOR")
    @TestHTTPEndpoint(VeranstaltungResource.class)
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
                .when().put("/{id}", pastEventId)
                .then()
                .statusCode(OK.getStatusCode())
                .body("name", is("Abgelaufenes Event (Admin Update)"));
    }
}
