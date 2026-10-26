package kreyj.konfplan.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import kreyj.konfplan.domain.lizenz.InstanzSchluessel;
import kreyj.konfplan.domain.lizenz.LizenzServerClient;
import kreyj.konfplan.domain.lizenz.LizenzServerException;
import kreyj.konfplan.domain.lizenz.LizenzServerException.Art;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Spricht das Lizenzserver-Protokoll ueber HTTP(S). Klartext-HTTP ist nur zu Loopback-Adressen
 * erlaubt (Tests, lokale Entwicklung): der Lizenzschluessel wird bei der Aktivierung uebertragen.
 */
@ApplicationScoped
public class HttpLizenzServerClient implements LizenzServerClient {
    private static final Logger LOG = Logger.getLogger(HttpLizenzServerClient.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Set<String> LOOPBACK = Set.of("localhost", "127.0.0.1", "[::1]");

    private final URI basis;
    private final Duration timeout;
    private final HttpClient http;

    @Inject
    public HttpLizenzServerClient(@ConfigProperty(name = "konfplan.lizenz.server-url") Optional<String> serverUrl,
                                  @ConfigProperty(name = "konfplan.lizenz.server-timeout", defaultValue = "10s") Duration timeout) {
        this(serverUrl.orElse(null), timeout);
    }

    public HttpLizenzServerClient(String serverUrl, Duration timeout) {
        this.timeout = timeout;
        this.http = HttpClient.newBuilder().connectTimeout(timeout).followRedirects(HttpClient.Redirect.NEVER).build();
        this.basis = pruefe(serverUrl);
    }

    private static URI pruefe(String serverUrl) {
        if (null == serverUrl || serverUrl.isBlank()) {
            return null;
        }
        try {
            URI uri = URI.create(serverUrl.trim().endsWith("/") ? serverUrl.trim() : serverUrl.trim() + "/");
            boolean https = "https".equalsIgnoreCase(uri.getScheme());
            boolean loopbackHttp = "http".equalsIgnoreCase(uri.getScheme()) && null != uri.getHost() && LOOPBACK.contains(uri.getHost().toLowerCase());
            if (!https && !loopbackHttp) {
                LOG.warn("konfplan.lizenz.server-url muss https:// verwenden (http:// nur für localhost) - Lizenzserver wird nicht genutzt.");
                return null;
            }
            return uri;
        } catch (IllegalArgumentException e) {
            LOG.warn("konfplan.lizenz.server-url ist keine gültige URL - Lizenzserver wird nicht genutzt.");
            return null;
        }
    }

    @Override
    public boolean konfiguriert() {
        return null != basis;
    }

    @Override
    public String aktivieren(Identitaet instanz, String lizenzschluessel) {
        JsonNode antwort = senden("api/v1/aktivierung", instanz, Map.of(
            "lizenzschluessel", lizenzschluessel,
            "instanzId", instanz.instanzId(),
            "publicKey", instanz.publicKey(),
            "version", instanz.version()));
        return token(antwort);
    }

    @Override
    public String revalidieren(Identitaet instanz, String token) {
        return token(senden("api/v1/revalidierung", instanz, Map.of(
            "instanzId", instanz.instanzId(),
            "token", token,
            "version", instanz.version())));
    }

    @Override
    public void deaktivieren(Identitaet instanz, String token) {
        senden("api/v1/deaktivierung", instanz, Map.of("instanzId", instanz.instanzId(), "token", token));
    }

    private static String token(JsonNode antwort) {
        String token = antwort.path("token").asText(null);
        if (null == token || token.isBlank()) {
            throw new LizenzServerException(Art.UNGUELTIGE_ANTWORT, null, "Der Lizenzserver hat keine Lizenz geliefert.");
        }
        return token;
    }

    private JsonNode senden(String pfad, Identitaet instanz, Map<String, Object> body) {
        if (null == basis) {
            throw new LizenzServerException(Art.ABGELEHNT, "KEIN_SERVER", "Es ist kein Lizenzserver konfiguriert.");
        }
        try {
            byte[] bytes = MAPPER.writeValueAsBytes(body);
            HttpRequest request = HttpRequest.newBuilder(basis.resolve(pfad))
                .timeout(timeout)
                .header("Content-Type", "application/json")
                .header("X-Instanz-Id", instanz.instanzId())
                .header("X-Signatur", InstanzSchluessel.signiere(instanz.privateKey(), bytes))
                .POST(HttpRequest.BodyPublishers.ofByteArray(bytes))
                .build();
            HttpResponse<byte[]> response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
            return auswerten(response.statusCode(), response.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LizenzServerException(Art.NICHT_ERREICHBAR, "Die Anfrage an den Lizenzserver wurde unterbrochen.", e);
        } catch (IOException e) {
            throw new LizenzServerException(Art.NICHT_ERREICHBAR, "Der Lizenzserver ist nicht erreichbar.", e);
        }
    }

    private static JsonNode auswerten(int status, byte[] body) {
        if (status >= 200 && status < 300) {
            try {
                return 0 == body.length ? MAPPER.createObjectNode() : MAPPER.readTree(body);
            } catch (IOException e) {
                throw new LizenzServerException(Art.UNGUELTIGE_ANTWORT, "Die Antwort des Lizenzservers ist nicht lesbar.", e);
            }
        }
        if (status >= 500) {
            throw new LizenzServerException(Art.NICHT_ERREICHBAR, null, "Der Lizenzserver meldet einen Fehler (HTTP " + status + ").");
        }
        String fehler = "Der Lizenzserver hat die Anfrage abgelehnt (HTTP " + status + ").";
        String code = null;
        try {
            JsonNode n = MAPPER.readTree(body);
            if (n.path("error").isTextual()) {
                fehler = n.path("error").asText();
            }
            if (n.path("code").isTextual()) {
                code = n.path("code").asText();
            }
        } catch (IOException ignored) {
            // Kein JSON-Body - die allgemeine Meldung oben genuegt.
        }
        throw new LizenzServerException(Art.ABGELEHNT, code, fehler);
    }
}
