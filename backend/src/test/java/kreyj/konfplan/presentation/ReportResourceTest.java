package kreyj.konfplan.presentation;

import io.quarkus.hibernate.orm.panache.PanacheQuery;
import io.quarkus.panache.mock.PanacheMock;
import io.quarkus.test.InjectMock;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.common.http.TestHTTPEndpoint;
import io.quarkus.test.h2.H2DatabaseTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.ws.rs.core.MediaType;
import kreyj.konfplan.application.service.PdfService;
import kreyj.konfplan.application.service.PlanService;
import kreyj.konfplan.persistence.Referent;
import kreyj.konfplan.persistence.Teilnehmer;
import kreyj.konfplan.persistence.Veranstaltung;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Collections;

import static io.restassured.RestAssured.given;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;

@QuarkusTest
@QuarkusTestResource(H2DatabaseTestResource.class)
@TestHTTPEndpoint(ReportResource.class)
class ReportResourceTest {

    @InjectMock
    PlanService planService;

    @InjectMock
    PdfService pdfService;

    private Veranstaltung mockVeranstaltung;
    private Teilnehmer mockTeilnehmer;
    private Referent mockReferent;

    @BeforeEach
    void setUp() {
        // Mock-Objekte für Entitäten erstellen
        mockVeranstaltung = new Veranstaltung();
        mockVeranstaltung.setId(1L);
        mockVeranstaltung.setName("Test Konferenz");

        mockTeilnehmer = new Teilnehmer();
        mockTeilnehmer.setId(1L);
        mockTeilnehmer.setEmail("teilnehmer@test.com");

        mockReferent = new Referent();
        mockReferent.setId(1L);
        mockReferent.setEmail("referent@test.com");

        // Mocking für PdfService, gibt ein Dummy-PDF zurück
        Mockito.when(pdfService.generatePdf(any())).thenReturn(new byte[]{1, 2, 3});
//        Mockito.when(planService.generiereAlleRaumschilderPdf(any())).thenReturn(new byte[]{1, 2, 3});
    }

    // --- Testfälle für Teilnehmer-Laufzettel ---

    @Test
    @TestSecurity(user = "testAdmin", roles = "ADMIN")
    void getLaufzettelTeilnehmer_asAdmin_shouldSucceed() {
        PanacheMock.mock(Teilnehmer.class);
        PanacheMock.mock(Veranstaltung.class);
        Mockito.when(Teilnehmer.findById(1L)).thenReturn(mockTeilnehmer);
        Mockito.when(Veranstaltung.findById(1L)).thenReturn(mockVeranstaltung);

        given()
                .when().get("1/teilnehmer/1/laufzettel")
                .then()
                .statusCode(200)
                .contentType(MediaType.TEXT_HTML);
    }

    @Test
    @TestSecurity(user = "teilnehmer@test.com", roles = "TEILNEHMER")
    void getLaufzettelTeilnehmerPdf_asTeilnehmer_shouldSucceed() {
        PanacheMock.mock(Teilnehmer.class);
        PanacheMock.mock(Veranstaltung.class);
        Mockito.when(Teilnehmer.findById(1L)).thenReturn(mockTeilnehmer);
        Mockito.when(Veranstaltung.findById(1L)).thenReturn(mockVeranstaltung);

        given()
                .when().get("1/teilnehmer/1/laufzettel-pdf")
                .then()
                .statusCode(200)
                .contentType("application/pdf");
    }

    @Test
    @TestSecurity(user = "testReferent", roles = "REFERENT")
    void getLaufzettelTeilnehmer_asWrongRole_shouldBeForbidden() {
        given()
                .when().get("1/teilnehmer/1/laufzettel")
                .then()
                .statusCode(403);
    }

    // --- Testfälle für Referenten-Laufzettel ---

    @Test
    @TestSecurity(user = "testAdmin", roles = "ADMIN")
    void getLaufzettelReferent_asAdmin_shouldSucceed() {
        PanacheMock.mock(Referent.class);
        PanacheMock.mock(Veranstaltung.class);
        Mockito.when(Referent.findById(1L)).thenReturn(mockReferent);
        Mockito.when(Veranstaltung.findById(1L)).thenReturn(mockVeranstaltung);

        given()
                .when().get("1/referent/1/laufzettel")
                .then()
                .statusCode(200)
                .contentType(MediaType.TEXT_HTML);
    }

    @Test
    @TestSecurity(user = "referent@test.com", roles = "REFERENT")
    void getLaufzettelReferent_asCorrectReferent_shouldSucceed() {
        PanacheMock.mock(Referent.class);
        PanacheMock.mock(Veranstaltung.class);
        Mockito.when(Referent.findById(1L)).thenReturn(mockReferent);
        Mockito.when(Veranstaltung.findById(1L)).thenReturn(mockVeranstaltung);

        given()
                .when().get("1/referent/1/laufzettel")
                .then()
                .statusCode(200);
    }

