package kreyj.konfplan.domain.lizenz;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.PublicKey;
import java.security.Signature;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Minimaler Lizenzserver fuer Tests - bildet das Protokoll aus Serverlizensierung.adoc nach:
 * signierte Anfragen (Header X-Signatur, geprueft gegen den bei der Aktivierung registrierten Public
 * Key), Aktivierungs-Limit pro Lizenz, Revalidierungszaehler mit Klon-Erkennung. Dient zugleich als
 * ausfuehrbare Referenz fuer den spaeteren echten Lizenzserver.
 */
public class FakeLizenzServer implements AutoCloseable {

    public record Lizenz(String lizenzId, String kunde, int maxInstanzen) {
    }

    private static final class Aktivierung {
        final String lizenzschluessel;
        final String publicKeyBase64;
        final PublicKey publicKey;
        long zaehler;
        String letzterToken;

        Aktivierung(String lizenzschluessel, String publicKeyBase64, PublicKey publicKey) {
            this.lizenzschluessel = lizenzschluessel;
            this.publicKeyBase64 = publicKeyBase64;
            this.publicKey = publicKey;
        }
    }

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final HttpServer server;
    private final Supplier<Instant> jetzt;
    private final Map<String, Lizenz> lizenzen = new ConcurrentHashMap<>();
    private final Set<String> widerrufen = ConcurrentHashMap.newKeySet();
    private final Map<String, Aktivierung> aktivierungen = new ConcurrentHashMap<>();

    public volatile Duration revalidierungsintervall = Duration.ofDays(7);
    public volatile Duration gracePeriode = Duration.ofDays(14);
    /** Wenn gesetzt, liefert die naechste Revalidierung statt einer neuen Lizenz genau diese (z.B. eine alte). */
    public volatile String erzwungeneAntwort;

