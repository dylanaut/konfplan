package kreyj.konfplan.domain.service;

import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Feste-Fenster-Rate-Begrenzung für {@code /api/auth/forgot-password}, pro Client-IP: nach
 * {@code maxAttempts} Anfragen innerhalb von {@code windowMinutes} wird die IP für den Rest
 * des Fensters blockiert. Bewusst pro IP statt pro Anmeldename (analog zu
 * {@link LoginRateLimiterService}), damit ein Angreifer nicht durch gezielte Anfragen das
 * Reset-Postfach eines fremden Nutzers zuspammen oder dessen eigenes Kontingent fuer den
 * echten Bedarfsfall aufbrauchen kann. Anders als beim Login gibt es hier keine Erfolg/
 * Fehlschlag-Unterscheidung - forgotPassword() liefert bewusst immer 202, um keine
 * Rueckschluesse auf existierende Anmeldenamen zuzulassen - daher wird jede Anfrage gezaehlt,
 * unabhaengig vom (fuer den Aufrufer nicht sichtbaren) Ergebnis. In-Memory (kein verteilter
 * Zustand) - passend fuer den Ein-Instanz-Betrieb der Debian-Pakete.
 */
@ApplicationScoped
public class ForgotPasswordRateLimiterService {

    @ConfigProperty(name = "app.security.forgot-password-rate-limit.max-attempts", defaultValue = "5")
    int maxAttempts;

    @ConfigProperty(name = "app.security.forgot-password-rate-limit.window-minutes", defaultValue = "15")
    int windowMinutes;

    private record Attempts(int count, Instant windowStart) {
    }

    private final ConcurrentMap<String, Attempts> attemptsByIp = new ConcurrentHashMap<>();


    public boolean isBlocked(String ip) {
        Attempts a = attemptsByIp.get(ip);
        return a != null && !windowExpired(a) && a.count() >= maxAttempts;
    }


    public void recordAttempt(String ip) {
        attemptsByIp.compute(ip, (key, existing) -> {
            if (existing == null || windowExpired(existing)) {
                return new Attempts(1, Instant.now());
            }
            return new Attempts(existing.count() + 1, existing.windowStart());
        });
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
