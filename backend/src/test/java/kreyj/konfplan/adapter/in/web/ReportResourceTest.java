package kreyj.konfplan.adapter.in.web;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.panache.mock.PanacheMock;
import io.quarkus.test.InjectMock;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.common.http.TestHTTPEndpoint;
import io.quarkus.test.h2.H2DatabaseTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.ws.rs.core.MediaType;
import kreyj.konfplan.adapter.in.web.ReportResource;
import kreyj.konfplan.adapter.in.web.dto.templating.TeilnehmerReport;
import kreyj.konfplan.domain.service.DashboardService;
import kreyj.konfplan.domain.service.PlanService;
import kreyj.konfplan.persistence.Prioritaet;
import kreyj.konfplan.persistence.Referent;
import kreyj.konfplan.persistence.Teilnehmer;
import kreyj.konfplan.persistence.Veranstaltung;
import kreyj.konfplan.persistence.Wahlvortrag;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.LocalDateTime;
import java.util.Collections;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.mockito.ArgumentMatchers.any;

/**
 * Testet die JSON-{@code -data}-Endpunkte des {@link ReportResource}.
 * Die früheren HTML/PDF-Endpunkte wurden im Zuge der Template-&gt;VUE-Migration entfernt;
 * gerendert wird nun im Frontend, der Server liefert nur noch JSON-Daten.
 */
@QuarkusTest
@QuarkusTestResource(H2DatabaseTestResource.class)
@TestHTTPEndpoint(ReportResource.class)
class ReportResourceTest {

    @InjectMock
    PlanService planService;

    @InjectMock
    DashboardService dashboardService;

    private Veranstaltung mockVeranstaltung;
    private Teilnehmer mockTeilnehmer;
    private Referent mockReferent;


    @BeforeEach
    void setUp() {
        mockVeranstaltung = new Veranstaltung();
        mockVeranstaltung.setId(1L);
        mockVeranstaltung.setName("Test Konferenz");

        mockTeilnehmer = new Teilnehmer();
        mockTeilnehmer.setId(1L);
        mockTeilnehmer.assignLoginName("teilnehmer@test.com");
        mockTeilnehmer.setEmail("teilnehmer@test.com");

        mockReferent = new Referent();
        mockReferent.setId(1L);
        mockReferent.assignLoginName("referent@test.com");
        mockReferent.setEmail("referent@test.com");
    }


    // --- Teilnehmer-Laufzettel ---


    @Test
    @TestSecurity(user = "testAdmin", roles = "ORGANISATOR")
    void getLaufzettelTeilnehmerData_asAdmin_shouldSucceed() {
        PanacheMock.mock(Teilnehmer.class);
        PanacheMock.mock(Veranstaltung.class);
        Mockito.when(Teilnehmer.findById(1L)).thenReturn(mockTeilnehmer);
        Mockito.when(Veranstaltung.findById(1L)).thenReturn(mockVeranstaltung);
        Mockito.when(planService.getPlanFuerTeilnehmer(any(), any())).thenReturn(Collections.emptyList());

        given()
                .when().get("1/teilnehmer/1/laufzettel-data")
                .then()
                .statusCode(200)
                .contentType(MediaType.APPLICATION_JSON);
    }


    @Test
    @TestSecurity(user = "teilnehmer@test.com", roles = "TEILNEHMER")
    void getLaufzettelTeilnehmerData_asSelf_shouldSucceed() {
        PanacheMock.mock(Teilnehmer.class);
        PanacheMock.mock(Veranstaltung.class);
        Mockito.when(Teilnehmer.findById(1L)).thenReturn(mockTeilnehmer);
        Mockito.when(Veranstaltung.findById(1L)).thenReturn(mockVeranstaltung);
        Mockito.when(planService.getPlanFuerTeilnehmer(any(), any())).thenReturn(Collections.emptyList());

        given()
                .when().get("1/teilnehmer/1/laufzettel-data")
                .then()
                .statusCode(200)
                .contentType(MediaType.APPLICATION_JSON);
    }


    @Test
    @TestSecurity(user = "testReferent", roles = "REFERENT")
    void getLaufzettelTeilnehmerData_asWrongRole_shouldBeForbidden() {
        given()
                .when().get("1/teilnehmer/1/laufzettel-data")
                .then()
                .statusCode(403);
    }


    // --- Referenten-Laufzettel ---


    @Test
    @TestSecurity(user = "testAdmin", roles = "ORGANISATOR")
    void getLaufzettelReferentData_asAdmin_shouldSucceed() {
        PanacheMock.mock(Referent.class);
        PanacheMock.mock(Veranstaltung.class);
        Mockito.when(Referent.findById(1L)).thenReturn(mockReferent);
        Mockito.when(Veranstaltung.findById(1L)).thenReturn(mockVeranstaltung);
        Mockito.when(planService.getPlanFuerReferent(any(), any())).thenReturn(Collections.emptyList());

        given()
                .when().get("1/referent/1/laufzettel-data")
                .then()
                .statusCode(200)
                .contentType(MediaType.APPLICATION_JSON);
    }


