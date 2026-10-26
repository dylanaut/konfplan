package kreyj.konfplan.domain.service;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import kreyj.konfplan.domain.lizenz.InstanzSchluessel;
import kreyj.konfplan.domain.lizenz.LizenzFehler;
import kreyj.konfplan.domain.lizenz.LizenzStatus;
import kreyj.konfplan.domain.lizenz.LizenzTestSupport;
import kreyj.konfplan.domain.lizenz.LizenzVerifier;
import kreyj.konfplan.domain.lizenz.LizenzZustand;
import kreyj.konfplan.persistence.Lizenzzustand;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Set;

import static kreyj.konfplan.domain.lizenz.LizenzTestSupport.KID;
import static kreyj.konfplan.domain.lizenz.LizenzTestSupport.PRIVATE_1;
import static kreyj.konfplan.domain.lizenz.LizenzTestSupport.claims;
import static kreyj.konfplan.domain.lizenz.LizenzTestSupport.header;
import static kreyj.konfplan.domain.lizenz.LizenzTestSupport.token;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@QuarkusTest
class LizenzStatusServiceTest {

    private static final long JETZT = 1_800_000_000L;

    @Inject
    LizenzStatusService echterService;

    private MutableClock clock;
    private LizenzStatusService service;

    @BeforeEach
    void setup() {
        QuarkusTransaction.requiringNew().run(() -> Lizenzzustand.deleteAll());
        clock = new MutableClock(Instant.ofEpochSecond(JETZT));
        service = new LizenzStatusService(new LizenzVerifier(Map.of(KID, LizenzTestSupport.public1()), Set.of(), clock), clock);
    }

    private String jkt() {
        return QuarkusTransaction.requiringNew().call(() ->
                InstanzSchluessel.thumbprint(InstanzSchluessel.publicKeyAusBase64(service.instanz().getInstanzPublicKey())));
    }

    private String lizenz() {
        return token(PRIVATE_1, header(), claims(jkt(), JETZT));
    }

    /** Frisch in eigener Transaktion lesen - die Session des Testthreads cached sonst den alten Stand. */
    private Instant wasserstand() {
        return QuarkusTransaction.requiringNew().call(() -> Lizenzzustand.<Lizenzzustand>findAll().firstResult().getZeitWasserstand());
    }

    private LizenzStatus status() {
        return QuarkusTransaction.requiringNew().call(service::status);
    }

    @Test
    void instanzWirdEinmaligAngelegtUndBleibtStabil() {
        Lizenzzustand erste = QuarkusTransaction.requiringNew().call(service::instanz);
        Lizenzzustand zweite = QuarkusTransaction.requiringNew().call(service::instanz);

        assertThat(erste.getInstanzId()).isNotBlank().isEqualTo(zweite.getInstanzId());
        assertThat(zweite.getInstanzPublicKey()).isEqualTo(erste.getInstanzPublicKey());
        assertThat((long) QuarkusTransaction.requiringNew().call(() -> Lizenzzustand.count())).isEqualTo(1L);
    }

    @Test
    void ohneLizenzIstDieInstanzUnlizenziert() {
        assertThat(status().zustand()).isEqualTo(LizenzZustand.UNLIZENZIERT);
    }

    @Test
    void installierteLizenzMachtDieInstanzLizenziert() {
        QuarkusTransaction.requiringNew().run(() -> service.installiere(lizenz()));

        LizenzStatus s = status();

        assertThat(s.zustand()).isEqualTo(LizenzZustand.LIZENZIERT);
        assertThat(s.claims().maxInstanzen()).isEqualTo(3);
    }

    @Test
    void ungueltigeLizenzWirdNichtInstalliert() {
        assertThatThrownBy(() -> QuarkusTransaction.requiringNew().run(() -> service.installiere("kein.gueltiges.token")))
                .isInstanceOf(LizenzFehler.class);

        assertThat(status().zustand()).isEqualTo(LizenzZustand.UNLIZENZIERT);
    }

    @Test
    void ungueltigeLizenzErsetztKeineBestehende() {
        QuarkusTransaction.requiringNew().run(() -> service.installiere(lizenz()));

        assertThatThrownBy(() -> QuarkusTransaction.requiringNew().run(() -> service.installiere("kein.gueltiges.token")))
                .isInstanceOf(LizenzFehler.class);

        assertThat(status().zustand()).isEqualTo(LizenzZustand.LIZENZIERT);
    }

    @Test
    void onlineTokenWirdAlsOfflineLizenzAbgelehnt() {
        Map<String, Object> c = claims(jkt(), JETZT);
        c.put("type", "online");

        assertThatThrownBy(() -> QuarkusTransaction.requiringNew().run(() -> service.installiere(token(PRIVATE_1, header(), c))))
                .isInstanceOf(LizenzFehler.class)
                .extracting(e -> ((LizenzFehler) e).getGrund())
                .isEqualTo(LizenzFehler.Grund.TYP);
    }

