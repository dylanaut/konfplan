package kreyj.konfplan.domain.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import kreyj.konfplan.domain.lizenz.InstanzSchluessel;
import kreyj.konfplan.domain.lizenz.LizenzClaims;
import kreyj.konfplan.domain.lizenz.LizenzFehler;
import kreyj.konfplan.domain.lizenz.LizenzStatus;
import kreyj.konfplan.domain.lizenz.LizenzVerifier;
import kreyj.konfplan.persistence.Lizenzzustand;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.security.KeyPair;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

/**
 * Verwaltet Instanzidentitaet und installierte Lizenz. Setzt noch nichts durch - die Schreibsperre
 * folgt in einem eigenen Schritt (siehe #781).
 */
@ApplicationScoped
public class LizenzStatusService {

    /** Der Zeit-Wasserstand wird nur in diesem Abstand fortgeschrieben, um Schreibzugriffe zu sparen. */
    static final Duration WASSERSTAND_SCHRITT = Duration.ofHours(1);
    private static final Duration ZEIT_TOLERANZ = Duration.ofMinutes(5);

    private final LizenzVerifier verifier;
    private final Clock clock;

    @Inject
    public LizenzStatusService(@ConfigProperty(name = "konfplan.lizenz.schluessel-resource", defaultValue = "license/public-keys.properties") String schluesselResource) {
        this(LizenzVerifier.ausClasspath(schluesselResource, Clock.systemUTC()), Clock.systemUTC());
    }

    LizenzStatusService(LizenzVerifier verifier, Clock clock) {
        this.verifier = verifier;
        this.clock = clock;
    }

    /** Liefert die Instanzidentitaet und legt sie beim ersten Aufruf an. */
    @Transactional
    public Lizenzzustand instanz() {
        Lizenzzustand z = Lizenzzustand.aktuell();
        if (null == z) {
            KeyPair kp = InstanzSchluessel.generiere();
            z = new Lizenzzustand();
            z.setInstanzId(UUID.randomUUID().toString());
            z.setInstanzPublicKey(Base64.getEncoder().encodeToString(kp.getPublic().getEncoded()));
            z.setInstanzPrivateKey(Base64.getEncoder().encodeToString(kp.getPrivate().getEncoded()));
            z.persist();
        }
        return z;
    }

    /**
     * Installiert eine Offline-Lizenz. Eine ungueltige Lizenz wird abgelehnt und ersetzt eine bereits
     * installierte nicht.
     *
     * @throws LizenzFehler wenn die Lizenz nicht akzeptiert werden darf
     */
    @Transactional
    public LizenzClaims installiere(String token) {
        Lizenzzustand z = instanz();
        LizenzClaims claims = verifier.verifiziere(token, jkt(z));
        if (LizenzClaims.Typ.OFFLINE != claims.typ()) {
            throw new LizenzFehler(LizenzFehler.Grund.TYP, "Nur Offline-Lizenzen koennen hochgeladen werden.");
        }
        Instant jetzt = clock.instant();
        z.setLizenzToken(token.trim());
        z.setLetzteValidierung(jetzt);
        z.setZeitWasserstand(spaeter(spaeter(z.getZeitWasserstand(), jetzt), claims.ausgestelltAm()));
        return claims;
    }

    @Transactional
    public LizenzStatus status() {
        Lizenzzustand z = instanz();
        Instant jetzt = clock.instant();

        if (null != z.getZeitWasserstand() && jetzt.isBefore(z.getZeitWasserstand())) {
            return LizenzStatus.eingeschraenkt(LizenzFehler.Grund.ZEIT);
        }
        if (null == z.getZeitWasserstand() || !jetzt.isBefore(z.getZeitWasserstand().plus(WASSERSTAND_SCHRITT))) {
            z.setZeitWasserstand(jetzt);
        }

        if (null == z.getLizenzToken()) {
            return LizenzStatus.unlizenziert();
        }
        try {
            LizenzClaims claims = verifier.verifiziere(z.getLizenzToken(), jkt(z));
            if (claims.ausgestelltAm().isAfter(jetzt.plus(ZEIT_TOLERANZ))) {
                return LizenzStatus.eingeschraenkt(LizenzFehler.Grund.ZEIT);
            }
            return LizenzStatus.lizenziert(claims);
        } catch (LizenzFehler e) {
            return LizenzStatus.eingeschraenkt(e.getGrund());
        }
    }

    private static String jkt(Lizenzzustand z) {
        return InstanzSchluessel.thumbprint(InstanzSchluessel.publicKeyAusBase64(z.getInstanzPublicKey()));
    }

    private static Instant spaeter(Instant a, Instant b) {
        return null == a || b.isAfter(a) ? b : a;
    }
}
