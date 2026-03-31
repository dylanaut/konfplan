package kreyj.vortragsmanager.resource;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import kreyj.vortragsmanager.dto.TalkStatDto;
import kreyj.vortragsmanager.entity.Talk;
import kreyj.vortragsmanager.entity.User;
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
    void testGetAllTalks() {
        Talk talk1 = new Talk();
        talk1.id = 1L;
        talk1.title = "Test Talk 1";

        Talk talk2 = new Talk();
        talk2.id = 2L;
        talk2.title = "Test Talk 2";

        Mockito.when(adminService.getAllTalks()).thenReturn(List.of(talk1, talk2));

        given()
                .when().get("/api/admin/talks")
                .then()
                .statusCode(200)
                .contentType(MediaType.APPLICATION_JSON)
                .body("size()", is(2),
                        "[0].title", is("Test Talk 1"),
                        "[1].title", is("Test Talk 2"));
    }

    @Test
    void testGetAllSpeakers() {
        User speaker1 = new User();
        speaker1.email = "speaker1@example.com";
        speaker1.role = "SPEAKER";

        Mockito.when(adminService.getAllSpeakers()).thenReturn(List.of(speaker1));

        given()
                .when().get("/api/admin/speakers")
                .then()
                .statusCode(200)
                .contentType(MediaType.APPLICATION_JSON)
                .body("size()", is(1),
                        "[0].email", is("speaker1@example.com"));
    }

    @Test
    void testUpdateTalk_Success() {
        Talk updated = new Talk();
        updated.id = 1L;
        updated.title = "New Title";

        Mockito.when(adminService.updateTalk(Mockito.eq(1L), Mockito.any(Talk.class)))
                .thenReturn(updated);

        given()
                .contentType(MediaType.APPLICATION_JSON)
                .body(updated)
                .when().put("/api/admin/talks/{id}", 1L)
                .then()
                .statusCode(200)
                .body("title", is("New Title"));
    }

    @Test
    @TestSecurity(user = "user@example.com", roles = "USER")
    void testGetAllTalks_Forbidden() {
        given()
                .when().get("/api/admin/talks")
                .then()
                .statusCode(403);
    }

    @Test
    @TestSecurity
    void testGetAllTalks_Unauthorized() {
        given()
                .when().get("/api/admin/talks")
                .then()
                .statusCode(401);
    }

    @Test
    void testGetStats() {
        TalkStatDto stats = new TalkStatDto("Talk A", 5, 2, 8);
        Mockito.when(adminService.getStats()).thenReturn(List.of(stats));

        given()
                .when().get("/api/admin/stats")
                .then()
                .statusCode(200)
                .contentType(MediaType.APPLICATION_JSON)
                .body("size()", is(1),
                        "[0].title", is("Talk A"),
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