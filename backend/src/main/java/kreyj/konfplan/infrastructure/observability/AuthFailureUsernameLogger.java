package kreyj.konfplan.infrastructure.observability;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.logging.Log;
import io.quarkus.security.spi.runtime.AuthenticationFailureEvent;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import java.util.Base64;

/**
 * Quarkus' eigene OIDC-Verifizierung loggt bei einem abgelaufenen/ungueltigen JWT nur eine
 * generische WARN-Zeile ohne Nutzerbezug ("Verification of the token issued to client ... has
 * failed"). Dieser Observer ergaenzt den Login-Namen, indem er das (nicht verifizierte, aber
 * dennoch decodierbare - JWTs sind nur signiert, nicht verschluesselt) Bearer-Token direkt aus
 * dem Request liest und den preferred_username-Claim ausliest, unabhaengig davon, ob die
 * Verifizierung erfolgreich war. {@link AuthenticationFailureEvent} feuert generisch bei jedem
 * Authentifizierungs-Fehlschlag (siehe Quarkus' HttpAuthenticator), nicht OIDC-spezifisch - daher
 * kein Risiko, dass das Event fuer Token-Ablauf speziell ausbleibt.
 */
@ApplicationScoped
public class AuthFailureUsernameLogger {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String CLAIM_PREFERRED_USERNAME = "preferred_username";

    @Inject
    ObjectMapper objectMapper;

    void onFailure(@Observes AuthenticationFailureEvent event) {
        RoutingContext routingContext = (RoutingContext) event.getEventProperties().get(RoutingContext.class.getName());
        if (null == routingContext) {
            return;
        }

        String authorizationHeader = routingContext.request().getHeader(AUTHORIZATION_HEADER);
        if (null == authorizationHeader || !authorizationHeader.startsWith(BEARER_PREFIX)) {
            return;
        }

        String username = extractPreferredUsername(authorizationHeader.substring(BEARER_PREFIX.length()));
        if (null == username) {
            return;
        }

        Log.warnf("Authentifizierung fehlgeschlagen fuer Nutzer '%s': %s", username, failureReason(event.getAuthenticationFailure()));
    }

    // AuthenticationFailedException selbst hat i.d.R. keine eigene Message (siehe Live-Test) -
    // die eigentliche Ursache steckt in der Cause (z.B. Quarkus OIDCs eigene
    // "The JWT is no longer valid."), die hier zusaetzlich zur benachbarten OidcProvider-WARN-
    // Zeile gespiegelt wird, damit diese Zeile allein aussagekraeftig ist (wichtig bei
    // gleichzeitigen Requests mehrerer Nutzer, wo Log-Zeilen sich verschachteln koennen).
    String failureReason(Throwable failure) {
        if (null != failure.getMessage()) {
            return failure.getMessage();
        }
        Throwable cause = failure.getCause();
        return null == cause ? failure.toString() : cause.getMessage();
    }

    String extractPreferredUsername(String jwt) {
        String[] parts = jwt.split("\\.");
        if (parts.length < 2) {
            return null;
        }
        try {
            byte[] payloadBytes = Base64.getUrlDecoder().decode(padBase64Url(parts[1]));
            JsonNode payload = objectMapper.readTree(payloadBytes);
            JsonNode usernameNode = payload.get(CLAIM_PREFERRED_USERNAME);
            return null == usernameNode ? null : usernameNode.asText();
        } catch (Exception e) {
            return null;
        }
    }

    // Base64URL-Segmente in einem JWT sind ungepolstert - Javas Decoder verlangt aber eine auf 4
    // Zeichen aufgefuellte Laenge, sonst IllegalArgumentException.
    private String padBase64Url(String base64Url) {
        int paddingLength = (4 - base64Url.length() % 4) % 4;
        return base64Url + "=".repeat(paddingLength);
    }
}
