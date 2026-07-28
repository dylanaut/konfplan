package kreyj.konfplan.util;

import io.smallrye.jwt.build.Jwt;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.time.Duration;

public final class JwtHelper {
    private static final String CLAIM_UPN = "upn";
    private static final String ISSUER = "https://konfplan.kreyj";  // from application.properties


    private JwtHelper() {
        // never instantiate
    }

    public static String getUserPrincipalName(JsonWebToken jwt) {
        return jwt.getClaim(CLAIM_UPN);
    }

    public static String tokenFor(String loginName, String role) {
        return Jwt.issuer(ISSUER)
                .upn(loginName)
                .subject(loginName)
                .groups(role)
                .expiresIn(Duration.ofMinutes(3))
                .sign();
    }
}
