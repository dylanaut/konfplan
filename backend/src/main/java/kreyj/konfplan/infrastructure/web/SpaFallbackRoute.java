package kreyj.konfplan.infrastructure.web;

import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.Router;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

/**
 * Quinoa's built-in {@code quarkus.quinoa.enable-spa-routing} fallback does not reliably serve
 * index.html for hard-navigated Vue Router routes on this Quarkus/Quinoa version combination
 * (reproduced locally in prod mode; a known, unresolved upstream ordering issue between Quinoa
 * and quarkus-rest, see https://github.com/quarkiverse/quarkus-quinoa/issues/666). Without this,
 * every deep link (e.g. a report opened via window.open, or the browser landing on /login after
 * a 401 redirect) 404s instead of loading the SPA.
 */
@ApplicationScoped
public class SpaFallbackRoute {

    void setup(@Observes Router router) {
        router.route(HttpMethod.GET, "/*").order(Integer.MIN_VALUE).handler(ctx -> {
            String path = ctx.normalizedPath();
            String lastSegment = path.substring(path.lastIndexOf('/') + 1);
            boolean looksLikeStaticFile = lastSegment.contains(".");
            if (path.equals("/") || path.startsWith("/api") || path.startsWith("/q") || looksLikeStaticFile) {
                ctx.next();
            } else {
                ctx.reroute("/");
            }
        });
    }
}
