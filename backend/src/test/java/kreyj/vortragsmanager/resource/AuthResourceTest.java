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
import kreyj.vortragsmanager.entity.Teilnehmer;
import kreyj.vortragsmanager.entity.User;
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
        PanacheMock.mock(User.class);
    }

    @Test
    void testForgotPassword_UserExists() {
        User user = new Admin();
        user.email = "test@example.com";
        user.firstName = "Test";
        user.role = "ADMIN";
        user.passwordHash = "some-dummy-hash"; // Passwort setzen, um NOT NULL constraint zu erfüllen

        Mockito.when(User.findByEmail("test@example.com")).thenReturn(user);
        // Wir müssen sicherstellen, dass persist() auf dem Mock-User nichts tut
        Mockito.doNothing().when(Mockito.mock(User.class)).persist();

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
        Mockito.when(User.findByEmail("unknown@example.com")).thenReturn(null);

        given()
                .queryParam("email", "unknown@example.com")
                .when().post("/api/auth/forgot-password")
                .then()
                .statusCode(202);
    }

    @Test
    void testResetPassword_Success() {
        User user = new Teilnehmer();
        user.resetToken = "valid-token";
        user.resetTokenExpiry = LocalDateTime.now().plusHours(1);
        user.passwordHash = BcryptUtil.bcryptHash("oldSecretPassword");

        PanacheQuery query = Mockito.mock(PanacheQuery.class);
        Mockito.when(User.find("resetToken", "valid-token")).thenReturn(query);
        Mockito.when(query.firstResult()).thenReturn(user);

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
        User user = new Teilnehmer();
        user.email = "user@example.com";
        user.passwordHash = BcryptUtil.bcryptHash("correctPassword");
        user.role = "TEILNEHMER";
        user.isActive = true;

        Mockito.when(User.findByEmail("user@example.com")).thenReturn(user);

        LoginRequest loginReq = new LoginRequest("user@example.com", "correctPassword");

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
