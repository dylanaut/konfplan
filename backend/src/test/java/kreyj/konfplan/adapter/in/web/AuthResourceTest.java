package kreyj.konfplan.adapter.in.web;

import io.quarkus.elytron.security.common.BcryptUtil;
import io.quarkus.hibernate.orm.panache.PanacheQuery;
import io.quarkus.mailer.Mail;
import io.quarkus.mailer.MockMailbox;
import io.quarkus.panache.mock.PanacheMock;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.common.http.TestHTTPEndpoint;
import io.quarkus.test.h2.H2DatabaseTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.MediaType;
import kreyj.konfplan.domain.service.ForgotPasswordRateLimiterService;
import kreyj.konfplan.domain.service.LoginRateLimiterService;
import kreyj.konfplan.domain.service.MailService;
import kreyj.konfplan.domain.service.TokenInvalidationService;
import kreyj.konfplan.persistence.Admin;
import kreyj.konfplan.persistence.Nutzer;
import kreyj.konfplan.persistence.Teilnehmer;
import kreyj.konfplan.adapter.in.web.dto.LoginRequest;
import kreyj.konfplan.adapter.in.web.dto.ResetRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

import static io.restassured.RestAssured.given;
import static jakarta.ws.rs.core.Response.Status.ACCEPTED;
import static jakarta.ws.rs.core.Response.Status.OK;
import static jakarta.ws.rs.core.Response.Status.UNAUTHORIZED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;


@QuarkusTest
@QuarkusTestResource(H2DatabaseTestResource.class)
@TestHTTPEndpoint(AuthResource.class)
class AuthResourceTest {

    @Inject
    MockMailbox mailbox;

    @Inject
    MailService mailService;

    @Inject
    LoginRateLimiterService loginRateLimiterService;

    @Inject
    ForgotPasswordRateLimiterService forgotPasswordRateLimiterService;

    @Inject
    TokenInvalidationService tokenInvalidationService;


    @BeforeEach
    void setup() {
        PanacheMock.mock(Nutzer.class);
        // Zustand der Rate-Limiter zwischen Testfaellen zuruecksetzen, sonst wuerden sich
        // die Zaehler der einzelnen Tests gegenseitig beeinflussen.
        loginRateLimiterService.reset();
        forgotPasswordRateLimiterService.reset();
        tokenInvalidationService.reset();
    }


    @AfterEach
    public void afterEach() {
        // clear the mailbox after each test run if you prefer
        mailbox.clear();
        loginRateLimiterService.reset();
        forgotPasswordRateLimiterService.reset();
        tokenInvalidationService.reset();
    }


    @Test
    void shouldSendRegistrationConfirmation() {
        Teilnehmer tn = new Teilnehmer();
        tn.setEmail("max@example.com");
        tn.setFirstName("Max");
        tn.setLastName("Mustermann");
        tn.setRole("TEILNEHMER");

        mailService.sendRegistrationConfirmation(tn);

        // erste Mail an diese Adresse finden
        List<Mail> mails = mailbox.getMailsSentTo("max@example.com");
        assertThat(mails).hasSize(1);
        Mail message = mails.getFirst();

        assertThat(message.getSubject()).isEqualTo("Willkommen bei KonfPlan!");
        assertThat(message.getTo().getFirst()).isEqualTo("max@example.com");
        assertThat(message.getHtml()).contains("Hallo Max");
    }


    @Test
    void testForgotPassword_UserExists() {
        Nutzer nutzer = new Admin();
        nutzer.assignLoginName("testadmin");
        nutzer.setEmail("test@example.com");
        nutzer.setFirstName("Test");
        nutzer.setRole("ADMIN");
        nutzer.setPasswordHash("some-dummy-hash"); // Passwort setzen, um NOT NULL constraint zu erfüllen

        Mockito.when(Nutzer.findByLoginName("testadmin")).thenReturn(nutzer);
        // Wir müssen sicherstellen, dass persist() auf dem Mock-Nutzer nichts tut
        Mockito.doNothing().when(Mockito.mock(Nutzer.class)).persist();

        given()
                .queryParam("loginName", "testadmin")
                .when().post("/forgot-password")
                .then()
                .statusCode(ACCEPTED.getStatusCode());

        List<Mail> mailList = mailbox.getMailsSentTo("test@example.com");

        assertThat(mailList).hasSize(1);

        assertThat(mailList.getFirst().getSubject()).isEqualTo("Passwort zurücksetzen - KonfPlan");
    }


    @Test
    void testForgotPassword_UserNotFound() {
        Mockito.when(Nutzer.findByLoginName("unknown")).thenReturn(null);

        given()
                .queryParam("loginName", "unknown")
                .when().post("/forgot-password")
                .then()
                .statusCode(ACCEPTED.getStatusCode());
    }


