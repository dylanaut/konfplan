package kreyj.konfplan.infrastructure.observability;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.quarkus.logging.Log;
import io.quarkus.scheduler.Scheduled;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Zaehlt online Nutzer und misst die Verweilzeit pro Rolle rein anhand tatsaechlicher
 * Backend-Aktivitaet (nicht anhand der Keycloak-Session-Laufzeit, die auch bei Inaktivitaet
 * "eingeloggt" bleibt). Jeder authentifizierte Request aktualisiert ueber
 * {@link UserActivityTrackingFilter} den Zeitstempel des jeweiligen Nutzers; ein periodischer
 * Sweep schliesst Nutzer ohne Aktivitaet innerhalb von {@link #ONLINE_TIMEOUT} als "Besuch" ab
 * und erfasst dessen Dauer als Timer-Metrik. Rein In-Memory - bei einem Neustart gehen laufende
 * Besuche verloren, was fuer Nutzungsstatistiken unerheblich ist.
 */
@ApplicationScoped
public class UserActivityTracker {

    static final Duration ONLINE_TIMEOUT = Duration.ofMinutes(5);
    private static final List<String> ROLES = List.of("ORGANISATOR", "ADMINISTRATOR", "REFERENT", "TEILNEHMER");

    private final Map<String, Visit> visitsBySubject = new ConcurrentHashMap<>();

    @Inject
    MeterRegistry registry;

    private record Visit(String role, Instant firstSeen, Instant lastSeen) {
        Visit withLastSeen(Instant now) {
            return new Visit(role, firstSeen, now);
        }
    }

    @PostConstruct
    void registerGauges() {
        for (String role : ROLES) {
            Gauge.builder("konfplan.users.online", this, tracker -> tracker.countOnline(role))
                .tag("role", role)
                .description("Anzahl Nutzer mit Backend-Aktivitaet innerhalb der letzten " + ONLINE_TIMEOUT.toMinutes() + " Minuten")
                .register(registry);
        }
    }

    public void recordActivity(String subject, String role) {
        Instant now = Instant.now();
        visitsBySubject.compute(subject, (key, existing) -> existing == null
            ? new Visit(role, now, now)
            : existing.withLastSeen(now));
    }

    long countOnline(String role) {
        Instant threshold = Instant.now().minus(ONLINE_TIMEOUT);
        return visitsBySubject.values().stream()
            .filter(v -> v.role().equals(role))
            .filter(v -> v.lastSeen().isAfter(threshold))
            .count();
    }

    @Scheduled(every = "1m")
    void closeTimedOutVisits() {
        Instant threshold = Instant.now().minus(ONLINE_TIMEOUT);
        visitsBySubject.entrySet().removeIf(entry -> {
            Visit visit = entry.getValue();
            if (visit.lastSeen().isBefore(threshold)) {
                Duration duration = Duration.between(visit.firstSeen(), visit.lastSeen());
                Timer.builder("konfplan.session.duration")
                    .tag("role", visit.role())
                    .description("Verweilzeit eines Nutzers im System (Zeit zwischen erster und letzter Aktivitaet eines zusammenhaengenden Besuchs)")
                    .register(registry)
                    .record(duration);
                Log.debugf("Besuch abgeschlossen: role=%s, dauer=%s", visit.role(), duration);
                return true;
            }
            return false;
        });
    }
}
