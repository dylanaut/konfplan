package kreyj.konfplan.domain.service;

import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Verwaltet, ab welchem Zeitpunkt zuvor ausgestellte JWTs eines Nutzers nicht mehr gueltig
 * sind - notwendig, weil ein Passwort-Reset (Self-Service per {@link
 * kreyj.konfplan.adapter.in.web.AuthResource#resetPassword} oder durch einen Admin per {@link
 * AdminService#resetPassword}) sonst bereits ausgestellte Tokens unangetastet laesst: wurde ein
 * Konto kompromittiert und ein Angreifer besitzt bereits ein gueltiges Token, wuerde ein
 * Passwort-Reset ihn nicht aussperren, solange das Token noch nicht abgelaufen ist (bis zu 4h,
 * siehe AuthResource#login). Durchgesetzt wird das ueber TokenInvalidationAugmentor, der bei
 * jeder authentifizierten Anfrage die iat-Claim des Tokens gegen den hier gespeicherten
 * Zeitstempel prueft. In-Memory (kein verteilter Zustand) - passend fuer den Ein-Instanz-Betrieb
 * der Debian-Pakete, analog zu {@link LoginRateLimiterService} und {@link
 * ForgotPasswordRateLimiterService}: nach einem Neustart gelten alte Tokens wieder als gueltig,
 * was angesichts ihrer ohnehin kurzen Lebensdauer (4h) hingenommen wird.
 */
@ApplicationScoped
public class TokenInvalidationService {

    /** Muss mindestens der maximalen JWT-Gueltigkeitsdauer entsprechen (4h, siehe AuthResource#login),
     * damit kein Eintrag entfernt wird, waehrend ein davon betroffenes altes Token theoretisch noch
     * als gueltig durchgehen wuerde. */
    private static final Duration RETENTION = Duration.ofHours(6);

    private final ConcurrentMap<String, Instant> invalidBeforeByLoginName = new ConcurrentHashMap<>();

    /**
     * Erklaert alle Tokens des Nutzers, die vor JETZT ausgestellt wurden, fuer ungueltig.
     * Der Schwellenwert wird auf volle Sekunden abgerundet, da die iat-Claim eines JWT nur
     * Sekundenpraezision hat (NumericDate) - ohne das Abrunden koennte ein direkt im Anschluss
     * (noch in derselben Sekunde) frisch ausgestelltes Token faelschlich als "davor" gelten.
     */
    public void invalidateTokensIssuedBefore(String loginName) {
        if (loginName == null) {
            return;
        }
        invalidBeforeByLoginName.put(loginName, Instant.now().truncatedTo(ChronoUnit.SECONDS));
    }

    public boolean isValid(String loginName, Instant issuedAt) {
        Instant threshold = invalidBeforeByLoginName.get(loginName);
        return threshold == null || !issuedAt.isBefore(threshold);
    }

    @Scheduled(every = "1h")
    void cleanup() {
        Instant cutoff = Instant.now().minus(RETENTION);
        invalidBeforeByLoginName.entrySet().removeIf(e -> e.getValue().isBefore(cutoff));
    }

    /** Setzt den gesamten Zustand zurueck - fuer Tests, damit Testfaelle sich nicht gegenseitig beeinflussen. */
    public void reset() {
        invalidBeforeByLoginName.clear();
    }
}
