package kreyj.vortragsmanager.resource;

import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import kreyj.vortragsmanager.entity.Admin;
import kreyj.vortragsmanager.entity.Referent;
import kreyj.vortragsmanager.entity.Teilnehmer;
import kreyj.vortragsmanager.entity.User;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
public class UserInheritanceTest {

    @Test
    @TestSecurity(user = "admin@test.de", roles = "ADMIN")
    @TestTransaction
    public void testPersistAdminViaJson() {
        String json = """
        {
            "email": "new-admin@vortragsmanager.de",
            "firstName": "Super",
            "lastName": "Admin",
            "role": "ADMIN"
        }
        """;

        given()
          .contentType(ContentType.JSON)
          .body(json)
          .when().post("/api/admin/users")
          .then()
          .statusCode(201);

        User user = User.findByEmail("new-admin@vortragsmanager.de");
        assertNotNull(user);
        assertTrue(user instanceof Admin, "User sollte eine Instanz von Admin sein");
        assertEquals("ADMIN", user.role);
    }

    @Test
    @TestSecurity(user = "admin@test.de", roles = "ADMIN")
    @TestTransaction
    public void testPersistReferentViaJson() {
        String json = """
        {
            "email": "expert@vortragsmanager.de",
            "firstName": "Max",
            "lastName": "Mustermann",
            "role": "REFERENT",
            "jobRole": "Software Architekt",
            "biography": "Langjährige Erfahrung in Java."
        }
        """;

        given()
          .contentType(ContentType.JSON)
          .body(json)
          .when().post("/api/admin/users")
          .then()
          .statusCode(201);

        User user = User.findByEmail("expert@vortragsmanager.de");
        assertNotNull(user);
        assertTrue(user instanceof Referent, "User sollte eine Instanz von Referent sein");
        Referent ref = (Referent) user;
        assertEquals("Software Architekt", ref.jobRole);
        assertEquals("Langjährige Erfahrung in Java.", ref.biography);
    }

    @Test
    @TestSecurity(user = "admin@test.de", roles = "ADMIN")
    @TestTransaction
    public void testPersistTeilnehmerViaJson() {
        String json = """
        {
            "email": "student@vortragsmanager.de",
            "firstName": "Lukas",
            "lastName": "Lernbereit",
            "role": "TEILNEHMER",
            "gruppe": "10.3"
        }
        """;

        given()
          .contentType(ContentType.JSON)
          .body(json)
          .when().post("/api/admin/users")
          .then()
          .statusCode(201);

        User user = User.findByEmail("student@vortragsmanager.de");
        assertNotNull(user);
        assertTrue(user instanceof Teilnehmer, "User sollte eine Instanz von Teilnehmer sein");
        Teilnehmer t = (Teilnehmer) user;
        assertEquals("10.3", t.gruppe);
    }
}
