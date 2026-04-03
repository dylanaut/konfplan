package kreyj.vortragsmanager.resource;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import kreyj.vortragsmanager.dto.VortragStatDto;
import kreyj.vortragsmanager.entity.Referent;
import kreyj.vortragsmanager.entity.User;
import kreyj.vortragsmanager.entity.Vortrag;
import kreyj.vortragsmanager.service.AdminService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;

@QuarkusTest
@TestSecurity(user = "admin@example.com", roles = "ADMIN")
class AdminResourceTest {

    @InjectMock
    AdminService adminService;

    @BeforeEach
    void setup() {
        Mockito.reset(adminService);
    }

    @Test
    void testGetAllVortrags() {
        Vortrag vortrag1 = new Vortrag();
        vortrag1.id = 1L;
        vortrag1.title = "Test Vortrag 1";

        Vortrag vortrag2 = new Vortrag();
        vortrag2.id = 2L;
        vortrag2.title = "Test Vortrag 2";

        Mockito.when(adminService.getAllVortraege()).thenReturn(List.of(vortrag1, vortrag2));

        given()
                .when().get("/api/admin/vortrags")
                .then()
                .statusCode(200)
                .contentType(MediaType.APPLICATION_JSON)
                .body("size()", is(2),
                        "[0].title", is("Test Vortrag 1"),
                        "[1].title", is("Test Vortrag 2"));
    }

    @Test
    void testGetAllSpeakers() {
        User speaker1 = new Referent();
        speaker1.email = "speaker1@example.com";
        speaker1.role = "SPEAKER";

        Mockito.when(adminService.getAllReferenten()).thenReturn(List.of(speaker1));

        given()
                .when().get("/api/admin/speakers")
                .then()
                .statusCode(200)
                .contentType(MediaType.APPLICATION_JSON)
                .body("size()", is(1),
                        "[0].email", is("speaker1@example.com"));
    }

    @Test
    void testUpdateVortrag_Success() {
        Vortrag updated = new Vortrag();
        updated.id = 1L;
        updated.title = "New Title";

        Mockito.when(adminService.updateVortrag(Mockito.eq(1L), Mockito.any(Vortrag.class)))
                .thenReturn(updated);

        given()
                .contentType(MediaType.APPLICATION_JSON)
                .body(updated)
                .when().put("/api/admin/vortrags/{id}", 1L)
                .then()
                .statusCode(200)
                .body("title", is("New Title"));
    }

    @Test
    @TestSecurity(user = "user@example.com", roles = "USER")
    void testGetAllVortrags_Forbidden() {
        given()
                .when().get("/api/admin/vortrags")
                .then()
                .statusCode(403);
    }

    @Test
    @TestSecurity
    void testGetAllVortrags_Unauthorized() {
        given()
                .when().get("/api/admin/vortrags")
                .then()
                .statusCode(401);
    }

    @Test
    void testGetStats() {
        VortragStatDto stats = new VortragStatDto("Vortrag A", 5, 2, 8);
        Mockito.when(adminService.getStats()).thenReturn(List.of(stats));

        given()
                .when().get("/api/admin/stats")
                .then()
                .statusCode(200)
                .contentType(MediaType.APPLICATION_JSON)
                .body("size()", is(1),
                        "[0].title", is("Vortrag A"),
                        "[0].countPrio1", is(5),
                        "[0].countTop3", is(2),
                        "[0].totalVotes", is(8));
    }

    @Test
    void testExportCsv() {
        Mockito.when(adminService.exportCsv()).thenReturn(
                Response.ok("test@test.de,Last,First,Org")
                        .type(MediaType.TEXT_PLAIN)
                        .build()
        );

        given()
                .when().get("/api/admin/export/csv")
                .then()
                .statusCode(200)
                .contentType(MediaType.TEXT_PLAIN)
                .body(containsString("test@test.de"));
    }
}