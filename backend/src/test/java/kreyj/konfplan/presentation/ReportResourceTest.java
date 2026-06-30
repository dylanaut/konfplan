package kreyj.konfplan.presentation;

import io.quarkus.panache.mock.PanacheMock;
import io.quarkus.test.InjectMock;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.common.http.TestHTTPEndpoint;
import io.quarkus.test.h2.H2DatabaseTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.ws.rs.core.MediaType;
import kreyj.konfplan.adapter.in.web.ReportResource;
import kreyj.konfplan.domain.service.PlanService;
import kreyj.konfplan.persistence.Referent;
import kreyj.konfplan.persistence.Teilnehmer;
import kreyj.konfplan.persistence.Veranstaltung;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Collections;

import static io.restassured.RestAssured.given;
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
        mockTeilnehmer.setEmail("teilnehmer@test.com");

        mockReferent = new Referent();
        mockReferent.setId(1L);
        mockReferent.setEmail("referent@test.com");
    }


    // --- Teilnehmer-Laufzettel ---


    @Test
    @TestSecurity(user = "testAdmin", roles = "ADMIN")
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
    @TestSecurity(user = "testAdmin", roles = "ADMIN")
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


    // --- Admin-Reports ---


    @Test
    @TestSecurity(user = "testAdmin", roles = "ADMIN")
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
    @TestSecurity(user = "testAdmin", roles = "ADMIN")
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
    @TestSecurity(user = "testAdmin", roles = "ADMIN")
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
    @TestSecurity(user = "testAdmin", roles = "ADMIN")
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
}