    @Test
    @TestSecurity(user = "wrong@referent.com", roles = "REFERENT")
    void getLaufzettelReferent_asWrongReferent_shouldBeForbidden() {
        PanacheMock.mock(Referent.class);
        PanacheMock.mock(Veranstaltung.class);
        Mockito.when(Referent.findById(1L)).thenReturn(mockReferent);
        Mockito.when(Veranstaltung.findById(1L)).thenReturn(mockVeranstaltung);

        given()
                .when().get("1/referent/1/laufzettel")
                .then()
                .statusCode(403);
    }

    // --- Testfälle für Admin-Reports ---

    @Test
    @TestSecurity(user = "testAdmin", roles = "ADMIN")
    void getUebersichtRaeume_asAdmin_shouldSucceed() {
        PanacheMock.mock(Veranstaltung.class);
        Mockito.when(Veranstaltung.findById(1L)).thenReturn(mockVeranstaltung);
        Mockito.when(planService.getDetaillierterPlan(any())).thenReturn(Collections.emptyList());

        given()
                .when().get("1/raeume")
                .then()
                .statusCode(200)
                .contentType(MediaType.TEXT_HTML);
    }

    @Test
    @TestSecurity(user = "testUser", roles = "TEILNEHMER")
    void getUebersichtRaeume_asNonAdmin_shouldBeForbidden() {
        given()
                .when().get("1/raeume")
                .then()
                .statusCode(403);
    }

    @Test
    @TestSecurity(user = "testAdmin", roles = "ADMIN")
    void getUebersichtRaeumePdf_asAdmin_shouldSucceed() {
        PanacheMock.mock(Veranstaltung.class);
        Mockito.when(Veranstaltung.findById(1L)).thenReturn(mockVeranstaltung);
        Mockito.when(planService.getDetaillierterPlan(any())).thenReturn(Collections.emptyList());

        given()
                .when().get("1/raeume-pdf")
                .then()
                .statusCode(200)
                .contentType("application/pdf");
    }

    @Test
    @TestSecurity(user = "testAdmin", roles = "ADMIN")
    void getAlleRaumschilder_asAdmin_shouldSucceed() {
        PanacheMock.mock(Veranstaltung.class);
        Mockito.when(Veranstaltung.findById(1L)).thenReturn(mockVeranstaltung);
        Mockito.when(planService.getRaumbelegungsplan(any())).thenReturn(Collections.emptyMap());

        given()
                .when().get("1/raumschilder")
                .then()
                .statusCode(200)
                .contentType(MediaType.TEXT_HTML);
    }

    @Test
    @TestSecurity(user = "testAdmin", roles = "ADMIN")
    void getAlleRaumschilderPdf_asAdmin_shouldSucceed() {
        PanacheMock.mock(Veranstaltung.class);
        Mockito.when(Veranstaltung.findById(1L)).thenReturn(mockVeranstaltung);

        given()
                .when().get("1/raumschilder-pdf")
                .then()
                .statusCode(200)
                .contentType("application/pdf");

    }

    @Test
    @TestSecurity(user = "testAdmin", roles = "ADMIN")
    void getFreieSlotsReferenten_asAdmin_shouldSucceed() {
        PanacheMock.mock(Veranstaltung.class);
        PanacheMock.mock(Referent.class);
        Mockito.when(Veranstaltung.findById(1L)).thenReturn(mockVeranstaltung);
        // Mock Panache Query
        var mockQuery = Mockito.mock(PanacheQuery.class);
        Mockito.when(mockQuery.list()).thenReturn(Collections.emptyList());
        Mockito.when(Referent.find(any(String.class), anyLong())).thenReturn(mockQuery);

        given()
                .when().get("1/freie-slots-referenten")
                .then()
                .statusCode(200)
                .contentType(MediaType.TEXT_HTML);
    }

    @Test
    @TestSecurity(user = "testAdmin", roles = "ADMIN")
    void getFreieSlotsTeilnehmerPdf_asAdmin_shouldSucceed() {
        PanacheMock.mock(Veranstaltung.class);
        Veranstaltung veranstaltungMock = Mockito.mock(Veranstaltung.class);
        Mockito.when(Veranstaltung.findById(1L)).thenReturn(veranstaltungMock);
        Mockito.when(veranstaltungMock.teilnehmer()).thenReturn(Collections.emptyList());

        given()
                .when().get("1/freie-slots-teilnehmer-pdf")
                .then()
                .statusCode(200)
                .contentType("application/pdf");
    }
}