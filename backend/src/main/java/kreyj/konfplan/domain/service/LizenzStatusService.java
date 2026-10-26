package kreyj.konfplan.domain.service;

import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import kreyj.konfplan.domain.exception.BusinessException;
import kreyj.konfplan.domain.lizenz.InstanzSchluessel;
import kreyj.konfplan.domain.lizenz.LizenzAnforderung;
import kreyj.konfplan.domain.lizenz.LizenzClaims;
import kreyj.konfplan.domain.lizenz.LizenzFehler;
import kreyj.konfplan.domain.lizenz.LizenzServerClient;
import kreyj.konfplan.domain.lizenz.LizenzServerException;
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
 * Verwaltet Instanzidentitaet und installierte Lizenz (Offline-Datei oder Online-Aktivierung beim
 * Lizenzserver). Die Schreibsperre selbst setzt der LizenzFilter durch.
 */
@ApplicationScoped
public class LizenzStatusService {

    /** Der Zeit-Wasserstand wird nur in diesem Abstand fortgeschrieben, um Schreibzugriffe zu sparen. */
    static final Duration WASSERSTAND_SCHRITT = Duration.ofHours(1);
    private static final Duration ZEIT_TOLERANZ = Duration.ofMinutes(5);
    /** Die Schreibsperre fragt den Status bei jeder gesperrten Anfrage ab - nicht jedes Mal die Datenbank belasten. */
    static final Duration STATUS_CACHE_DAUER = Duration.ofSeconds(30);
    /** Ab so viel Zeit vor der faelligen Revalidierung beginnt der Job, sie zu versuchen. */
    static final Duration REVALIDIERUNG_VORLAUF = Duration.ofDays(2);

    private record GecachterStatus(LizenzStatus status, Instant bis) {
    }

    private final LizenzVerifier verifier;
    private final Clock clock;
    private final LizenzServerClient serverClient;
    private final String version;
    private volatile GecachterStatus cache;

    @Inject
    public LizenzStatusService(@ConfigProperty(name = "konfplan.lizenz.schluessel-resource", defaultValue = "license/public-keys.properties") String schluesselResource,
                               LizenzServerClient serverClient,
                               @ConfigProperty(name = "quarkus.application.version", defaultValue = "unbekannt") String version) {
        this(LizenzVerifier.ausClasspath(schluesselResource, Clock.systemUTC()), Clock.systemUTC(), serverClient, version);
    }

    LizenzStatusService(LizenzVerifier verifier, Clock clock, LizenzServerClient serverClient, String version) {
        this.verifier = verifier;
        this.clock = clock;
        this.serverClient = serverClient;
        this.version = version;
    }

