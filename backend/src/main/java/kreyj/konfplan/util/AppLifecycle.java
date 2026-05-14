package kreyj.konfplan.util;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.jboss.logging.Logger;

/**
 * This bean observes the application startup event to eagerly initialize
 * components that might be loaded lazily, causing issues on the first request.
 */
@ApplicationScoped
public class AppLifecycle {
    private static final Logger LOG = Logger.getLogger(AppLifecycle.class);


    /**
     * Eagerly inject the JsonWebToken provider.
     * This forces the JWT parsing and validation infrastructure, including the loading
     * of verification keys, to be initialized at application startup.
     */
    @Inject
    JsonWebToken jwt;

    void onStart(@Observes StartupEvent ev) {
        LOG.info("Application started with JWT issuer: " + jwt.getIssuer());
    }
}