    @Test
    @TestSecurity(user = "referent@test.com", roles = "REFERENT")
    void getLaufzettelReferentData_asCorrectReferent_shouldSucceed() {
        PanacheMock.mock(Referent.class);
        PanacheMock.mock(Veranstaltung.class);
        Mockito.when(Referent.findById(1L)).thenReturn(mockReferent);
        Mockito.when(Veranstaltung.findById(1L)).thenReturn(mockVeranstaltung);
        Mockito.when(planService.getPlanFuerReferent(any(), any())).thenReturn(Collections.emptyList());

        given()
                .when().get("1/referent/1/laufzettel-data")
                .then()
                .statusCode(200)
                .contentType(MediaType.APPLICATION_JSON);
    }


    @Test
    @TestSecurity(user = "wrong@referent.com", roles = "REFERENT")
    void getLaufzettelReferentData_asWrongReferent_shouldBeForbidden() {
        PanacheMock.mock(Referent.class);
        PanacheMock.mock(Veranstaltung.class);
        Mockito.when(Referent.findById(1L)).thenReturn(mockReferent);
        Mockito.when(Veranstaltung.findById(1L)).thenReturn(mockVeranstaltung);

        given()
                .when().get("1/referent/1/laufzettel-data")
                .then()
                .statusCode(403);
    }


    @Test
    @TestSecurity(user = "testAdmin", roles = "ORGANISATOR")
    void getAlleLaufzettelReferentenData_asAdmin_shouldSucceed() {
        PanacheMock.mock(Veranstaltung.class);
        Mockito.when(Veranstaltung.findById(1L)).thenReturn(mockVeranstaltung);

        given()
                .when().get("1/laufzettel-alle-referenten-data")
                .then()
                .statusCode(200)
                .contentType(MediaType.APPLICATION_JSON);
    }


    // --- Organisator-Reports ---


    @Test
    @TestSecurity(user = "testAdmin", roles = "ORGANISATOR")
    void getUebersichtRaeumeData_asAdmin_shouldSucceed() {
        PanacheMock.mock(Veranstaltung.class);
        Mockito.when(Veranstaltung.findById(1L)).thenReturn(mockVeranstaltung);
        Mockito.when(planService.getDetaillierterPlan(any())).thenReturn(Collections.emptyList());

        given()
                .when().get("1/raeume-data")
                .then()
                .statusCode(200)
                .contentType(MediaType.APPLICATION_JSON);
    }


    @Test
    @TestSecurity(user = "testUser", roles = "TEILNEHMER")
    void getUebersichtRaeumeData_asNonAdmin_shouldBeForbidden() {
        given()
                .when().get("1/raeume-data")
                .then()
                .statusCode(403);
    }


    @Test
    @TestSecurity(user = "testAdmin", roles = "ORGANISATOR")
    void getAlleRaumschilderData_asAdmin_shouldSucceed() {
        PanacheMock.mock(Veranstaltung.class);
        Mockito.when(Veranstaltung.findById(1L)).thenReturn(mockVeranstaltung);
        Mockito.when(planService.getRaumbelegungsplan(any())).thenReturn(Collections.emptyMap());

        given()
                .when().get("1/raumschilder-data")
                .then()
                .statusCode(200)
                .contentType(MediaType.APPLICATION_JSON);
    }


    @Test
    @TestSecurity(user = "testAdmin", roles = "ORGANISATOR")
    void getFreieSlotsReferentenData_asAdmin_shouldSucceed() {
        PanacheMock.mock(Veranstaltung.class);
        Mockito.when(Veranstaltung.findById(1L)).thenReturn(mockVeranstaltung);
        Mockito.when(planService.getFreieSlotsReferenten(any())).thenReturn(Collections.emptyMap());

        given()
                .when().get("1/freie-slots-referenten-data")
                .then()
                .statusCode(200)
                .contentType(MediaType.APPLICATION_JSON);
    }


    @Test
    @TestSecurity(user = "testAdmin", roles = "ORGANISATOR")
    void getFreieSlotsTeilnehmerData_asAdmin_shouldSucceed() {
        PanacheMock.mock(Veranstaltung.class);
        Mockito.when(Veranstaltung.findById(1L)).thenReturn(mockVeranstaltung);
        Mockito.when(planService.getFreieSlotsTeilnehmer(any())).thenReturn(Collections.emptyMap());

        given()
                .when().get("1/freie-slots-teilnehmer-data")
                .then()
                .statusCode(200)
                .contentType(MediaType.APPLICATION_JSON);
    }


    // --- Teilnehmer-Dashboard (Teilnehmer-Zuordnungen) ---