    @Test
    void testForgotPassword_RateLimited_AfterMaxAttempts() {
        Mockito.when(Nutzer.findByLoginName("unknown")).thenReturn(null);

        // Default app.security.forgot-password-rate-limit.max-attempts=5: die ersten 5
        // Anfragen werden regulaer mit 202 akzeptiert (auch fuer einen unbekannten Anmeldenamen -
        // forgotPassword() antwortet immer 202, um keine Rueckschluesse zuzulassen).
        for (int i = 0; i < 5; i++) {
            given()
                    .queryParam("loginName", "unknown")
                    .when().post("/forgot-password")
                    .then()
                    .statusCode(ACCEPTED.getStatusCode());
        }

        // Die 6. Anfrage von derselben IP wird geblockt.
        given()
                .queryParam("loginName", "unknown")
                .when().post("/forgot-password")
                .then()
                .statusCode(429)
                .header("Retry-After", notNullValue());
    }


    @Test
    void testForgotPassword_RateLimit_IsScopedPerIp() {
        Mockito.when(Nutzer.findByLoginName("unknown")).thenReturn(null);

        // IP "1.1.1.1" bis zur Sperre ausreizen.
        for (int i = 0; i < 5; i++) {
            given()
                    .queryParam("loginName", "unknown")
                    .header("X-Forwarded-For", "1.1.1.1")
                    .when().post("/forgot-password")
                    .then().statusCode(ACCEPTED.getStatusCode());
        }
        given()
                .queryParam("loginName", "unknown")
                .header("X-Forwarded-For", "1.1.1.1")
                .when().post("/forgot-password")
                .then().statusCode(429);

        // Eine andere IP ("2.2.2.2") darf davon unberuehrt weiter (regulaer mit 202) anfragen.
        given()
                .queryParam("loginName", "unknown")
                .header("X-Forwarded-For", "2.2.2.2")
                .when().post("/forgot-password")
                .then().statusCode(ACCEPTED.getStatusCode());
    }


    @Test
    void testResetPassword_Success() {
        Nutzer nutzer = new Teilnehmer();
        nutzer.assignLoginName("resetuser");
        nutzer.setResetToken("valid-token");
        nutzer.setResetTokenExpiry(LocalDateTime.now().plusHours(1));
        nutzer.setPasswordHash(BcryptUtil.bcryptHash("oldSecretPassword"));

        PanacheQuery query = Mockito.mock(PanacheQuery.class);
        Mockito.when(Nutzer.find("resetToken", "valid-token")).thenReturn(query);
        Mockito.when(query.firstResult()).thenReturn(nutzer);

        ResetRequest req = new ResetRequest("valid-token", "newSecretPassword");

        given()
                .contentType(MediaType.APPLICATION_JSON)
                .body(req)
                .when().post("/reset-password")
                .then()
                .statusCode(OK.getStatusCode());
    }


    @Test
    void testResetPassword_InvalidatesTokensIssuedBeforeTheReset() {
        // Ein bereits ausgestelltes Token (z.B. bei einem gestohlenen Login) darf nach einem
        // Passwort-Reset nicht weiterhin gueltig bleiben, bis es regulaer abgelaufen ist (siehe
        // TokenInvalidationService).
        Nutzer nutzer = new Teilnehmer();
        nutzer.assignLoginName("resetuser2");
        nutzer.setResetToken("valid-token-2");
        nutzer.setResetTokenExpiry(LocalDateTime.now().plusHours(1));
        nutzer.setPasswordHash(BcryptUtil.bcryptHash("oldSecretPassword"));

        PanacheQuery query = Mockito.mock(PanacheQuery.class);
        Mockito.when(Nutzer.find("resetToken", "valid-token-2")).thenReturn(query);
        Mockito.when(query.firstResult()).thenReturn(nutzer);

        Instant beforeReset = Instant.now().minusSeconds(5);
        assertThat(tokenInvalidationService.isValid("resetuser2", beforeReset)).isTrue();

        given()
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ResetRequest("valid-token-2", "newSecretPassword"))
                .when().post("/reset-password")
                .then()
                .statusCode(OK.getStatusCode());

