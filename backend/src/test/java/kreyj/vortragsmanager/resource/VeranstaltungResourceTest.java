package kreyj.vortragsmanager.resource;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import kreyj.vortragsmanager.dto.VortragStatDto;
import kreyj.vortragsmanager.entity.EventSlot;
import kreyj.vortragsmanager.entity.Teilnehmer;
import kreyj.vortragsmanager.entity.Vortrag;
import kreyj.vortragsmanager.entity.Wahlvortrag;
import kreyj.vortragsmanager.service.AdminService;
import kreyj.vortragsmanager.service.VeranstaltungService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Arrays;
import java.util.Collections;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;

@QuarkusTest
@TestSecurity(user = "admin@test.de", roles = "ADMIN")
class VeranstaltungResourceTest {

    @InjectMock
    AdminService adminService;

    @InjectMock
    VeranstaltungService veranstaltungService;

    @Test
    void testGetVortraegeHierarchical() {
        Long vid = 1L;
        Vortrag v1 = new Wahlvortrag();
        v1.id = 10L;
        v1.titel = "Test Vortrag";

        Mockito.when(adminService.getAllVortraege(vid)).thenReturn(Collections.singletonList(v1));

        given()
                .when().get("/api/veranstaltungen/{vid}/vortraege", vid)
                .then()
                .statusCode(200)
                .body("size()", is(1))
                .body("[0].titel", is("Test Vortrag"));
    }

    @Test
    void testGetSlotsHierarchical() {
        Long vid = 1L;
        EventSlot s1 = new EventSlot();
        s1.id = 5L;
        s1.description = "Slot A";

        Mockito.when(adminService.getAllEventSlots(vid)).thenReturn(Collections.singletonList(s1));

        given()
                .when().get("/api/veranstaltungen/{vid}/slots", vid)
                .then()
                .statusCode(200)
                .body("size()", is(1))
                .body("[0].description", is("Slot A"));
    }

    @Test
    void testGetStatsHierarchical() {
        Long vid = 1L;
        VortragStatDto dto = new VortragStatDto("Vortrag 1", 5, 4, 3, 10, 15);

        Mockito.when(adminService.getStats(vid)).thenReturn(Collections.singletonList(dto));

        given()
                .when().get("/api/veranstaltungen/{vid}/stats", vid)
                .then()
                .statusCode(200)
                .body("size()", is(1))
                .body("[0].titel", is("Vortrag 1"))
                .body("[0].countPrio1", is(5))
                .body("[0].countPrio1", is(4))
                .body("[0].countPrio1", is(3));
    }

    @Test
    void testCreateBenutzerHierarchical() {
        Long vid = 1L;
        Teilnehmer t = new Teilnehmer();
        t.email = "new@test.de";
        t.role = "TEILNEHMER";

        Mockito.when(adminService.createUser(Mockito.any(), Mockito.eq(vid))).thenReturn(t);

        given()
                .contentType(ContentType.JSON)
                .body(t)
                .when().post("/api/veranstaltungen/{vid}/benutzer", vid)
                .then()
                .statusCode(201)
                .body("email", is("new@test.de"));
    }
}
