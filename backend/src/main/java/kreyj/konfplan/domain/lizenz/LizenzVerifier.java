package kreyj.konfplan.domain.lizenz;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import kreyj.konfplan.domain.lizenz.LizenzFehler.Grund;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.PublicKey;
import java.security.Signature;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/**
 * Prueft eine Lizenz im JWS-Compact-Format (RFC 7515/8037, Algorithmus EdDSA). Bewusst ohne
 * JWT-Bibliothek und ohne Auth-Mechanismus: der Algorithmus ist fest vorgegeben (kein "none",
 * keine symmetrischen Verfahren), die Signatur wird vor dem Lesen der Claims geprueft.
 *
 * Reihenfolge der Pruefungen: Format, Header (alg/kid), Signatur, dann Claims.
 */
public class LizenzVerifier {

    public static final String AUSSTELLER = "konfplan-lizenzserver";
    public static final String ZIELGRUPPE = "konfplan";

    private static final String ALGORITHMUS = "EdDSA";
    private static final Duration TOLERANZ = Duration.ofSeconds(60);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Map<String, PublicKey> schluessel;
    private final Set<String> gesperrteKids;
    private final Clock clock;

    public LizenzVerifier(Map<String, PublicKey> schluessel, Set<String> gesperrteKids, Clock clock) {
        this.schluessel = Map.copyOf(schluessel);
        this.gesperrteKids = Set.copyOf(gesperrteKids);
        this.clock = clock;
    }

