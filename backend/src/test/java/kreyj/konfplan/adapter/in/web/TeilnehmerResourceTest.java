package kreyj.konfplan.adapter.in.web;

import io.quarkus.elytron.security.common.BcryptUtil;
import io.quarkus.mailer.Mail;
import io.quarkus.mailer.MockMailbox;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.common.http.TestHTTPEndpoint;
import io.quarkus.test.h2.H2DatabaseTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.jwt.Claim;
import io.quarkus.test.security.jwt.JwtSecurity;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import kreyj.konfplan.adapter.in.web.dto.EmailChangeRequestDto;
import kreyj.konfplan.adapter.in.web.dto.NutzerDto;
import kreyj.konfplan.persistence.Nutzer;
import kreyj.konfplan.persistence.Teilnehmer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static jakarta.ws.rs.core.Response.Status.BAD_REQUEST;
import static jakarta.ws.rs.core.Response.Status.CONFLICT;
import static jakarta.ws.rs.core.Response.Status.OK;
import static jakarta.ws.rs.core.Response.Status.FORBIDDEN;
import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
@QuarkusTestResource(H2DatabaseTestResource.class)
@TestHTTPEndpoint(TeilnehmerResource.class)
class TeilnehmerResourceTest extends DatabaseCleaner {

    @Inject
    MockMailbox mailbox;

    Long teilnehmerId;


    @BeforeEach
    @Transactional
    void setup() {
        Teilnehmer t = new Teilnehmer();
        t.assignLoginName("tom.teilnehmer");
        t.setEmail("tom.alt@test.de");
        t.setFirstName("Tom");
        t.setLastName("Teilnehmer");
        t.setPasswordHash(BcryptUtil.bcryptHash("correctPassword"));
        t.persist();
        teilnehmerId = t.getId();
    }


    @AfterEach
    void afterEach() {
        mailbox.clear();
    }


    @Test
    @TestSecurity(user = "tom.teilnehmer", roles = "TEILNEHMER")
    @JwtSecurity(claims = {@Claim(key = "upn", value = "tom.teilnehmer")})
    void requestEmailChange_success_sendsMailsAndStoresPendingChange() {
        EmailChangeRequestDto req = new EmailChangeRequestDto();
        req.currentPassword = "correctPassword";
        req.newEmail = "tom.neu@test.de";

        given()
            .contentType(ContentType.JSON)
            .body(req)
            .when().post("/email-change-request")
            .then()
            .statusCode(OK.getStatusCode());

        Nutzer nutzer = Nutzer.<Nutzer>findById(teilnehmerId);
        assertThat(nutzer.getNewEmail()).isEqualTo("tom.neu@test.de");
        assertThat(nutzer.getEmailChangeToken()).isNotBlank();
        assertThat(nutzer.getEmailChangeTokenExpiry()).isAfter(LocalDateTime.now());
        // E-Mail selbst darf erst nach Bestätigung geändert werden.
        assertThat(nutzer.getEmail()).isEqualTo("tom.alt@test.de");

        List<Mail> mailsToOld = mailbox.getMailsSentTo("tom.alt@test.de");
        assertThat(mailsToOld).hasSize(1);
        List<Mail> mailsToNew = mailbox.getMailsSentTo("tom.neu@test.de");
        assertThat(mailsToNew).hasSize(1);
        assertThat(mailsToNew.getFirst().getHtml()).contains("/email-change-confirm?token=" + nutzer.getEmailChangeToken());
    }


    @Test
    @TestSecurity(user = "tom.teilnehmer", roles = "TEILNEHMER")
    @JwtSecurity(claims = {@Claim(key = "upn", value = "tom.teilnehmer")})
    void requestEmailChange_wrongPassword_isRejected() {
        EmailChangeRequestDto req = new EmailChangeRequestDto();
        req.currentPassword = "wrongPassword";
        req.newEmail = "tom.neu@test.de";

        given()
            .contentType(ContentType.JSON)
            .body(req)
            .when().post("/email-change-request")
            .then()
            .statusCode(FORBIDDEN.getStatusCode());

        assertThat(mailbox.getMailsSentTo("tom.neu@test.de")).isEmpty();
    }


