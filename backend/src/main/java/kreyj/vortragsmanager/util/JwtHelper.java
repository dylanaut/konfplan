package kreyj.vortragsmanager.util;

import org.eclipse.microprofile.jwt.JsonWebToken;
import org.jetbrains.annotations.UnknownNullability;

public class JwtHelper {

    public static final String CLAIM_UPN = "upn";

    private JwtHelper() {
        // never instantiate
    }

    public static String getUserPrincipalName(@UnknownNullability JsonWebToken jwt) {
        return jwt.getClaim(CLAIM_UPN);
    }
}
