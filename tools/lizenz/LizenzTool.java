import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Stellt KonfPlan-Offline-Lizenzen aus (siehe backend/src/main/asciidoc/Serverlizensierung.adoc).
 * Einzelne Quelldatei ohne Abhaengigkeiten:
 *
 *   java tools/lizenz/LizenzTool.java keygen --key signing.key --kid 2026-1
 *   java tools/lizenz/LizenzTool.java ausstellen --key signing.key --kid 2026-1 \
 *       --anforderung anforderung.json --kunde "Schule X" --lizenz-id LIC-2026-001 \
 *       --tage 365 --max-instanzen 1 --features planung --out license.lic
 *
 * ACHTUNG: Der Signaturschluessel liegt hier in einer Datei. Das ist nur fuer Tests und eine erste
 * Uebergangslizenz gedacht - fuer den Produktivbetrieb gehoert der Schluessel in ein KMS/HSM (ein
 * Leak dieser Datei erlaubt unbegrenzt gefaelschte Lizenzen).
 */
public final class LizenzTool {

    private static final String AUSSTELLER = "konfplan-lizenzserver";
    private static final String ZIELGRUPPE = "konfplan";
    private static final int MAX_TAGE = 3660;

    private LizenzTool() {
    }

    public static void main(String[] args) {
        try {
            if (args.length < 1) {
                throw new Fehler("Aufruf: keygen|ausstellen [Optionen] - siehe Kopfkommentar von LizenzTool.java");
            }
            Map<String, String> opt = optionen(Arrays.copyOfRange(args, 1, args.length));
            switch (args[0]) {
                case "keygen" -> keygen(opt);
                case "ausstellen" -> ausstellen(opt);
                default -> throw new Fehler("Unbekanntes Kommando: " + args[0]);
            }
        } catch (Fehler e) {
            System.err.println("Fehler: " + e.getMessage());
            System.exit(1);
        } catch (IOException | GeneralSecurityException e) {
            System.err.println("Fehler: " + e);
            System.exit(1);
        }
    }

    private static void keygen(Map<String, String> opt) throws IOException, GeneralSecurityException {
        Path keyFile = Path.of(pflicht(opt, "key"));
        String kid = kid(pflicht(opt, "kid"));
        if (Files.exists(keyFile)) {
            throw new Fehler("Schluesseldatei existiert bereits und wird nicht ueberschrieben: " + keyFile);
        }

        KeyPair kp = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        // Datei zuerst mit Besitzer-Rechten anlegen, dann erst den Schluessel schreiben.
        Files.createFile(keyFile, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
        Files.writeString(keyFile, Base64.getEncoder().encodeToString(kp.getPrivate().getEncoded()) + "\n", StandardOpenOption.WRITE);

        System.out.println("Privater Schluessel geschrieben: " + keyFile + " (Rechte 600) - sicher aufbewahren, niemals einchecken!");
        System.out.println();
        System.out.println("Eintrag fuer backend/src/main/resources/license/public-keys.properties:");
        System.out.println(kid + "=" + Base64.getEncoder().encodeToString(kp.getPublic().getEncoded()));
    }

    private static void ausstellen(Map<String, String> opt) throws IOException, GeneralSecurityException {
        Path keyFile = Path.of(pflicht(opt, "key"));
        String kid = kid(pflicht(opt, "kid"));
        String kunde = nichtLeer(pflicht(opt, "kunde"), "kunde");
        String lizenzId = nichtLeer(pflicht(opt, "lizenz-id"), "lizenz-id");
        int tage = zahl(pflicht(opt, "tage"), "tage", 1, MAX_TAGE);
        int maxInstanzen = zahl(opt.getOrDefault("max-instanzen", "1"), "max-instanzen", 1, 1_000_000);
        List<String> features = opt.containsKey("features")
            ? Arrays.stream(opt.get("features").split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList()
            : List.of();
        Path out = Path.of(pflicht(opt, "out"));
        Path audit = Path.of(opt.getOrDefault("audit", "lizenz-audit.log"));

        pruefeDateirechte(keyFile);
        PrivateKey key = KeyFactory.getInstance("Ed25519").generatePrivate(
            new PKCS8EncodedKeySpec(Base64.getDecoder().decode(Files.readString(keyFile).trim())));

        // Anforderungsdatei: Der Thumbprint wird aus dem Public Key neu berechnet und mit dem
        // angegebenen verglichen - eine veraenderte oder falsch kopierte Datei fuehrt so nicht zu einer
        // Lizenz, die die Instanz hinterher ablehnt.
        String anforderung = Files.readString(Path.of(pflicht(opt, "anforderung")));
        String instanzId = jsonFeld(anforderung, "instanzId");
        String publicKeyB64 = jsonFeld(anforderung, "publicKey");
        PublicKey instanzKey = KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(publicKeyB64)));
        String jkt = thumbprint(instanzKey);
        if (!jkt.equals(jsonFeld(anforderung, "thumbprint"))) {
            throw new Fehler("Der Thumbprint in der Anforderungsdatei passt nicht zum Public Key - Datei beschaedigt oder veraendert.");
        }

        Instant jetzt = Instant.now();
        Instant ablauf = jetzt.plus(Duration.ofDays(tage));
        String header = "{\"alg\":\"EdDSA\",\"kid\":" + str(kid) + "}";
        String payload = "{\"iss\":" + str(AUSSTELLER)
            + ",\"aud\":" + str(ZIELGRUPPE)
            + ",\"sub\":" + str(lizenzId)
            + ",\"customerId\":" + str(kunde)
            + ",\"maxInstances\":" + maxInstanzen
            + ",\"features\":[" + String.join(",", features.stream().map(LizenzTool::str).toList()) + "]"
            + ",\"type\":\"offline\""
            + ",\"cnf\":{\"jkt\":" + str(jkt) + "}"
            + ",\"iat\":" + jetzt.getEpochSecond()
            + ",\"nbf\":" + jetzt.getEpochSecond()
            + ",\"exp\":" + ablauf.getEpochSecond() + "}";

        String signiert = b64(header.getBytes(StandardCharsets.UTF_8)) + "." + b64(payload.getBytes(StandardCharsets.UTF_8));
        Signature sig = Signature.getInstance("Ed25519");
        sig.initSign(key);
        sig.update(signiert.getBytes(StandardCharsets.US_ASCII));
        String lizenz = signiert + "." + b64(sig.sign());

        Files.writeString(out, lizenz + "\n");
        Files.writeString(audit,
            jetzt + "\tlizenz=" + lizenzId + "\tkunde=" + kunde + "\tinstanz=" + instanzId + "\tjkt=" + jkt
                + "\tkid=" + kid + "\tmaxInstanzen=" + maxInstanzen + "\tbis=" + ablauf + "\n",
            StandardOpenOption.CREATE, StandardOpenOption.APPEND);

        System.out.println("Lizenz " + lizenzId + " fuer " + kunde + " (Instanz " + instanzId + ") gueltig bis " + ablauf + " geschrieben: " + out);
        System.out.println("Ausstellung protokolliert in: " + audit);
    }

