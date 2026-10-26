package kreyj.konfplan.domain.lizenz;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.Base64;

/**
 * Ed25519-Schluesselpaar einer KonfPlan-Instanz. Die Lizenz bindet sich ueber den JWK-Thumbprint
 * (RFC 7638) des Public Keys an die Instanz (cnf.jkt, RFC 7800/9449).
 */
public final class InstanzSchluessel {

    private static final String ALGORITHMUS = "Ed25519";
    private static final int SPKI_LAENGE = 44;
    private static final int ROHSCHLUESSEL_LAENGE = 32;

    private InstanzSchluessel() {
    }

    public static KeyPair generiere() {
        try {
            return KeyPairGenerator.getInstance(ALGORITHMUS).generateKeyPair();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Ed25519 nicht verfügbar", e);
        }
    }

    public static PublicKey publicKeyAusBase64(String base64Spki) {
        try {
            return KeyFactory.getInstance(ALGORITHMUS).generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(base64Spki)));
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalArgumentException("Ungültiger Ed25519-Public-Key", e);
        }
    }

    /**
     * RFC 7638: SHA-256 ueber das kanonische JWK {"crv","kty","x"} (Member alphabetisch, ohne
     * Whitespace), base64url ohne Padding.
     */
    public static String thumbprint(PublicKey publicKey) {
        byte[] spki = publicKey.getEncoded();
        if (SPKI_LAENGE != spki.length) {
            throw new IllegalArgumentException("Kein Ed25519-Public-Key");
        }
        byte[] x = Arrays.copyOfRange(spki, SPKI_LAENGE - ROHSCHLUESSEL_LAENGE, SPKI_LAENGE);
        String jwk = "{\"crv\":\"Ed25519\",\"kty\":\"OKP\",\"x\":\"" + Base64.getUrlEncoder().withoutPadding().encodeToString(x) + "\"}";
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(jwk.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("SHA-256 nicht verfügbar", e);
        }
    }
}