    @Test
    @TestSecurity(user = "testAdmin", roles = "ORGANISATOR")
    void getTeilnehmerDashboardData_asAdmin_shouldSucceed() {
        // Regression: dieser Endpoint loeste den Aufrufer frueher fälschlich per
        // TeilnehmerService.findByLoginName() auf und lieferte fuer Admins (die keine
        // Teilnehmer sind) 404, obwohl der Report alle Teilnehmer der Veranstaltung zeigt.
        PanacheMock.mock(Veranstaltung.class);
        Mockito.when(Veranstaltung.findById(1L)).thenReturn(mockVeranstaltung);
        Mockito.when(dashboardService.getTeilnehmerReport(any())).thenReturn(
            new TeilnehmerReport(null, null, Collections.emptyMap(), Collections.emptyList(), Collections.emptyList()));

        given()
                .when().get("1/teilnehmer-dashboard-data")
                .then()
                .statusCode(200)
                .contentType(MediaType.APPLICATION_JSON);
    }


    @Test
    @TestSecurity(user = "teilnehmer@test.com", roles = "TEILNEHMER")
    void getTeilnehmerDashboardData_asTeilnehmer_shouldSucceed() {
        PanacheMock.mock(Veranstaltung.class);
        Mockito.when(Veranstaltung.findById(1L)).thenReturn(mockVeranstaltung);
        Mockito.when(dashboardService.getTeilnehmerReport(any())).thenReturn(
            new TeilnehmerReport(null, null, Collections.emptyMap(), Collections.emptyList(), Collections.emptyList()));

        given()
                .when().get("1/teilnehmer-dashboard-data")
                .then()
                .statusCode(200)
                .contentType(MediaType.APPLICATION_JSON);
    }


    @Test
    @TestSecurity(user = "testReferent", roles = "REFERENT")
    void getTeilnehmerDashboardData_asWrongRole_shouldBeForbidden() {
        given()
                .when().get("1/teilnehmer-dashboard-data")
                .then()
                .statusCode(403);
    }


    // --- Vortrag-Anmeldungen (Prioritäten je Wahlvortrag) ---
    // Läuft bewusst gegen echte H2-Persistenz statt PanacheMock: die eigentliche HQL-Query
    // (vortrag.id = ?1 and prioWert > 0 order by ...) soll real ausgeführt werden.


    @Test
    @TestSecurity(user = "testAdmin", roles = "ORGANISATOR")
    void getVortragAnmeldungenData_asAdmin_liefertNurPositivePrioritaetenAbsteigendSortiert() {
        Long[] ids = new Long[2];
        QuarkusTransaction.requiringNew().run(() -> {
            Veranstaltung v = new Veranstaltung();
            v.setName("Anmeldungen-Test-Event");
            v.setBeginntAm(LocalDateTime.now());
            v.persist();

            Referent referent = new Referent();
            referent.assignLoginName("anmeldungen-referent");
            referent.setEmail("anmeldungen-referent@test.de");
            referent.persist();

            Wahlvortrag wv = new Wahlvortrag();
            wv.setTitel("Testvortrag");
            wv.setVeranstaltung(v);
            wv.setReferent(referent);
            wv.persist();

            Teilnehmer t1 = new Teilnehmer();
            t1.assignLoginName("teilnehmer.eins");
            t1.setEmail("teilnehmer.eins@test.de");
            t1.persist();

            Teilnehmer t2 = new Teilnehmer();
            t2.assignLoginName("teilnehmer.zwei");
            t2.setEmail("teilnehmer.zwei@test.de");
            t2.persist();

            Teilnehmer ohnePraeferenz = new Teilnehmer();
            ohnePraeferenz.assignLoginName("teilnehmer.drei");
            ohnePraeferenz.setEmail("teilnehmer.drei@test.de");
            ohnePraeferenz.persist();

            new Prioritaet(t1, wv, 5).persist();
            new Prioritaet(t2, wv, 8).persist();
            // prioWert 0 = "keine Präferenz" - darf nicht als Anmeldung erscheinen.
            new Prioritaet(ohnePraeferenz, wv, 0).persist();

            ids[0] = v.getId();
            ids[1] = wv.getId();
        });

        given()
                .when().get(ids[0] + "/vortrag/" + ids[1] + "/anmeldungen-data")
                .then()
                .statusCode(200)
                .contentType(MediaType.APPLICATION_JSON)
                .body("vortragTitel", is("Testvortrag"))
                .body("anmeldungen.size()", is(2))
                .body("anmeldungen[0].loginName", is("teilnehmer.zwei"))
                .body("anmeldungen[0].prioWert", is(8))
                .body("anmeldungen[1].loginName", is("teilnehmer.eins"))
                .body("anmeldungen[1].prioWert", is(5));
    }


    @Test
    @TestSecurity(user = "testReferent", roles = "REFERENT")
    void getVortragAnmeldungenData_asWrongRole_shouldBeForbidden() {
        given()
                .when().get("1/vortrag/1/anmeldungen-data")
                .then()
                .statusCode(403);
    }


    @Test
    @TestSecurity(user = "testAdmin", roles = "ORGANISATOR")
    void getVortragAnmeldungenData_unbekannterVortrag_shouldReturn404() {
        given()
                .when().get("1/vortrag/999999/anmeldungen-data")
                .then()
                .statusCode(404);
    }
}
