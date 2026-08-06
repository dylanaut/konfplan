package kreyj.konfplan.adapter.in.web;

import io.quarkus.elytron.security.common.BcryptUtil;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.h2.H2DatabaseTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.smallrye.jwt.build.Jwt;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import kreyj.konfplan.domain.service.TokenInvalidationService;
import kreyj.konfplan.persistence.Admin;
import kreyj.konfplan.persistence.Nutzer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static io.restassured.RestAssured.given;

/**
 * Prueft die tatsaechliche Durchsetzung von {@link TokenInvalidationService} durch {@link
 * TokenInvalidationAugmentor} end-to-end mit einem echten, signierten JWT gegen einen echten
 * geschuetzten Endpunkt - nicht nur die reine Datenstruktur. Dafuer wird die JWT-Authentifizierung
 * (per %test.quarkus.smallrye-jwt.enabled=false sonst global deaktiviert, siehe
 * application.properties) fuer diese Testklasse gezielt wieder aktiviert.
 */
@QuarkusTest
@QuarkusTestResource(H2DatabaseTestResource.class)
@TestProfile(TokenInvalidationIntegrationTest.JwtEnabledProfile.class)
class TokenInvalidationIntegrationTest {

    public static class JwtEnabledProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("quarkus.smallrye-jwt.enabled", "true");
        }
    }

    private static final String LOGIN_NAME = "tokeninvalidation-admin";

    @Inject
    TokenInvalidationService tokenInvalidationService;

    @BeforeEach
    @Transactional
    void setup() {
        tokenInvalidationService.reset();
        Nutzer.deleteAll();

        Admin admin = new Admin();
        admin.assignLoginName(LOGIN_NAME);
        admin.setEmail("tokeninvalidation-admin@example.com");
        admin.setFirstName("Token");
        admin.setLastName("Invalidation");
        admin.setPasswordHash(BcryptUtil.bcryptHash("irrelevant"));
        admin.setActive(true);
        admin.persist();
    }

    @AfterEach
    void afterEach() {
        tokenInvalidationService.reset();
    }

    private String issueToken(Instant issuedAt) {
        return Jwt.issuer("https://konfplan.kreyj")
                .upn(LOGIN_NAME)
                .subject(LOGIN_NAME)
                .groups("ADMIN")
                .issuedAt(issuedAt)
                .expiresIn(Duration.ofHours(4))
                .sign();
    }

    @Test
    void tokenIssuedBeforePasswordResetIsRejectedAfterward() {
        String oldToken = issueToken(Instant.now().minusSeconds(30));

        given().auth().oauth2(oldToken)
                .when().get("/api/admin/protokolle")
                .then().statusCode(200);

        tokenInvalidationService.invalidateTokensIssuedBefore(LOGIN_NAME);

        given().auth().oauth2(oldToken)
                .when().get("/api/admin/protokolle")
                .then().statusCode(401);
    }

    @Test
    void tokenIssuedAfterPasswordResetRemainsValid() {
        tokenInvalidationService.invalidateTokensIssuedBefore(LOGIN_NAME);

        String newToken = issueToken(Instant.now().plusSeconds(5));

        given().auth().oauth2(newToken)
                .when().get("/api/admin/protokolle")
                .then().statusCode(200);
    }

    @Test
    void unrelatedUsersTokenIsUnaffectedByAnotherUsersPasswordReset() {
        tokenInvalidationService.invalidateTokensIssuedBefore("some-other-user");

        String token = issueToken(Instant.now().minusSeconds(30));

        given().auth().oauth2(token)
                .when().get("/api/admin/protokolle")
                .then().statusCode(200);
    }
}
