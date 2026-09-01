package kreyj.konfplan.infrastructure.observability;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class AuthFailureUsernameLoggerTest {

    private AuthFailureUsernameLogger logger;

    @BeforeEach
    void setup() {
        logger = new AuthFailureUsernameLogger();
        logger.objectMapper = new ObjectMapper();
    }

    private String fakeJwt(String payloadJson) {
        return encode("{\"alg\":\"RS256\"}") + "." + encode(payloadJson) + ".fake-signature";
    }

    private String encode(String json) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }


    @Test
    void extractPreferredUsername_gueltigesToken_liefertUsername() {
        String jwt = fakeJwt("{\"preferred_username\":\"tom.teilnehmer\"}");
        assertThat(logger.extractPreferredUsername(jwt)).isEqualTo("tom.teilnehmer");
    }

    @Test
    void extractPreferredUsername_fehlenderClaim_liefertNull() {
        String jwt = fakeJwt("{\"sub\":\"123\"}");
        assertThat(logger.extractPreferredUsername(jwt)).isNull();
    }

    @Test
    void extractPreferredUsername_ungueltigesFormat_liefertNullOhneException() {
        assertThat(logger.extractPreferredUsername("nicht-ein-jwt")).isNull();
        assertThat(logger.extractPreferredUsername("")).isNull();
        assertThat(logger.extractPreferredUsername("a.b")).isNull();
    }

    @Test
    void extractPreferredUsername_payloadBrauchtBase64UrlPadding_wirdKorrektDekodiert() {
        // "x" als Wert erzeugt bewusst eine Payload-Laenge, die ungepolstert nicht durch 4 teilbar
        // ist - deckt die padBase64Url()-Logik ab.
        String jwt = fakeJwt("{\"preferred_username\":\"x\"}");
        assertThat(logger.extractPreferredUsername(jwt)).isEqualTo("x");
    }


    @Test
    void failureReason_eigeneMessageVorhanden_wirdVerwendet() {
        Exception e = new RuntimeException("eigene Meldung");
        assertThat(logger.failureReason(e)).isEqualTo("eigene Meldung");
    }

    @Test
    void failureReason_keineEigeneMessageAberCause_liefertCauseMessage() {
        Exception cause = new RuntimeException("The JWT is no longer valid.");
        Exception e = new RuntimeException(null, cause);
        assertThat(logger.failureReason(e)).isEqualTo("The JWT is no longer valid.");
    }

    @Test
    void failureReason_wederMessageNochCause_liefertToString() {
        Exception e = new RuntimeException() {
            @Override
            public String toString() {
                return "custom-toString";
            }
        };
        assertThat(logger.failureReason(e)).isEqualTo("custom-toString");
    }
}
