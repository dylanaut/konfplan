package kreyj.konfplan.infrastructure.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.arc.profile.IfBuildProfile;
import io.quarkus.logging.Log;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import kreyj.konfplan.domain.service.ProtokollService;
import kreyj.konfplan.persistence.Nutzer;
import kreyj.konfplan.persistence.ProtokollKategorie;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.representations.idm.EventRepresentation;

import java.util.List;

/**
 * Fehlgeschlagene Anmeldungen (LOGIN_ERROR) passieren in Keycloak, nie im KonfPlan-Backend
 * selbst - Keycloak lehnt falsche Zugangsdaten ab, bevor die App ueberhaupt ein Token sieht.
 * Pollt daher periodisch Keycloaks Realm-Events (muessen dafuer aktiviert sein, siehe
 * {@link kreyj.konfplan.application.prodsupport.ProdKeycloakRealmSyncService} sowie die
 * Realm-JSON fuer Dev/Erstanlage) und zaehlt neue Events als Micrometer-Counter, gruppiert nach
 * der KonfPlan-Rolle des betroffenen Nutzers (per Login-Namen aufgeloest, nicht per Keycloaks
 * userId - die bleibt bei komplett falschem Nutzernamen leer). Zusaetzlich landet jedes Event
 * mit dem versuchten Login-Namen als Akteur im App-eigenen {@code Protokoll} (Kategorie LOGIN),
 * damit ein Organisator es auch dort sieht statt nur in Keycloaks eigener Events-Ansicht.
 */
@ApplicationScoped
@IfBuildProfile(anyOf = {"dev", "prod"})
public class FailedLoginMetricsPoller {

    private static final String LOGIN_ERROR_EVENT_TYPE = "LOGIN_ERROR";
    private static final String UNKNOWN_ROLE = "UNKNOWN";

    @Inject
    Keycloak keycloak;

    @Inject
    MeterRegistry registry;

    @Inject
    ProtokollService protokollService;

    @ConfigProperty(name = "quarkus.keycloak.admin-client.realm")
    String realm;

    private volatile long lastPolledAtMillis = System.currentTimeMillis();

    @Scheduled(every = "1m")
    void pollFailedLogins() {
        long dateFrom = lastPolledAtMillis + 1;
        long dateTo = System.currentTimeMillis();

        List<EventRepresentation> events;
        try {
            // "asc" MUSS klein geschrieben sein - Keycloaks REST-Endpoint antwortet mit 400 Bad
            // Request bei "ASC", obwohl der admin-client dafuer einen generischen String-Parameter
            // ohne Enum/Validierung anbietet (per Live-Test gegen Keycloak 26.7.0 verifiziert).
            events = keycloak.realm(realm)
                .getEvents(List.of(LOGIN_ERROR_EVENT_TYPE), null, null, dateFrom, dateTo, null, null, null, "asc");
        } catch (Exception e) {
            Log.warn("Konnte fehlgeschlagene Anmeldungen nicht von Keycloak abfragen.", e);
            return;
        }

        for (EventRepresentation event : events) {
            String username = extractUsername(event);
            Nutzer nutzer = null == username ? null : Nutzer.findByLoginName(username);
            String role = null == nutzer ? UNKNOWN_ROLE : nutzer.getClass().getSimpleName().toUpperCase();

            Counter.builder("konfplan.login.failures")
                .tag("role", role)
                .description("Fehlgeschlagene Anmeldeversuche, gruppiert nach Rolle")
                .register(registry)
                .increment();

            // Akteur explizit auf den versuchten Login-Namen setzen (statt ProtokollService'
            // Default aus dem JWT des aktuellen Requests - hier gibt es keinen, da der Poll-Lauf
            // nicht im Kontext des fehlgeschlagenen Logins selbst laeuft).
            protokollService.log(ProtokollKategorie.LOGIN, "Fehlgeschlagene Anmeldung",
                "Anmeldeversuch für Login-Namen '" + (null == username ? "unbekannt" : username) + "' fehlgeschlagen.",
                null == nutzer ? null : nutzer.getId(),
                null == username ? "unbekannt" : username);
        }

        lastPolledAtMillis = dateTo;
    }

    private String extractUsername(EventRepresentation event) {
        return null == event.getDetails() ? null : event.getDetails().get("username");
    }
}
