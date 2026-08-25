package kreyj.konfplan.infrastructure.observability;

import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.SecurityContext;
import jakarta.ws.rs.ext.Provider;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.util.List;

/**
 * Aktualisiert pro authentifiziertem Request den Aktivitaets-Zeitstempel des Nutzers in
 * {@link UserActivityTracker} - bewusst ohne Datenbankzugriff (Rolle kommt direkt aus dem
 * bereits verifizierten JWT/SecurityContext, nicht per Nutzer-Lookup), damit das Tracking selbst
 * keine zusaetzliche DB-Last erzeugt.
 */
@Provider
@Priority(Priorities.AUTHENTICATION + 100)
public class UserActivityTrackingFilter implements ContainerRequestFilter {

    private static final List<String> ROLES = List.of("ADMIN", "REFERENT", "TEILNEHMER");

    @Inject
    UserActivityTracker tracker;

    @Inject
    JsonWebToken jwt;

    @Override
    public void filter(ContainerRequestContext requestContext) {
        SecurityContext securityContext = requestContext.getSecurityContext();
        if (null == securityContext || null == securityContext.getUserPrincipal()) {
            return;
        }

        String subject = jwt.getSubject();
        if (null == subject) {
            return;
        }

        ROLES.stream()
            .filter(securityContext::isUserInRole)
            .findFirst()
            .ifPresent(role -> tracker.recordActivity(subject, role));
    }
}
