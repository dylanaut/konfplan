package kreyj.konfplan.util;

import io.quarkus.qute.TemplateLocator;
import io.quarkus.qute.Variant;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Ermöglicht das Laden von Qute-Templates aus dem Dateisystem.
 * Dies erlaubt Änderungen an E-Mail-Templates ohne Redeploy.
 */
@ApplicationScoped
public class ExternalTemplateLocator implements TemplateLocator {
    private static final Logger LOG = Logger.getLogger(ExternalTemplateLocator.class);

    @ConfigProperty(name = "app.templates.external-path")
    Optional<String> externalPath;

    @Override
    public Optional<TemplateLocation> locate(String id) {
        if (externalPath.isEmpty()) {
            return Optional.empty();
        }

        Path templatePath = Path.of(externalPath.get(), id);

        if (Files.exists(templatePath) && Files.isRegularFile(templatePath)) {
            LOG.debugf("Lade Template aus Dateisystem: %s", templatePath.toAbsolutePath());
            return Optional.of(new TemplateLocation() {
                @Override
                public Reader read() {
                    try {
                        return new InputStreamReader(Files.newInputStream(templatePath), StandardCharsets.UTF_8);
                    } catch (IOException e) {
                        throw new RuntimeException("Fehler beim Lesen des externen Templates: " + templatePath, e);
                    }
                }

                @Override
                public Optional<Variant> getVariant() {
                    return Optional.empty();
                }
            });
        }
        return Optional.empty();
    }
}