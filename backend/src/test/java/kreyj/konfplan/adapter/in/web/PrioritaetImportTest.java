package kreyj.konfplan.adapter.in.web;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.common.http.TestHTTPEndpoint;
import io.quarkus.test.h2.H2DatabaseTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.transaction.Transactional;
import kreyj.konfplan.domain.service.AdminService;
import kreyj.konfplan.persistence.Admin;
import kreyj.konfplan.persistence.Prioritaet;
import kreyj.konfplan.persistence.Referent;
import kreyj.konfplan.persistence.Teilnehmer;
import kreyj.konfplan.persistence.Veranstaltung;
import kreyj.konfplan.persistence.Wahlvortrag;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@QuarkusTest
@TestSecurity(user = "admin@test.de", roles = "ADMIN")
@QuarkusTestResource(H2DatabaseTestResource.class)
@TestHTTPEndpoint(AdminResource.class)
class PrioritaetImportTest extends DatabaseCleaner {

    Long testVid;
    Long wv1Id;
    Long wv2Id;
    Long teilnehmer1Id;


    @BeforeEach
    @Transactional
    void setup() {
        Admin admin = new Admin();
        admin.assignLoginName("admin@test.de");
        admin.setEmail("admin@test.de");
        admin.persist();

        Veranstaltung v = new Veranstaltung();
        v.setName("Test Event " + System.currentTimeMillis());
        v.setBeginntAm(LocalDateTime.of(2025, 10, 10, 9, 0));
        v.setEndetAm(LocalDateTime.of(2025, 10, 10, 17, 0));
        v.persist();
        testVid = v.getId();

        admin.addVeranstaltung(v);
        admin.persist();

        Teilnehmer t1 = new Teilnehmer();
        t1.assignLoginName("teilnehmer1@test.de");
        t1.setEmail("teilnehmer1@test.de");
        t1.persist();
        t1.addVeranstaltung(v);
        teilnehmer1Id = t1.getId();

        Referent r1 = new Referent();
        r1.assignLoginName("referent1@test.de");
        r1.setEmail("referent1@test.de");
        r1.persist();
        r1.addVeranstaltung(v);

        Wahlvortrag wv1 = Wahlvortrag.create("Wahlvortrag 1", "Inhalt", r1, true, 1, v);
        wv1.persist();
        wv1Id = wv1.getId();

        Wahlvortrag wv2 = Wahlvortrag.create("Wahlvortrag 2", "Inhalt", r1, true, 2, v);
        wv2.persist();
        wv2Id = wv2.getId();
    }


    @Test
    void testImportPrioritaeten() {
        String csv = String.format("# Legende: %d=%s # %d=%s\n", wv1Id, "Wahlvortrag 1", wv2Id, "Wahlvortrag 2") +
                AdminService.CSV_PRIO_HEADER + "\n" +
                String.format("teilnehmer1@test.de;%d :5,%d: 3 \n", wv1Id, wv2Id);

        given()
                .multiPart("file", "prioritaeten.csv", csv.getBytes())
                .when()
                .post("/veranstaltungen/{vid}/prioritaeten/import", testVid)
                .then()
                .statusCode(200)
                .body(is("Import erfolgreich: 2 Prioritäten importiert/aktualisiert."));

        assertEquals(2, Prioritaet.count());

        Prioritaet p1 = Prioritaet.find("teilnehmer.id = ?1 and vortrag.id = ?2", teilnehmer1Id, wv1Id).firstResult();
        assertNotNull(p1);
        assertEquals(5, p1.getPrioWert());

        Prioritaet p2 = Prioritaet.find("teilnehmer.id = ?1 and vortrag.id = ?2", teilnehmer1Id, wv2Id).firstResult();
        assertNotNull(p2);
        assertEquals(3, p2.getPrioWert());
    }
}