    @Test
    @TestSecurity(user = "tom.teilnehmer", roles = "TEILNEHMER")
    @JwtSecurity(claims = {@Claim(key = "upn", value = "tom.teilnehmer")})
    void requestEmailChange_emailAlreadyInUse_isRejected() {
        Teilnehmer other = new Teilnehmer();
        other.assignLoginName("other.teilnehmer");
        other.setEmail("bereits.vergeben@test.de");
        other.setPasswordHash(BcryptUtil.bcryptHash("x"));
        persistInNewTransaction(other);

        EmailChangeRequestDto req = new EmailChangeRequestDto();
        req.currentPassword = "correctPassword";
        req.newEmail = "bereits.vergeben@test.de";

        given()
            .contentType(ContentType.JSON)
            .body(req)
            .when().post("/email-change-request")
            .then()
            .statusCode(CONFLICT.getStatusCode());
    }


    @Test
    void confirmEmailChange_validToken_updatesEmail() {
        String token = UUID.randomUUID().toString();
        setPendingEmailChange(token, "tom.neu@test.de", LocalDateTime.now().plusHours(1));

        given()
            .queryParam("token", token)
            .when().get("/email-change-confirm")
            .then()
            .statusCode(OK.getStatusCode());

        Nutzer nutzer = Nutzer.<Nutzer>findById(teilnehmerId);
        assertThat(nutzer.getEmail()).isEqualTo("tom.neu@test.de");
        assertThat(nutzer.getNewEmail()).isNull();
        assertThat(nutzer.getEmailChangeToken()).isNull();
        assertThat(nutzer.getEmailChangeTokenExpiry()).isNull();
    }


    @Test
    void confirmEmailChange_unknownToken_isRejected() {
        given()
            .queryParam("token", "does-not-exist")
            .when().get("/email-change-confirm")
            .then()
            .statusCode(BAD_REQUEST.getStatusCode());

        assertThat(Nutzer.<Nutzer>findById(teilnehmerId).getEmail()).isEqualTo("tom.alt@test.de");
    }


    @Test
    void confirmEmailChange_expiredToken_isRejected() {
        String token = UUID.randomUUID().toString();
        setPendingEmailChange(token, "tom.neu@test.de", LocalDateTime.now().minusMinutes(1));

        given()
            .queryParam("token", token)
            .when().get("/email-change-confirm")
            .then()
            .statusCode(BAD_REQUEST.getStatusCode());

        assertThat(Nutzer.<Nutzer>findById(teilnehmerId).getEmail()).isEqualTo("tom.alt@test.de");
    }


    @Test
    @TestSecurity(user = "tom.teilnehmer", roles = "TEILNEHMER")
    @JwtSecurity(claims = {@Claim(key = "upn", value = "tom.teilnehmer")})
    void updateProfile_stillRejectsDirectEmailChange() {
        NutzerDto dto = new NutzerDto();
        dto.email = "versuchter.direkter.wechsel@test.de";
        dto.firstName = "Tom";
        dto.lastName = "Teilnehmer";
        dto.gruppen = List.of();
        dto.version = 0L;

        given()
            .contentType(ContentType.JSON)
            .body(dto)
            .when().put("/profile")
            .then()
            .statusCode(BAD_REQUEST.getStatusCode());

        assertThat(Nutzer.<Nutzer>findById(teilnehmerId).getEmail()).isEqualTo("tom.alt@test.de");
    }


    @Transactional
    void setPendingEmailChange(String token, String newEmail, LocalDateTime expiry) {
        Nutzer nutzer = Nutzer.<Nutzer>findById(teilnehmerId);
        nutzer.setNewEmail(newEmail);
        nutzer.setEmailChangeToken(token);
        nutzer.setEmailChangeTokenExpiry(expiry);
    }


    @Transactional
    void persistInNewTransaction(Teilnehmer teilnehmer) {
        teilnehmer.persist();
    }
}