    @Test
    void lizenzEinerAnderenInstanzWirdAbgelehnt() {
        String fremd = InstanzSchluessel.thumbprint(InstanzSchluessel.generiere().getPublic());
        String token = token(PRIVATE_1, header(), claims(fremd, JETZT));

        assertThatThrownBy(() -> QuarkusTransaction.requiringNew().run(() -> service.installiere(token)))
                .isInstanceOf(LizenzFehler.class)
                .extracting(e -> ((LizenzFehler) e).getGrund())
                .isEqualTo(LizenzFehler.Grund.INSTANZ);
    }

    @Test
    void abgelaufeneLizenzSchraenktEin() {
        QuarkusTransaction.requiringNew().run(() -> service.installiere(lizenz()));
        clock.vor(Duration.ofDays(2));

        LizenzStatus s = status();

        assertThat(s.zustand()).isEqualTo(LizenzZustand.EINGESCHRAENKT);
        assertThat(s.grund()).isEqualTo(LizenzFehler.Grund.ABGELAUFEN);
    }

    @Test
    void zurueckgestellteSystemuhrSchraenktEin() {
        QuarkusTransaction.requiringNew().run(() -> service.installiere(lizenz()));
        clock.vor(Duration.ofHours(12));
        assertThat(status().zustand()).isEqualTo(LizenzZustand.LIZENZIERT);

        // Uhr zurueck vor den zuletzt gesehenen Zeitpunkt - die Lizenz waere sonst "wieder" gueltig.
        clock.vor(Duration.ofHours(-6));

        LizenzStatus s = status();

        assertThat(s.zustand()).isEqualTo(LizenzZustand.EINGESCHRAENKT);
        assertThat(s.grund()).isEqualTo(LizenzFehler.Grund.ZEIT);
    }

    @Test
    void wasserstandSchreitetNurInGrossenSchrittenFort() {
        status();
        Instant erster = wasserstand();

        clock.vor(Duration.ofMinutes(10));
        status();

        assertThat(wasserstand()).isEqualTo(erster);

        clock.vor(LizenzStatusService.WASSERSTAND_SCHRITT);
        status();

        assertThat(wasserstand()).isAfter(erster);
    }

    @Test
    void gecachterStatusWirdBeiInstallationVerworfenUndLaeuftAb() {
        assertThat(QuarkusTransaction.requiringNew().call(service::statusGecacht).zustand()).isEqualTo(LizenzZustand.UNLIZENZIERT);

        // Innerhalb der Cache-Dauer sichtbar bleibt der alte Stand, auch wenn die Lizenz direkt in der DB steht ...
        String token = lizenz();
        QuarkusTransaction.requiringNew().run(() -> Lizenzzustand.<Lizenzzustand>findAll().firstResult().setLizenzToken(token));
        assertThat(QuarkusTransaction.requiringNew().call(service::statusGecacht).zustand()).isEqualTo(LizenzZustand.UNLIZENZIERT);

        // ... nach Ablauf wird neu berechnet.
        clock.vor(LizenzStatusService.STATUS_CACHE_DAUER.plusSeconds(1));
        assertThat(QuarkusTransaction.requiringNew().call(service::statusGecacht).zustand()).isEqualTo(LizenzZustand.LIZENZIERT);
    }

    @Test
    void installationVerwirftDenGecachtenStatusSofort() {
        assertThat(QuarkusTransaction.requiringNew().call(service::statusGecacht).zustand()).isEqualTo(LizenzZustand.UNLIZENZIERT);

        QuarkusTransaction.requiringNew().run(() -> service.installiere(lizenz()));

        assertThat(QuarkusTransaction.requiringNew().call(service::statusGecacht).zustand()).isEqualTo(LizenzZustand.LIZENZIERT);
    }

    @Test
    void anforderungLiefertIdentitaetUndThumbprint() {
        var a = QuarkusTransaction.requiringNew().call(service::anforderung);

        assertThat(a.instanzId()).hasSize(36);
        assertThat(a.thumbprint()).isEqualTo(jkt());
        assertThat(a.publicKey()).isNotBlank();
    }

    @Test
    void injizierterServiceNutztDieKonfigurierteTestSchluesselDatei() {
        QuarkusTransaction.requiringNew().run(() -> Lizenzzustand.deleteAll());

        Lizenzzustand z = QuarkusTransaction.requiringNew().call(echterService::instanz);
        String jkt = InstanzSchluessel.thumbprint(InstanzSchluessel.publicKeyAusBase64(z.getInstanzPublicKey()));
        long jetzt = Instant.now().getEpochSecond();

        QuarkusTransaction.requiringNew().run(() -> echterService.installiere(token(PRIVATE_1, header(), claims(jkt, jetzt))));

        assertThat(QuarkusTransaction.requiringNew().call(echterService::status).zustand()).isEqualTo(LizenzZustand.LIZENZIERT);
    }

    private static final class MutableClock extends Clock {
        private Instant jetzt;

        MutableClock(Instant start) {
            this.jetzt = start;
        }

        void vor(Duration d) {
            jetzt = jetzt.plus(d);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return jetzt;
        }
    }
}
