package kreyj.vortragsmanager.resource;

import io.quarkus.elytron.security.common.BcryptUtil;
import io.quarkus.hibernate.orm.panache.PanacheQuery;
import io.quarkus.mailer.Mail;
import io.quarkus.mailer.MockMailbox;
import io.quarkus.panache.mock.PanacheMock;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.h2.H2DatabaseTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.MediaType;
import kreyj.vortragsmanager.dto.LoginRequest;
import kreyj.vortragsmanager.dto.ResetRequest;
import kreyj.vortragsmanager.entity.Admin;
import kreyj.vortragsmanager.entity.Nutzer;
import kreyj.vortragsmanager.entity.Teilnehmer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.LocalDateTime;
import java.util.List;

import static io.restassured.RestAssured.given;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

@QuarkusTest
@QuarkusTestResource(H2DatabaseTestResource.class)
class AuthResourceTest {

    @Inject
    MockMailbox mailbox;

    @BeforeEach
    void setup() {
        PanacheMock.mock(Nutzer.class);
    }

    @Test
    void testForgotPassword_UserExists() {
        Nutzer nutzer = new Admin();
        nutzer.email = "test@example.com";
        nutzer.firstName = "Test";
        nutzer.role = "ADMIN";
        nutzer.passwordHash = "some-dummy-hash"; // Passwort setzen, um NOT NULL constraint zu erfüllen

        Mockito.when(Nutzer.findByEmail("test@example.com")).thenReturn(nutzer);
        // Wir müssen sicherstellen, dass persist() auf dem Mock-Nutzer nichts tut
        Mockito.doNothing().when(Mockito.mock(Nutzer.class)).persist();

        given()
                .queryParam("email", "test@example.com")
                .when().post("/api/auth/forgot-password")
                .then()
                .statusCode(202);

        List<Mail> mails = mailbox.getMailsSentTo("test@example.com");

        assertThat(mails.size(), is(1));
        assertThat(mails.getFirst().getSubject(), is("Passwort zurücksetzen - Vortragsmanager"));
    }

    @Test
    void testForgotPassword_UserNotFound() {
        Mockito.when(Nutzer.findByEmail("unknown@example.com")).thenReturn(null);

        given()
                .queryParam("email", "unknown@example.com")
                .when().post("/api/auth/forgot-password")
                .then()
                .statusCode(202);
    }

    @Test
    void testResetPassword_Success() {
        Nutzer nutzer = new Teilnehmer();
        nutzer.resetToken = "valid-token";
        nutzer.resetTokenExpiry = LocalDateTime.now().plusHours(1);
        nutzer.passwordHash = BcryptUtil.bcryptHash("oldSecretPassword");

        PanacheQuery query = Mockito.mock(PanacheQuery.class);
        Mockito.when(Nutzer.find("resetToken", "valid-token")).thenReturn(query);
        Mockito.when(query.firstResult()).thenReturn(nutzer);

        ResetRequest req = new ResetRequest("valid-token", "newSecretPassword");

        given()
                .contentType(MediaType.APPLICATION_JSON)
                .body(req)
                .when().post("/api/auth/reset-password")
                .then()
                .statusCode(200);
    }

    @Test
    void testLogin_Success() {
        Nutzer nutzer = new Teilnehmer();
        nutzer.email = "nutzer@example.com";
        nutzer.passwordHash = BcryptUtil.bcryptHash("correctPassword");
        nutzer.role = "TEILNEHMER";
        nutzer.isActive = true;

        Mockito.when(Nutzer.findByEmail("nutzer@example.com")).thenReturn(nutzer);

        LoginRequest loginReq = new LoginRequest("nutzer@example.com", "correctPassword");

        given()
                .contentType(MediaType.APPLICATION_JSON)
                .body(loginReq)
                .when().post("/api/auth/login")
                .then()
                .statusCode(200)
                .body("token", notNullValue())
                .body("role", is("TEILNEHMER"));
    }
}
