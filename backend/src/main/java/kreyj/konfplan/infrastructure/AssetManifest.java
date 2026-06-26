package kreyj.konfplan.infrastructure;

import io.quarkus.runtime.LaunchMode;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

@ApplicationScoped
public class AssetManifest {
    private static final Logger LOG = Logger.getLogger(AssetManifest.class);

    @ConfigProperty(name = "quarkus.quinoa.dev-server.port", defaultValue = "5173")
    int devServerPort;

    private Map<String, String> manifest = Map.of();


    void onStart(@Observes StartupEvent ev) {
        if (LaunchMode.current().isDevOrTest()) {
            return; // im Dev-Modus brauchen wir kein Manifest
        }
        try (InputStream is = getClass().getClassLoader()
            .getResourceAsStream("META-INF/resources/.vite/manifest.json")) {
            if (is != null) {
                JsonObject json = Json.createReader(is).readObject();
                Map<String, String> map = new HashMap<>();
                for (String key : json.keySet()) {
                    String file = json.getJsonObject(key).getString("file");
                    map.put(key, "/" + file);
                }
                this.manifest = Map.copyOf(map);
            }
        } catch (Exception e) {
            LOG.warn("Konnte Vite-Manifest nicht laden", e);
        }
    }


    public String asset(String entry) {
        if (LaunchMode.current().isDevOrTest()) {
            // Vite-Dev-Server liefert Quelldateien unter ihrem Originalpfad aus,
            // Quinoa proxyt das transparent unter der Quarkus-URL durch
            return "/src/" + entry;
        }
        return manifest.getOrDefault(entry, "/" + entry);
    }


    public boolean isDev() {
        return LaunchMode.current().isDevOrTest();
    }
}
