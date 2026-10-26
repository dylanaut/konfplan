package kreyj.konfplan.domain.lizenz;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/** Baut Test-Lizenzen. Die Schluessel gehoeren nur zu den Test-Public-Keys, nie zu einem echten Aussteller. */
public final class LizenzTestSupport {

    public static final String KID = "test-1";
    public static final String PUBLIC_1 = "MCowBQYDK2VwAyEA54KDjNRzLu9RLGgn57FhhmvRZKP51gUj8MA/3Q74a0s=";
    public static final String PRIVATE_1 = "MC4CAQAwBQYDK2VwBCIEIKgNRcBfJLDoVCydcujCncMLGbYR0/qoYWMlydMakm0P";
    /** Zweites Paar, das dem Verifier unbekannt ist. */
    public static final String PRIVATE_2 = "MC4CAQAwBQYDK2VwBCIEICYsy7S4FJD25UvpTadKLYhrOwhXZQtegMMChxC/O4uY";

    private LizenzTestSupport() {
    }

    public static PublicKey public1() {
        return InstanzSchluessel.publicKeyAusBase64(PUBLIC_1);
    }

    /** Gueltige Standard-Claims fuer die uebergebene Instanz; Zeiten in Epoch-Sekunden relativ zu {@code jetzt}. */
    public static Map<String, Object> claims(String jkt, long jetzt) {
        Map<String, Object> c = new LinkedHashMap<>();
        c.put("iss", LizenzVerifier.AUSSTELLER);
        c.put("aud", LizenzVerifier.ZIELGRUPPE);
        c.put("sub", "LIC-1");
        c.put("customerId", "CUST-1");
        c.put("maxInstances", 3);
        c.put("features", java.util.List.of("planung"));
        c.put("type", "offline");
        c.put("cnf", Map.of("jkt", jkt));
        c.put("iat", jetzt - 60);
        c.put("nbf", jetzt - 60);
        c.put("exp", jetzt + 86400);
        return c;
    }

    public static String token(String privateKey, Map<String, Object> header, Map<String, Object> claims) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper m = new com.fasterxml.jackson.databind.ObjectMapper();
            String h = b64(m.writeValueAsBytes(header));
            String p = b64(m.writeValueAsBytes(claims));
            PrivateKey key = KeyFactory.getInstance("Ed25519").generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(privateKey)));
            Signature sig = Signature.getInstance("Ed25519");
            sig.initSign(key);
            sig.update((h + "." + p).getBytes(StandardCharsets.US_ASCII));
            return h + "." + p + "." + b64(sig.sign());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public static Map<String, Object> header() {
        Map<String, Object> h = new LinkedHashMap<>();
        h.put("alg", "EdDSA");
        h.put("kid", KID);
        return h;
    }

    public static String b64(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