    public FakeLizenzServer(Supplier<Instant> jetzt) {
        this.jetzt = jetzt;
        try {
            server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        server.createContext("/api/v1/aktivierung", (ex) -> bearbeite(ex, this::aktivierung));
        server.createContext("/api/v1/revalidierung", (ex) -> bearbeite(ex, this::revalidierung));
        server.createContext("/api/v1/deaktivierung", (ex) -> bearbeite(ex, this::deaktivierung));
        server.start();
    }

    public String url() {
        return "http://localhost:" + server.getAddress().getPort();
    }

    public void lizenz(String lizenzschluessel, Lizenz lizenz) {
        lizenzen.put(lizenzschluessel, lizenz);
    }

    public void widerrufe(String lizenzschluessel) {
        widerrufen.add(lizenzschluessel);
    }

    public long anzahlAktivierungen(String lizenzschluessel) {
        return aktivierungen.values().stream().filter((a) -> a.lizenzschluessel.equals(lizenzschluessel)).count();
    }

    public Long zaehler(String instanzId) {
        Aktivierung a = aktivierungen.get(instanzId);
        return null == a ? null : a.zaehler;
    }

    /** Registriert eine fremde Instanz, ohne dass sie sich aktiviert hat (belegt einen Platz). */
    public void registriere(String lizenzschluessel, String instanzId, PublicKey key) {
        aktivierungen.put(instanzId, new Aktivierung(lizenzschluessel, Base64.getEncoder().encodeToString(key.getEncoded()), key));
    }

    @Override
    public void close() {
        server.stop(0);
    }

    // ---- Protokoll ----

    private interface Handler {
        Antwort handle(HttpExchange ex, byte[] body, JsonNode json) throws Exception;
    }

    private record Antwort(int status, Object body) {
    }

    private static Antwort fehler(int status, String code, String meldung) {
        return new Antwort(status, Map.of("error", meldung, "code", code));
    }

    private void bearbeite(HttpExchange ex, Handler handler) throws IOException {
        Antwort antwort;
        try {
            byte[] body = ex.getRequestBody().readAllBytes();
            antwort = handler.handle(ex, body, MAPPER.readTree(body));
        } catch (Exception e) {
            antwort = new Antwort(500, Map.of("error", String.valueOf(e)));
        }
        byte[] bytes = null == antwort.body() ? new byte[0] : MAPPER.writeValueAsBytes(antwort.body());
        ex.getResponseHeaders().add("Content-Type", "application/json");
        ex.sendResponseHeaders(antwort.status(), 0 == bytes.length ? -1 : bytes.length);
        if (bytes.length > 0) {
            ex.getResponseBody().write(bytes);
        }
        ex.close();
    }

    private static boolean signaturGueltig(HttpExchange ex, byte[] body, PublicKey key) {
        try {
            String signatur = ex.getRequestHeaders().getFirst("X-Signatur");
            if (null == signatur) {
                return false;
            }
            Signature sig = Signature.getInstance("Ed25519");
            sig.initVerify(key);
            sig.update(body);
            return sig.verify(Base64.getUrlDecoder().decode(signatur));
        } catch (Exception e) {
            return false;
        }
    }

    private Antwort aktivierung(HttpExchange ex, byte[] body, JsonNode json) {
        String instanzId = json.path("instanzId").asText();
        String schluessel = json.path("lizenzschluessel").asText();
        String publicKeyB64 = json.path("publicKey").asText();
        PublicKey key = InstanzSchluessel.publicKeyAusBase64(publicKeyB64);

        // Besitznachweis: die Anfrage muss mit dem Private Key zum mitgesendeten Public Key signiert sein.
        if (!instanzId.equals(ex.getRequestHeaders().getFirst("X-Instanz-Id")) || !signaturGueltig(ex, body, key)) {
            return fehler(401, "SIGNATUR", "Ungültige Signatur.");
        }
        Lizenz lizenz = lizenzen.get(schluessel);
        if (null == lizenz) {
            return fehler(404, "UNBEKANNT", "Unbekannter Lizenzschlüssel.");
        }
        if (widerrufen.contains(schluessel)) {
            return fehler(410, "WIDERRUFEN", "Die Lizenz wurde widerrufen.");
        }
        long belegt = aktivierungen.entrySet().stream()
            .filter((e) -> e.getValue().lizenzschluessel.equals(schluessel) && !e.getKey().equals(instanzId)).count();
        if (belegt >= lizenz.maxInstanzen()) {
            return fehler(403, "LIMIT_ERREICHT", "Alle " + lizenz.maxInstanzen() + " Aktivierungen dieser Lizenz sind belegt.");
        }

        Aktivierung a = aktivierungen.computeIfAbsent(instanzId, (id) -> new Aktivierung(schluessel, publicKeyB64, key));
        a.zaehler++;
        a.letzterToken = token(lizenz, a);
        return new Antwort(200, Map.of("token", a.letzterToken));
    }

    private Antwort revalidierung(HttpExchange ex, byte[] body, JsonNode json) throws IOException {
        Aktivierung a = aktivierungen.get(json.path("instanzId").asText());
        if (null == a) {
            return fehler(404, "UNBEKANNT", "Diese Instanz ist nicht aktiviert.");
        }
        if (!signaturGueltig(ex, body, a.publicKey)) {
            return fehler(401, "SIGNATUR", "Ungültige Signatur.");
        }
        if (widerrufen.contains(a.lizenzschluessel)) {
            return fehler(410, "WIDERRUFEN", "Die Lizenz wurde widerrufen.");
        }
        long eingereicht = seq(json.path("token").asText());
        if (eingereicht == a.zaehler - 1 && null != a.letzterToken) {
            // Wiederholung nach verlorener Antwort: dieselbe Lizenz noch einmal ausliefern.
            return new Antwort(200, Map.of("token", a.letzterToken));
        }
        if (eingereicht != a.zaehler) {
            return fehler(409, "KLON_ERKANNT", "Die Lizenz wurde bereits von einer anderen Kopie dieser Instanz erneuert.");
        }
        a.zaehler++;
        a.letzterToken = token(lizenzen.get(a.lizenzschluessel), a);
        return new Antwort(200, Map.of("token", null != erzwungeneAntwort ? erzwungeneAntwort : a.letzterToken));
    }

    private Antwort deaktivierung(HttpExchange ex, byte[] body, JsonNode json) {
        String instanzId = json.path("instanzId").asText();
        Aktivierung a = aktivierungen.get(instanzId);
        if (null == a) {
            return fehler(404, "UNBEKANNT", "Diese Instanz ist nicht aktiviert.");
        }
        if (!signaturGueltig(ex, body, a.publicKey)) {
            return fehler(401, "SIGNATUR", "Ungültige Signatur.");
        }
        aktivierungen.remove(instanzId);
        return new Antwort(204, null);
    }

    private static long seq(String token) throws IOException {
        String[] teile = token.split("\\.");
        return MAPPER.readTree(Base64.getUrlDecoder().decode(teile[1])).path("seq").asLong(-1);
    }

    private String token(Lizenz lizenz, Aktivierung a) {
        Instant jetzt = this.jetzt.get();
        String jkt = InstanzSchluessel.thumbprint(InstanzSchluessel.publicKeyAusBase64(a.publicKeyBase64));
        Instant rev = jetzt.plus(revalidierungsintervall);
        Map<String, Object> c = new LinkedHashMap<>();
        c.put("iss", LizenzVerifier.AUSSTELLER);
        c.put("aud", LizenzVerifier.ZIELGRUPPE);
        c.put("sub", lizenz.lizenzId());
        c.put("customerId", lizenz.kunde());
        c.put("maxInstances", lizenz.maxInstanzen());
        c.put("features", java.util.List.of("planung"));
        c.put("type", "online");
        c.put("cnf", Map.of("jkt", jkt));
        c.put("seq", a.zaehler);
        c.put("iat", jetzt.getEpochSecond());
        c.put("nbf", jetzt.getEpochSecond());
        c.put("rev", rev.getEpochSecond());
        c.put("exp", rev.plus(gracePeriode).getEpochSecond());
        return LizenzTestSupport.token(LizenzTestSupport.PRIVATE_1, LizenzTestSupport.header(), c);
    }
}
