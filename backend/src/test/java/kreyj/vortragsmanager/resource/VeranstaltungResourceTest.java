package kreyj.vortragsmanager.resource;

import io.quarkus.test.InjectMock;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.h2.H2DatabaseTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import kreyj.vortragsmanager.dto.UserDto;
import kreyj.vortragsmanager.dto.VortragStatDto;
import kreyj.vortragsmanager.entity.EventSlot;
import kreyj.vortragsmanager.entity.Vortrag;
import kreyj.vortragsmanager.entity.Wahlvortrag;
import kreyj.vortragsmanager.service.AdminService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Collections;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;

@QuarkusTest
@TestSecurity(user = "admin@test.de", roles = "ADMIN")
@QuarkusTestResource(H2DatabaseTestResource.class)
class VeranstaltungResourceTest {
    @InjectMock
    AdminService adminService;

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
                .body("[0].countPrio2", is(4))
                .body("[0].countPrio3", is(3));
    }

    @Test
    void testCreateNutzerHierarchical() {
        Long vid = 1L;
        UserDto t = new UserDto();
        t.email = "new@test.de";
        t.role = "TEILNEHMER";

        Mockito.when(adminService.createUser(Mockito.any(), Mockito.anyList())).thenReturn(t);

        given()
                .contentType(ContentType.JSON)
                .body(t)
                .when().post("/api/veranstaltungen/{vid}/nutzer", vid)
                .then()
                .statusCode(201)
                .body("email", is("new@test.de"));
    }
}
