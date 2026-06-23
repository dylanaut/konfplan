package kreyj.konfplan.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.qute.TemplateExtension;
import io.quarkus.runtime.Startup;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

import java.io.InputStream;
import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;

@ApplicationScoped
@Startup
public class AssetService {
    private static final Logger LOG = Logger.getLogger(AssetService.class);
    private static Map<String, String> manifest = Collections.emptyMap();

    @PostConstruct
    void init() {
        try (InputStream is = getClass().getResourceAsStream("/META-INF/resources/manifest.json")) {
            if (is == null) {
                LOG.warn("manifest.json nicht gefunden. Asset-Pfade werden nicht aufgelöst. Führen Sie einen Frontend-Build durch.");
                return;
            }
            ObjectMapper mapper = new ObjectMapper();
            Map<String, ManifestEntry> parsedManifest = mapper.readValue(is, new TypeReference<>() {});

            // Transform the manifest into a simple map of originalName -> finalPath
            manifest = parsedManifest.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> "/" + e.getValue().file));

            LOG.info("Asset-Manifest erfolgreich geladen.");
        } catch (Exception e) {
            LOG.error("Fehler beim Laden des Asset-Manifests (manifest.json).", e);
        }
    }

    public static String getAssetPath(String originalName) {
        // Fallback, falls das Manifest nicht geladen werden konnte oder der Key nicht existiert
        return manifest.getOrDefault(originalName, originalName);
    }

    // Hilfsklasse, um die Struktur der manifest.json zu parsen
    private static class ManifestEntry {
        public String file;
        // Hier könnten weitere Felder wie 'css', 'imports' etc. stehen, die wir aber ignorieren.
    }

    @TemplateExtension
    public static class AssetExtensions {
        public static String asset(String originalName) {
            return AssetService.getAssetPath(originalName);
        }
    }
}