        assertThat(tokenInvalidationService.isValid("resetuser2", beforeReset)).isFalse();
        assertThat(tokenInvalidationService.isValid("resetuser2", Instant.now().plusSeconds(5))).isTrue();
    }


    @Test
    void testLogin_Success() {
        Nutzer nutzer = new Teilnehmer();
        nutzer.assignLoginName("nutzeruser");
        nutzer.setEmail("nutzer@example.com");
        nutzer.setPasswordHash(BcryptUtil.bcryptHash("correctPassword"));
        nutzer.setRole("TEILNEHMER");
        nutzer.setActive(true);

        Mockito.when(Nutzer.findByLoginName("nutzeruser")).thenReturn(nutzer);

        LoginRequest loginReq = new LoginRequest("nutzeruser", "correctPassword");

        given()
                .contentType(MediaType.APPLICATION_JSON)
                .body(loginReq)
                .when().post("/login")
                .then()
                .statusCode(OK.getStatusCode())
                .body("token", notNullValue())
                .body("role", is("TEILNEHMER"));
    }


    @Test
    void testLogin_Failure_WrongPassword() {
        Nutzer nutzer = new Teilnehmer();
        nutzer.assignLoginName("nutzeruser");
        nutzer.setPasswordHash(BcryptUtil.bcryptHash("correctPassword"));
        nutzer.setRole("TEILNEHMER");
        nutzer.setActive(true);

        Mockito.when(Nutzer.findByLoginName("nutzeruser")).thenReturn(nutzer);

        given()
                .contentType(MediaType.APPLICATION_JSON)
                .body(new LoginRequest("nutzeruser", "wrongPassword"))
                .when().post("/login")
                .then()
                .statusCode(UNAUTHORIZED.getStatusCode());
    }


    @Test
    void testLogin_RateLimited_AfterMaxFailedAttempts() {
        Mockito.when(Nutzer.findByLoginName("nutzeruser")).thenReturn(null);
        LoginRequest badLogin = new LoginRequest("nutzeruser", "wrongPassword");

        // Default app.security.login-rate-limit.max-attempts=5: die ersten 5 Fehlversuche
        // werden regulaer mit 401 abgelehnt.
        for (int i = 0; i < 5; i++) {
            given()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(badLogin)
                    .when().post("/login")
                    .then()
                    .statusCode(UNAUTHORIZED.getStatusCode());
        }

        // Der 6. Versuch von derselben IP wird geblockt, bevor ueberhaupt Anmeldedaten geprueft werden.
        given()
                .contentType(MediaType.APPLICATION_JSON)
                .body(badLogin)
                .when().post("/login")
                .then()
                .statusCode(429)
                .header("Retry-After", notNullValue());
    }


    @Test
    void testLogin_RateLimit_ResetsAfterSuccessfulLogin() {
        Nutzer nutzer = new Teilnehmer();
        nutzer.assignLoginName("nutzeruser");
        nutzer.setPasswordHash(BcryptUtil.bcryptHash("correctPassword"));
        nutzer.setRole("TEILNEHMER");
        nutzer.setActive(true);
        Mockito.when(Nutzer.findByLoginName("nutzeruser")).thenReturn(nutzer);

        LoginRequest badLogin = new LoginRequest("nutzeruser", "wrongPassword");
        LoginRequest goodLogin = new LoginRequest("nutzeruser", "correctPassword");

        // 4 Fehlversuche (unterhalb des Limits von 5), dann ein erfolgreicher Login.
        for (int i = 0; i < 4; i++) {
            given().contentType(MediaType.APPLICATION_JSON).body(badLogin)
                    .when().post("/login")
                    .then().statusCode(UNAUTHORIZED.getStatusCode());
        }
        given().contentType(MediaType.APPLICATION_JSON).body(goodLogin)
                .when().post("/login")
                .then().statusCode(OK.getStatusCode());

        // Der erfolgreiche Login muss den Fehlversuch-Zaehler zuruecksetzen: erneut 4
        // Fehlversuche duerfen NICHT blockiert werden (waeren es kumuliert 8, ueber dem Limit).
        for (int i = 0; i < 4; i++) {
            given().contentType(MediaType.APPLICATION_JSON).body(badLogin)
                    .when().post("/login")
                    .then().statusCode(UNAUTHORIZED.getStatusCode());
        }
    }


    @Test
    void testLogin_RateLimit_IsScopedPerIp() {
        Mockito.when(Nutzer.findByLoginName("nutzeruser")).thenReturn(null);
        LoginRequest badLogin = new LoginRequest("nutzeruser", "wrongPassword");

        // IP "1.1.1.1" bis zur Sperre ausreizen.
        for (int i = 0; i < 5; i++) {
            given()
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Forwarded-For", "1.1.1.1")
                    .body(badLogin)
                    .when().post("/login")
                    .then().statusCode(UNAUTHORIZED.getStatusCode());
        }
        given()
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Forwarded-For", "1.1.1.1")
                .body(badLogin)
                .when().post("/login")
                .then().statusCode(429);

        // Eine andere IP ("2.2.2.2") darf davon unberuehrt weiter (regulaer mit 401) anfragen.
        given()
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Forwarded-For", "2.2.2.2")
                .body(badLogin)
                .when().post("/login")
                .then().statusCode(UNAUTHORIZED.getStatusCode());
    }
}
