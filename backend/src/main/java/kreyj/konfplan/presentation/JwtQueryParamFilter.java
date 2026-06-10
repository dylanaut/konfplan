package kreyj.konfplan.presentation;

import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.ext.Provider;

/**
 * Dieser Filter liest ein JWT-Token aus dem Query-Parameter "jwt"
 * und setzt es in den Authorization-Header.
 * Dies ist notwendig für direkte Browser-Navigationen (z.B. window.open),
 * da der Browser dabei keine benutzerdefinierten Header sendet.
 */
@Provider
@Priority(Priorities.AUTHENTICATION - 1) // Läuft vor der eigentlichen Authentifizierung
public class JwtQueryParamFilter implements ContainerRequestFilter {

    private static final String JWT_QUERY_PARAM = "jwt";
    private static final String BEARER_PREFIX = "Bearer ";

    @Override
    public void filter(ContainerRequestContext requestContext) {
        // Prüfen, ob der Authorization-Header bereits gesetzt ist
        String authorizationHeader = requestContext.getHeaderString(HttpHeaders.AUTHORIZATION);

        if (authorizationHeader == null || authorizationHeader.isEmpty()) {
            // Wenn nicht, prüfen, ob ein JWT-Token als Query-Parameter vorhanden ist
            String jwtFromQuery = requestContext.getUriInfo().getQueryParameters().getFirst(JWT_QUERY_PARAM);

            if (jwtFromQuery != null && !jwtFromQuery.isEmpty()) {
                // Token gefunden, in den Authorization-Header setzen
                requestContext.getHeaders().add(HttpHeaders.AUTHORIZATION, BEARER_PREFIX + jwtFromQuery);
            }
        }
    }
}