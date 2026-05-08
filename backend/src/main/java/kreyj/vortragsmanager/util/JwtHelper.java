package kreyj.vortragsmanager.util;

import io.smallrye.jwt.build.Jwt;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.jetbrains.annotations.UnknownNullability;

import java.time.Duration;

public class JwtHelper {
    private static final String CLAIM_UPN = "upn";
    private static final String ISSUER = "https://konfplan.kreyj";  // from application.properties


    private JwtHelper() {
        // never instantiate
    }

    public static String getUserPrincipalName(@UnknownNullability JsonWebToken jwt) {
        return jwt.getClaim(CLAIM_UPN);
    }

    public static String tokenFor(String userEmail, String role) {
        return Jwt.issuer(ISSUER)
                .upn(userEmail)
                .subject(userEmail)
                .groups(role)
                .expiresIn(Duration.ofMinutes(3))
                .sign();
    }
}