    /** Ist ein Lizenzserver konfiguriert (Online-Aktivierung moeglich)? */
    public boolean onlineMoeglich() {
        return serverClient.konfiguriert();
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
            throw new LizenzFehler(LizenzFehler.Grund.TYP, "Nur Offline-Lizenzen können hochgeladen werden.");
        }
        uebernehme(z, token.trim(), claims, null);
        return claims;
    }

    /**
     * Aktiviert diese Instanz beim Lizenzserver und installiert die gelieferte Online-Lizenz. Der
     * bisherige Lizenzzustand bleibt unveraendert, wenn der Server ablehnt oder nicht erreichbar ist.
     *
     * @throws LizenzServerException wenn der Lizenzserver nicht erreichbar ist oder ablehnt
     * @throws LizenzFehler          wenn die gelieferte Lizenz nicht akzeptiert werden darf
     */
    public LizenzStatus aktiviere(String lizenzschluessel) {
        if (null == lizenzschluessel || lizenzschluessel.isBlank()) {
            throw new BusinessException("Bitte einen Lizenzschlüssel angeben.");
        }
        LizenzServerClient.Identitaet identitaet = identitaet();
        String token = serverClient.aktivieren(identitaet, lizenzschluessel.trim());

        QuarkusTransaction.requiringNew().run(() -> installiereOnline(token, true));
        return statusNeu();
    }

    /**
     * Erneuert die Online-Lizenz beim Lizenzserver. Auch eine abgelaufene Lizenz darf erneuert werden;
     * ob sie noch zu erneuern ist, entscheidet der Server. Das Ergebnis des Versuchs (Zeitpunkt, Fehler)
     * wird festgehalten.
     *
     * @throws LizenzServerException wenn der Lizenzserver nicht erreichbar ist oder ablehnt
     */
    public LizenzStatus revalidiere() {
        LizenzServerClient.Identitaet identitaet = identitaet();
        String token = QuarkusTransaction.requiringNew().call(() -> instanz().getLizenzToken());
        Long zaehler = QuarkusTransaction.requiringNew().call(() -> instanz().getLetzterZaehler());
        if (null == token || null == zaehler) {
            throw new BusinessException("Es ist keine Online-Lizenz aktiviert.");
        }

        try {
            String neu = serverClient.revalidieren(identitaet, token);
            QuarkusTransaction.requiringNew().run(() -> installiereOnline(neu, false));
        } catch (LizenzServerException | LizenzFehler e) {
            QuarkusTransaction.requiringNew().run(() -> {
                Lizenzzustand z = instanz();
                z.setRevalidierungVersuch(clock.instant());
                z.setRevalidierungFehler(abschneiden(e.getMessage()));
            });
            cache = null;
            throw e;
        }
        return statusNeu();
    }

    /** Fuer den Hintergrundjob: erneuert die Online-Lizenz, sobald das absehbar noetig ist. */
    public void revalidiereFallsFaellig() {
        if (!serverClient.konfiguriert() || null == QuarkusTransaction.requiringNew().call(() -> instanz().getLetzterZaehler())) {
            return;
        }
        LizenzStatus s = statusNeu();
        boolean faellig = null == s.claims()
            || !clock.instant().isBefore(s.claims().naechsteRevalidierung().minus(REVALIDIERUNG_VORLAUF));
        if (faellig) {
            revalidiere();
        }
    }

    /**
     * Entfernt die Lizenz von dieser Instanz. Eine Online-Lizenz gibt vorher ihren Aktivierungsplatz
     * beim Lizenzserver frei - ist er nicht erreichbar, bricht das ab, ausser {@code nurLokal} ist gesetzt
     * (der Platz muss dann beim Hersteller freigegeben werden).
     */
    public LizenzStatus deaktiviere(boolean nurLokal) {
        LizenzServerClient.Identitaet identitaet = identitaet();
        String token = QuarkusTransaction.requiringNew().call(() -> instanz().getLizenzToken());
        Long zaehler = QuarkusTransaction.requiringNew().call(() -> instanz().getLetzterZaehler());
        if (null != token && null != zaehler && !nurLokal) {
            serverClient.deaktivieren(identitaet, token);
        }
        QuarkusTransaction.requiringNew().run(() -> {
            Lizenzzustand z = instanz();
            z.setLizenzToken(null);
            z.setLetzterZaehler(null);
            z.setRevalidierungVersuch(null);
            z.setRevalidierungFehler(null);
        });
        cache = null;
        return statusNeu();
    }

    /** Wie {@link #status()}, aber hoechstens alle {@link #STATUS_CACHE_DAUER} neu berechnet. */
    @Transactional
    public LizenzStatus statusGecacht() {
        GecachterStatus c = cache;
        Instant jetzt = clock.instant();
        if (null != c && jetzt.isBefore(c.bis())) {
            return c.status();
        }
        LizenzStatus status = status();
        cache = new GecachterStatus(status, jetzt.plus(STATUS_CACHE_DAUER));
        return status;
    }

    @Transactional
    public LizenzAnforderung anforderung() {
        Lizenzzustand z = instanz();
        return new LizenzAnforderung(z.getInstanzId(), z.getInstanzPublicKey(), jkt(z));
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
            if (LizenzClaims.Typ.ONLINE == claims.typ() && !jetzt.isBefore(claims.naechsteRevalidierung())) {
                return LizenzStatus.grace(claims);
            }
            return LizenzStatus.lizenziert(claims);
        } catch (LizenzFehler e) {
            return LizenzStatus.eingeschraenkt(e.getGrund());
        }
    }

    /** Zeitpunkt und Fehler des letzten Revalidierungsversuchs fuer die Anzeige. */
    @Transactional
    public Lizenzzustand zustand() {
        return instanz();
    }

    /**
     * Status in einer eigenen Transaktion berechnen: so liest er garantiert den gerade geschriebenen
     * Stand und nicht eine in der Session des Aufrufers gecachte Entity.
     */
    private LizenzStatus statusNeu() {
        return QuarkusTransaction.requiringNew().call(this::status);
    }

    private LizenzServerClient.Identitaet identitaet() {
        return QuarkusTransaction.requiringNew().call(() -> {
            Lizenzzustand z = instanz();
            return new LizenzServerClient.Identitaet(z.getInstanzId(), z.getInstanzPublicKey(),
                InstanzSchluessel.privateKeyAusBase64(z.getInstanzPrivateKey()), version);
        });
    }

    private void installiereOnline(String token, boolean aktivierung) {
        Lizenzzustand z = instanz();
        LizenzClaims claims = verifier.verifiziere(token, jkt(z));
        if (LizenzClaims.Typ.ONLINE != claims.typ()) {
            throw new LizenzFehler(LizenzFehler.Grund.TYP, "Der Lizenzserver hat keine Online-Lizenz geliefert.");
        }
        // Eine Revalidierung muss den Zaehler erhoehen - sonst koennte eine eingespielte alte Antwort die Lizenz zuruecksetzen.
        if (!aktivierung && null != z.getLetzterZaehler() && claims.zaehler() <= z.getLetzterZaehler()) {
            throw new LizenzFehler(LizenzFehler.Grund.VERALTET, "Der Lizenzserver hat eine veraltete Lizenz geliefert.");
        }
        uebernehme(z, token, claims, claims.zaehler());
        z.setRevalidierungVersuch(clock.instant());
        z.setRevalidierungFehler(null);
    }

    private void uebernehme(Lizenzzustand z, String token, LizenzClaims claims, Long zaehler) {
        Instant jetzt = clock.instant();
        z.setLizenzToken(token);
        z.setLetzterZaehler(zaehler);
        z.setLetzteValidierung(jetzt);
        z.setZeitWasserstand(spaeter(spaeter(z.getZeitWasserstand(), jetzt), claims.ausgestelltAm()));
        cache = null;
    }

    private static String jkt(Lizenzzustand z) {
        return InstanzSchluessel.thumbprint(InstanzSchluessel.publicKeyAusBase64(z.getInstanzPublicKey()));
    }

    private static Instant spaeter(Instant a, Instant b) {
        return null == a || b.isAfter(a) ? b : a;
    }

    private static String abschneiden(String text) {
        return null == text || text.length() <= 1000 ? text : text.substring(0, 1000);
    }
}
