package kreyj.konfplan.domain.service;

import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Feste-Fenster-Rate-Begrenzung für {@code /api/auth/login}, pro Client-IP: nach
 * {@code maxAttempts} fehlgeschlagenen Versuchen innerhalb von {@code windowMinutes} wird
 * die IP für den Rest des Fensters blockiert. Bewusst pro IP statt pro Anmeldename, damit ein
 * Angreifer nicht durch gezielte Fehlversuche das Konto eines fremden Nutzers sperren kann.
 * In-Memory (kein verteilter Zustand) - passend für den Ein-Instanz-Betrieb der Debian-Pakete.
 */
@ApplicationScoped
public class LoginRateLimiterService {

    @ConfigProperty(name = "app.security.login-rate-limit.max-attempts", defaultValue = "5")
    int maxAttempts;

    @ConfigProperty(name = "app.security.login-rate-limit.window-minutes", defaultValue = "15")
    int windowMinutes;

    private record Attempts(int count, Instant windowStart) {
    }

    private final ConcurrentMap<String, Attempts> attemptsByIp = new ConcurrentHashMap<>();


    public boolean isBlocked(String ip) {
        Attempts a = attemptsByIp.get(ip);
        return a != null && !windowExpired(a) && a.count() >= maxAttempts;
    }


    public void recordFailure(String ip) {
        attemptsByIp.compute(ip, (key, existing) -> {
            if (existing == null || windowExpired(existing)) {
                return new Attempts(1, Instant.now());
            }
            return new Attempts(existing.count() + 1, existing.windowStart());
        });
    }


    public void recordSuccess(String ip) {
        attemptsByIp.remove(ip);
    }


    /** Verbleibende Sperrdauer, oder {@link Duration#ZERO}, wenn die IP nicht (mehr) blockiert ist. */
    public Duration remainingBlockDuration(String ip) {
        Attempts a = attemptsByIp.get(ip);
        if (a == null) {
            return Duration.ZERO;
        }
        Duration remaining = Duration.between(Instant.now(), unblockedAt(a));
        return remaining.isNegative() ? Duration.ZERO : remaining;
    }


    private boolean windowExpired(Attempts a) {
        return Instant.now().isAfter(unblockedAt(a));
    }


    private Instant unblockedAt(Attempts a) {
        return a.windowStart().plus(Duration.ofMinutes(windowMinutes));
    }


    /** Räumt abgelaufene Einträge periodisch auf, damit die Map bei vielen verschiedenen Angreifer-IPs nicht unbegrenzt wächst. */
    @Scheduled(every = "10m")
    void cleanup() {
        attemptsByIp.entrySet().removeIf(e -> windowExpired(e.getValue()));
    }


    /** Setzt den gesamten Sperr-Zustand zurück - für Tests, damit Testfälle sich nicht gegenseitig beeinflussen. */
    public void reset() {
        attemptsByIp.clear();
    }
}