    /**
     * Laedt die Public Keys aus einer Classpath-Properties-Datei: {@code <kid>=<Base64 X.509-SPKI>}
     * je Schluessel, optional {@code gesperrt=<kid>,<kid>}. Fehlt die Datei, ist kein Schluessel
     * bekannt und jede Lizenz wird abgelehnt.
     */
    public static LizenzVerifier ausClasspath(String resource, Clock clock) {
        Properties props = new Properties();
        try (InputStream in = LizenzVerifier.class.getClassLoader().getResourceAsStream(resource)) {
            if (null != in) {
                props.load(in);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Lizenz-Schlüssel nicht lesbar: " + resource, e);
        }
        Map<String, PublicKey> keys = new HashMap<>();
        Set<String> gesperrt = new HashSet<>();
        for (String name : props.stringPropertyNames()) {
            String wert = props.getProperty(name).trim();
            if ("gesperrt".equals(name)) {
                Arrays.stream(wert.split(",")).map(String::trim).filter(s -> !s.isEmpty()).forEach(gesperrt::add);
            } else {
                keys.put(name, InstanzSchluessel.publicKeyAusBase64(wert));
            }
        }
        return new LizenzVerifier(keys, gesperrt, clock);
    }

    /**
     * @param token       die Lizenz als JWS Compact Serialization
     * @param instanzJkt  Thumbprint des Instanz-Public-Keys; muss dem Claim cnf.jkt entsprechen
     * @throws LizenzFehler wenn die Lizenz aus irgendeinem Grund nicht akzeptiert werden darf
     */
    public LizenzClaims verifiziere(String token, String instanzJkt) {
        if (null == token) {
            throw new LizenzFehler(Grund.FORMAT, "Keine Lizenz übergeben.");
        }
        String[] teile = token.trim().split("\\.", -1);
        if (3 != teile.length) {
            throw new LizenzFehler(Grund.FORMAT, "Die Lizenz hat kein gültiges JWS-Format.");
        }

        JsonNode header = parse(teile[0]);
        if (header.has("crit")) {
            throw new LizenzFehler(Grund.FORMAT, "Kritische Header-Parameter werden nicht unterstützt.");
        }
        if (!ALGORITHMUS.equals(header.path("alg").asText(null))) {
            throw new LizenzFehler(Grund.ALGORITHMUS, "Nur der Signaturalgorithmus " + ALGORITHMUS + " wird akzeptiert.");
        }
        String kid = header.path("kid").asText(null);
        if (null == kid || kid.isEmpty()) {
            throw new LizenzFehler(Grund.UNVOLLSTAENDIG, "Der Lizenz-Header enthält keine kid.");
        }
        if (gesperrteKids.contains(kid)) {
            throw new LizenzFehler(Grund.GESPERRTER_SCHLUESSEL, "Der Signaturschlüssel " + kid + " ist gesperrt.");
        }
        PublicKey key = schluessel.get(kid);
        if (null == key) {
            throw new LizenzFehler(Grund.UNBEKANNTER_SCHLUESSEL, "Der Signaturschlüssel " + kid + " ist unbekannt.");
        }

        pruefeSignatur(key, teile[0] + "." + teile[1], teile[2]);

        return pruefeClaims(parse(teile[1]), kid, instanzJkt);
    }

    private LizenzClaims pruefeClaims(JsonNode c, String kid, String instanzJkt) {
        if (!AUSSTELLER.equals(c.path("iss").asText(null))) {
            throw new LizenzFehler(Grund.AUSSTELLER, "Die Lizenz stammt nicht vom erwarteten Aussteller.");
        }
        if (!hatZielgruppe(c.path("aud"))) {
            throw new LizenzFehler(Grund.ZIELGRUPPE, "Die Lizenz ist nicht für KonfPlan ausgestellt.");
        }

        String lizenzId = text(c, "sub");
        String kundenId = text(c, "customerId");
        String jkt = text(c.path("cnf"), "jkt");
        if (!c.path("maxInstances").isInt() || 1 > c.path("maxInstances").asInt()) {
            throw new LizenzFehler(Grund.UNVOLLSTAENDIG, "Claim maxInstances fehlt oder ist ungültig.");
        }
        LizenzClaims.Typ typ = switch (c.path("type").asText("")) {
            case "online" -> LizenzClaims.Typ.ONLINE;
            case "offline" -> LizenzClaims.Typ.OFFLINE;
            default -> throw new LizenzFehler(Grund.TYP, "Unbekannter Lizenztyp.");
        };
        Instant gueltigBis = zeitpunkt(c, "exp", true);
        Instant gueltigAb = zeitpunkt(c, "nbf", false);
        Instant ausgestelltAm = zeitpunkt(c, "iat", true);

        // Online-Lizenzen tragen zusaetzlich den Revalidierungszaehler und den Zeitpunkt der
        // naechsten faelligen Revalidierung; exp ist dann das Ende der Grace-Period.
        boolean online = LizenzClaims.Typ.ONLINE == typ;
        Long zaehler = null;
        Instant naechsteRevalidierung = null;
        if (online) {
            if (!c.path("seq").isIntegralNumber() || 0 > c.path("seq").asLong()) {
                throw new LizenzFehler(Grund.UNVOLLSTAENDIG, "Claim seq fehlt oder ist ungültig.");
            }
            zaehler = c.path("seq").asLong();
            naechsteRevalidierung = zeitpunkt(c, "rev", true);
            if (naechsteRevalidierung.isAfter(gueltigBis)) {
                throw new LizenzFehler(Grund.UNVOLLSTAENDIG, "Claim rev liegt nach exp.");
            }
        }

        Set<String> features = new HashSet<>();
        JsonNode featuresNode = c.path("features");
        if (featuresNode.isArray()) {
            featuresNode.forEach(f -> features.add(f.asText()));
        } else if (!featuresNode.isMissingNode()) {
            throw new LizenzFehler(Grund.UNVOLLSTAENDIG, "Claim features muss eine Liste sein.");
        }

        if (!jkt.equals(instanzJkt)) {
            throw new LizenzFehler(Grund.INSTANZ, "Die Lizenz ist an eine andere Instanz gebunden.");
        }
        Instant jetzt = clock.instant();
        if (jetzt.isAfter(gueltigBis.plus(TOLERANZ))) {
            throw new LizenzFehler(Grund.ABGELAUFEN, "Die Lizenz ist abgelaufen.");
        }
        if (null != gueltigAb && jetzt.isBefore(gueltigAb.minus(TOLERANZ))) {
            throw new LizenzFehler(Grund.NOCH_NICHT_GUELTIG, "Die Lizenz ist noch nicht gültig.");
        }

        return new LizenzClaims(lizenzId, kundenId, c.path("maxInstances").asInt(), Set.copyOf(features), typ, kid, ausgestelltAm, gueltigAb, gueltigBis, zaehler, naechsteRevalidierung);
    }

    private static void pruefeSignatur(PublicKey key, String signiert, String signatur) {
        try {
            Signature sig = Signature.getInstance("Ed25519");
            sig.initVerify(key);
            sig.update(signiert.getBytes(StandardCharsets.US_ASCII));
            if (!sig.verify(Base64.getUrlDecoder().decode(signatur))) {
                throw new LizenzFehler(Grund.SIGNATUR, "Die Lizenzsignatur ist ungültig.");
            }
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new LizenzFehler(Grund.SIGNATUR, "Die Lizenzsignatur ist ungültig.");
        }
    }

    private static boolean hatZielgruppe(JsonNode aud) {
        if (aud.isTextual()) {
            return ZIELGRUPPE.equals(aud.asText());
        }
        if (aud.isArray()) {
            for (JsonNode a : aud) {
                if (ZIELGRUPPE.equals(a.asText())) {
                    return true;
                }
            }
        }
        return false;
    }

    private static JsonNode parse(String base64url) {
        try {
            JsonNode node = MAPPER.readTree(Base64.getUrlDecoder().decode(base64url));
            if (null == node || !node.isObject()) {
                throw new LizenzFehler(Grund.FORMAT, "Die Lizenz hat kein gültiges JWS-Format.");
            }
            return node;
        } catch (IllegalArgumentException | IOException e) {
            throw new LizenzFehler(Grund.FORMAT, "Die Lizenz hat kein gültiges JWS-Format.");
        }
    }

    private static String text(JsonNode node, String name) {
        String wert = node.path(name).asText(null);
        if (null == wert || wert.isEmpty()) {
            throw new LizenzFehler(Grund.UNVOLLSTAENDIG, "Claim " + name + " fehlt.");
        }
        return wert;
    }

    private static Instant zeitpunkt(JsonNode c, String name, boolean pflicht) {
        JsonNode n = c.path(name);
        if (n.isMissingNode() || n.isNull()) {
            if (pflicht) {
                throw new LizenzFehler(Grund.UNVOLLSTAENDIG, "Claim " + name + " fehlt.");
            }
            return null;
        }
        if (!n.isIntegralNumber()) {
            throw new LizenzFehler(Grund.UNVOLLSTAENDIG, "Claim " + name + " ist keine Zeitangabe.");
        }
        try {
            return Instant.ofEpochSecond(n.asLong());
        } catch (DateTimeException e) {
            throw new LizenzFehler(Grund.UNVOLLSTAENDIG, "Claim " + name + " ist keine Zeitangabe.");
        }
    }
}
