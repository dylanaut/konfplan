package kreyj.vortragsmanager.resource;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import kreyj.vortragsmanager.entity.Admin;
import kreyj.vortragsmanager.entity.User;
import kreyj.vortragsmanager.service.AdminService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.nio.file.Path;
import java.util.Arrays;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;

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
    void testGetAllUsersGlobal() {
        User admin1 = new Admin();
        admin1.email = "admin1@example.com";
        admin1.role = "ADMIN";

        // Hinweis: Wir nutzen hier eine generische Liste von Usern
        Mockito.when(adminService.getAllUsers()).thenReturn(Arrays.asList(admin1));

        given()
                .when().get("/api/admin/users")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("size()", is(1))
                .body("[0].email", is("admin1@example.com"));
    }

    @Test
    void testImportAdminsGlobal() throws Exception {
        Mockito.when(adminService.importAdminsFromCsv(Mockito.any(Path.class))).thenReturn(3);

        given()
                .multiPart("file", "admins.csv", "Vorname;Nachname;Email\nAdmin;One;a1@test.de".getBytes(), "text/csv")
                .when().post("/api/admin/admins/import")
                .then()
                .statusCode(200)
                .body(is("Import erfolgreich: 3 Administratoren angelegt."));
    }

    @Test
    @TestSecurity(user = "user@example.com", roles = "USER")
    void testGlobalAccessForbidden() {
        given()
                .when().get("/api/admin/users")
                .then()
                .statusCode(403);
    }
}