    /** RFC 7638: SHA-256 ueber das kanonische JWK {"crv","kty","x"}, base64url ohne Padding. */
    static String thumbprint(PublicKey publicKey) throws GeneralSecurityException {
        byte[] spki = publicKey.getEncoded();
        if (44 != spki.length) {
            throw new Fehler("Kein Ed25519-Public-Key.");
        }
        byte[] x = Arrays.copyOfRange(spki, 12, 44);
        String jwk = "{\"crv\":\"Ed25519\",\"kty\":\"OKP\",\"x\":\"" + b64(x) + "\"}";
        return b64(MessageDigest.getInstance("SHA-256").digest(jwk.getBytes(StandardCharsets.UTF_8)));
    }

    private static void pruefeDateirechte(Path keyFile) throws IOException {
        if (!Files.isRegularFile(keyFile)) {
            throw new Fehler("Schluesseldatei nicht gefunden: " + keyFile);
        }
        var rechte = Files.getPosixFilePermissions(keyFile);
        if (rechte.contains(PosixFilePermission.GROUP_READ) || rechte.contains(PosixFilePermission.OTHERS_READ)
            || rechte.contains(PosixFilePermission.GROUP_WRITE) || rechte.contains(PosixFilePermission.OTHERS_WRITE)) {
            throw new Fehler("Die Schluesseldatei ist fuer andere Nutzer zugaenglich - bitte 'chmod 600 " + keyFile + "'.");
        }
    }

    /** Liest ein String-Feld aus der flachen Anforderungsdatei (Werte enthalten keine Escape-Sequenzen). */
    private static String jsonFeld(String json, String name) {
        Matcher m = Pattern.compile("\"" + Pattern.quote(name) + "\"\\s*:\\s*\"([^\"\\\\]*)\"").matcher(json);
        if (!m.find()) {
            throw new Fehler("Die Anforderungsdatei enthaelt kein Feld '" + name + "'.");
        }
        return m.group(1);
    }

    private static String str(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.append('"').toString();
    }

    private static String b64(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String kid(String kid) {
        if (!kid.matches("[A-Za-z0-9._-]+")) {
            throw new Fehler("kid darf nur Buchstaben, Ziffern, '.', '_' und '-' enthalten (sie ist Schluessel in public-keys.properties).");
        }
        return kid;
    }

    private static String nichtLeer(String wert, String name) {
        if (wert.isBlank()) {
            throw new Fehler("Option --" + name + " darf nicht leer sein.");
        }
        return wert;
    }

    private static int zahl(String wert, String name, int min, int max) {
        try {
            int n = Integer.parseInt(wert.trim());
            if (n < min || n > max) {
                throw new NumberFormatException();
            }
            return n;
        } catch (NumberFormatException e) {
            throw new Fehler("Option --" + name + " muss eine Zahl zwischen " + min + " und " + max + " sein.");
        }
    }

    private static String pflicht(Map<String, String> opt, String name) {
        String wert = opt.get(name);
        if (null == wert) {
            throw new Fehler("Option --" + name + " fehlt.");
        }
        return wert;
    }

    private static Map<String, String> optionen(String[] args) {
        Map<String, String> opt = new HashMap<>();
        List<String> rest = new ArrayList<>(Arrays.asList(args));
        while (!rest.isEmpty()) {
            String name = rest.remove(0);
            if (!name.startsWith("--") || rest.isEmpty()) {
                throw new Fehler("Ungueltige Option oder fehlender Wert bei: " + name);
            }
            opt.put(name.substring(2), rest.remove(0));
        }
        return opt;
    }

    private static final class Fehler extends RuntimeException {
        Fehler(String message) {
            super(message);
        }
    }
}
