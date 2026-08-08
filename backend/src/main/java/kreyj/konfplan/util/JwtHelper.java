package kreyj.konfplan.util;

import org.eclipse.microprofile.jwt.JsonWebToken;

public final class JwtHelper {
    private static final String CLAIM_PREFERRED_USERNAME = "preferred_username";

    private JwtHelper() {
        // never instantiate
    }

    /**
     * Keycloak setzt {@code preferred_username} auf den Wert, mit dem der Keycloak-User
     * angelegt wurde - das ist bei uns immer {@code Nutzer.loginName} (siehe
     * {@link kreyj.konfplan.domain.service.KeycloakUserProvisioningService}).
     */
    public static String getUserPrincipalName(JsonWebToken jwt) {
        return jwt.getClaim(CLAIM_PREFERRED_USERNAME);
    }
}
