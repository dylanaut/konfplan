package kreyj.konfplan.domain.service;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import kreyj.konfplan.domain.exception.BusinessException;
import kreyj.konfplan.domain.lizenz.FakeLizenzServer;
import kreyj.konfplan.domain.lizenz.FakeLizenzServer.Lizenz;
import kreyj.konfplan.domain.lizenz.InstanzSchluessel;
import kreyj.konfplan.domain.lizenz.LizenzClaims;
import kreyj.konfplan.domain.lizenz.LizenzFehler;
import kreyj.konfplan.domain.lizenz.LizenzServerClient;
import kreyj.konfplan.domain.lizenz.LizenzServerException;
import kreyj.konfplan.domain.lizenz.LizenzServerException.Art;
import kreyj.konfplan.domain.lizenz.LizenzStatus;
import kreyj.konfplan.domain.lizenz.LizenzTestSupport;
import kreyj.konfplan.domain.lizenz.LizenzVerifier;
import kreyj.konfplan.domain.lizenz.LizenzZustand;
import kreyj.konfplan.infrastructure.HttpLizenzServerClient;
import kreyj.konfplan.persistence.Lizenzzustand;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;

import static kreyj.konfplan.domain.lizenz.LizenzTestSupport.KID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Online-Aktivierung und -Revalidierung gegen einen {@link FakeLizenzServer}. */
@QuarkusTest
class LizenzOnlineTest {

    private static final String SCHLUESSEL = "KEY-1";

    private MutableClock clock;
    private FakeLizenzServer server;
    private LizenzServerClient client;
    private LizenzStatusService service;

    @BeforeEach
    void setup() {
        QuarkusTransaction.requiringNew().run(() -> Lizenzzustand.deleteAll());
        clock = new MutableClock(Instant.ofEpochSecond(1_800_000_000L));
        server = new FakeLizenzServer(clock::instant);
        server.lizenz(SCHLUESSEL, new Lizenz("LIC-ON-1", "Schule Online", 1));
        client = new HttpLizenzServerClient(server.url(), Duration.ofSeconds(5));
        service = service(client);
    }

    @AfterEach
    void teardown() {
        server.close();
    }

    private LizenzStatusService service(LizenzServerClient c) {
        return new LizenzStatusService(new LizenzVerifier(Map.of(KID, LizenzTestSupport.public1()), Set.of(), clock), clock, c, "9.9.9");
    }

    private String instanzId() {
        return QuarkusTransaction.requiringNew().call(() -> service.instanz().getInstanzId());
    }

    private Lizenzzustand zustand() {
        return QuarkusTransaction.requiringNew().call(() -> Lizenzzustand.<Lizenzzustand>findAll().firstResult());
    }

    private LizenzStatus status() {
        return QuarkusTransaction.requiringNew().call(service::status);
    }

    private static LizenzServerException.Art art(Throwable t) {
        return ((LizenzServerException) t).getArt();
    }

    private static String code(Throwable t) {
        return ((LizenzServerException) t).getCode();
    }

    @Test
    void aktivierungInstalliertEineOnlineLizenz() {
        LizenzStatus s = service.aktiviere(SCHLUESSEL);

        assertThat(s.zustand()).isEqualTo(LizenzZustand.LIZENZIERT);
        assertThat(s.claims().typ()).isEqualTo(LizenzClaims.Typ.ONLINE);
        assertThat(s.claims().lizenzId()).isEqualTo("LIC-ON-1");
        assertThat(s.claims().zaehler()).isEqualTo(1L);
        assertThat(s.claims().naechsteRevalidierung()).isEqualTo(clock.instant().plus(Duration.ofDays(7)).truncatedTo(java.time.temporal.ChronoUnit.SECONDS));
        assertThat(server.anzahlAktivierungen(SCHLUESSEL)).isEqualTo(1);
        assertThat(zustand().getLetzterZaehler()).isEqualTo(1L);
        assertThat(zustand().getLetzteValidierung()).isEqualTo(clock.instant());
    }

    @Test
    void unbekannterLizenzschluesselWirdAbgelehntUndAendertNichts() {
        assertThatThrownBy(() -> service.aktiviere("FALSCH"))
            .isInstanceOf(LizenzServerException.class)
            .satisfies((e) -> {
                assertThat(art(e)).isEqualTo(Art.ABGELEHNT);
                assertThat(code(e)).isEqualTo("UNBEKANNT");
            });

        assertThat(status().zustand()).isEqualTo(LizenzZustand.UNLIZENZIERT);
    }

    @Test
    void leererLizenzschluesselWirdAbgelehnt() {
        assertThatThrownBy(() -> service.aktiviere("  ")).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.aktiviere(null)).isInstanceOf(BusinessException.class);
    }

    @Test
    void erreichtesInstanzLimitWirdAbgelehnt() {
        KeyPair fremd = InstanzSchluessel.generiere();
        server.registriere(SCHLUESSEL, "andere-instanz", fremd.getPublic());

        assertThatThrownBy(() -> service.aktiviere(SCHLUESSEL))
            .isInstanceOf(LizenzServerException.class)
            .satisfies((e) -> assertThat(code(e)).isEqualTo("LIMIT_ERREICHT"));
        assertThat(status().zustand()).isEqualTo(LizenzZustand.UNLIZENZIERT);
    }

    @Test
    void widerrufeneLizenzWirdAbgelehnt() {
        server.widerrufe(SCHLUESSEL);

        assertThatThrownBy(() -> service.aktiviere(SCHLUESSEL))
            .satisfies((e) -> assertThat(code(e)).isEqualTo("WIDERRUFEN"));
    }

    @Test
    void nichtErreichbarerServerAendertNichts() {
        LizenzStatusService ohneServer = service(new HttpLizenzServerClient("http://localhost:1", Duration.ofSeconds(2)));

        assertThatThrownBy(() -> ohneServer.aktiviere(SCHLUESSEL))
            .isInstanceOf(LizenzServerException.class)
            .satisfies((e) -> assertThat(art(e)).isEqualTo(Art.NICHT_ERREICHBAR));
        assertThat(status().zustand()).isEqualTo(LizenzZustand.UNLIZENZIERT);
    }

    @Test
    void revalidierungErhoehtDenZaehlerUndVerlaengertDieFrist() {
        service.aktiviere(SCHLUESSEL);
        clock.vor(Duration.ofDays(5));

        LizenzStatus s = service.revalidiere();

        assertThat(s.zustand()).isEqualTo(LizenzZustand.LIZENZIERT);
        assertThat(s.claims().zaehler()).isEqualTo(2L);
        assertThat(s.claims().naechsteRevalidierung()).isAfter(clock.instant().plus(Duration.ofDays(6)));
        assertThat(zustand().getRevalidierungFehler()).isNull();
        assertThat(server.zaehler(instanzId())).isEqualTo(2L);
    }

    @Test
    void faelligeRevalidierungErgibtGraceUndDanachAblauf() {
        service.aktiviere(SCHLUESSEL);

        clock.vor(Duration.ofDays(8));
        assertThat(status().zustand()).isEqualTo(LizenzZustand.GRACE);
        assertThat(status().claims()).isNotNull();

        clock.vor(Duration.ofDays(14));
        LizenzStatus abgelaufen = status();
        assertThat(abgelaufen.zustand()).isEqualTo(LizenzZustand.EINGESCHRAENKT);
        assertThat(abgelaufen.grund()).isEqualTo(LizenzFehler.Grund.ABGELAUFEN);

        // Auch eine abgelaufene Lizenz kann erneuert werden - der Server entscheidet.
        assertThat(service.revalidiere().zustand()).isEqualTo(LizenzZustand.LIZENZIERT);
    }

    @Test
    void fehlgeschlageneRevalidierungWirdFestgehaltenUndLaesstDieGraceLaufen() {
        service.aktiviere(SCHLUESSEL);
        server.close();
        clock.vor(Duration.ofDays(6));

        assertThatThrownBy(service::revalidiere).isInstanceOf(LizenzServerException.class);

        assertThat(zustand().getRevalidierungFehler()).contains("nicht erreichbar");
        assertThat(zustand().getRevalidierungVersuch()).isEqualTo(clock.instant());
        assertThat(status().zustand()).isEqualTo(LizenzZustand.LIZENZIERT);
    }

    @Test
    void geklonteInstanzMitVeraltetemZaehlerWirdErkannt() {
        service.aktiviere(SCHLUESSEL);
        String alterToken = zustand().getLizenzToken();
        LizenzServerClient.Identitaet id = QuarkusTransaction.requiringNew().call(() -> {
            Lizenzzustand z = service.instanz();
            return new LizenzServerClient.Identitaet(z.getInstanzId(), z.getInstanzPublicKey(), InstanzSchluessel.privateKeyAusBase64(z.getInstanzPrivateKey()), "9.9.9");
        });
        service.revalidiere();
        service.revalidiere();

        // Die Kopie meldet sich mit dem Stand von vor zwei Revalidierungen.
        assertThatThrownBy(() -> client.revalidieren(id, alterToken))
            .satisfies((e) -> assertThat(code(e)).isEqualTo("KLON_ERKANNT"));
    }

    @Test
    void wiederholungNachVerlorenerAntwortWirdIdempotentBeantwortet() {
        service.aktiviere(SCHLUESSEL);
        String token1 = zustand().getLizenzToken();
        LizenzServerClient.Identitaet id = QuarkusTransaction.requiringNew().call(() -> {
            Lizenzzustand z = service.instanz();
            return new LizenzServerClient.Identitaet(z.getInstanzId(), z.getInstanzPublicKey(), InstanzSchluessel.privateKeyAusBase64(z.getInstanzPrivateKey()), "9.9.9");
        });

        String a = client.revalidieren(id, token1);
        // Antwort "ging verloren": derselbe Stand wird erneut eingereicht.
        String b = client.revalidieren(id, token1);

        assertThat(b).isEqualTo(a);
        assertThat(server.zaehler(id.instanzId())).isEqualTo(2L);
    }

    @Test
    void eingespielteAlteAntwortSetztDieLizenzNichtZurueck() {
        service.aktiviere(SCHLUESSEL);
        String alt = zustand().getLizenzToken();
        service.revalidiere();
        server.erzwungeneAntwort = alt;

        assertThatThrownBy(service::revalidiere)
            .isInstanceOf(LizenzFehler.class)
            .satisfies((e) -> assertThat(((LizenzFehler) e).getGrund()).isEqualTo(LizenzFehler.Grund.VERALTET));

        assertThat(zustand().getLetzterZaehler()).isEqualTo(2L);
        assertThat(zustand().getRevalidierungFehler()).contains("veraltet");
    }

    @Test
    void anfragenMitFalscherSignaturWerdenVomServerAbgelehnt() {
        service.aktiviere(SCHLUESSEL);
        KeyPair andere = InstanzSchluessel.generiere();
        LizenzServerClient.Identitaet falsch = QuarkusTransaction.requiringNew().call(() -> {
            Lizenzzustand z = service.instanz();
            return new LizenzServerClient.Identitaet(z.getInstanzId(), z.getInstanzPublicKey(), andere.getPrivate(), "9.9.9");
        });

        assertThatThrownBy(() -> client.revalidieren(falsch, zustand().getLizenzToken()))
            .satisfies((e) -> assertThat(code(e)).isEqualTo("SIGNATUR"));
        assertThatThrownBy(() -> client.deaktivieren(falsch, zustand().getLizenzToken()))
            .satisfies((e) -> assertThat(code(e)).isEqualTo("SIGNATUR"));
    }

    @Test
    void deaktivierungGibtDenPlatzFreiUndEntferntDieLizenz() {
        service.aktiviere(SCHLUESSEL);

        LizenzStatus s = service.deaktiviere(false);

        assertThat(s.zustand()).isEqualTo(LizenzZustand.UNLIZENZIERT);
        assertThat(server.anzahlAktivierungen(SCHLUESSEL)).isZero();
        assertThat(zustand().getLetzterZaehler()).isNull();
        // Der freie Platz laesst sich erneut aktivieren.
        assertThat(service.aktiviere(SCHLUESSEL).zustand()).isEqualTo(LizenzZustand.LIZENZIERT);
    }

    @Test
    void deaktivierungOhneServerBrichtAbEsSeiDennNurLokal() {
        service.aktiviere(SCHLUESSEL);
        server.close();

        assertThatThrownBy(() -> service.deaktiviere(false)).isInstanceOf(LizenzServerException.class);
        assertThat(status().zustand()).isEqualTo(LizenzZustand.LIZENZIERT);

        assertThat(service.deaktiviere(true).zustand()).isEqualTo(LizenzZustand.UNLIZENZIERT);
    }

    @Test
    void revalidierungOhneOnlineLizenzWirdAbgelehnt() {
        assertThatThrownBy(service::revalidiere).isInstanceOf(BusinessException.class);
    }

    @Test
    void hintergrundjobErneuertErstKurzVorDerFaelligkeit() {
        service.aktiviere(SCHLUESSEL);

        clock.vor(Duration.ofDays(4));
        service.revalidiereFallsFaellig();
        assertThat(server.zaehler(instanzId())).as("noch 3 Tage bis zur Faelligkeit - zu frueh").isEqualTo(1L);

        clock.vor(Duration.ofDays(2).plusHours(1));
        service.revalidiereFallsFaellig();
        assertThat(server.zaehler(instanzId())).as("weniger als 2 Tage Vorlauf - faellig").isEqualTo(2L);
    }

    @Test
    void hintergrundjobIgnoriertOfflineLizenzenUndFehlendenServer() {
        LizenzStatusService ohneServer = service(new HttpLizenzServerClient((String) null, Duration.ofSeconds(1)));

        ohneServer.revalidiereFallsFaellig();
        service.revalidiereFallsFaellig();

        assertThat(server.anzahlAktivierungen(SCHLUESSEL)).isZero();
    }

    @Test
    void onlineMoeglichSpiegeltDieKonfiguration() {
        assertThat(service.onlineMoeglich()).isTrue();
        assertThat(service(new HttpLizenzServerClient((String) null, Duration.ofSeconds(1))).onlineMoeglich()).isFalse();
    }

    @Test
    void serverUrlMussHttpsSeinAusserFuerLoopback() {
        assertThat(new HttpLizenzServerClient("https://lizenz.example.org", Duration.ofSeconds(1)).konfiguriert()).isTrue();
        assertThat(new HttpLizenzServerClient("http://localhost:8080", Duration.ofSeconds(1)).konfiguriert()).isTrue();
        assertThat(new HttpLizenzServerClient("http://lizenz.example.org", Duration.ofSeconds(1)).konfiguriert()).isFalse();
        assertThat(new HttpLizenzServerClient("ftp://lizenz.example.org", Duration.ofSeconds(1)).konfiguriert()).isFalse();
        assertThat(new HttpLizenzServerClient("kein url", Duration.ofSeconds(1)).konfiguriert()).isFalse();
        assertThat(new HttpLizenzServerClient("", Duration.ofSeconds(1)).konfiguriert()).isFalse();
    }

    @Test
    void offlineLizenzenBleibenOhneZaehlerUndWerdenNichtRevalidiert() {
        String jkt = InstanzSchluessel.thumbprint(InstanzSchluessel.publicKeyAusBase64(
            QuarkusTransaction.requiringNew().call(() -> service.instanz().getInstanzPublicKey())));
        String offline = LizenzTestSupport.token(LizenzTestSupport.PRIVATE_1, LizenzTestSupport.header(),
            LizenzTestSupport.claims(jkt, clock.instant().getEpochSecond()));
        QuarkusTransaction.requiringNew().run(() -> service.installiere(offline));

        assertThat(zustand().getLetzterZaehler()).isNull();
        assertThatThrownBy(service::revalidiere).isInstanceOf(BusinessException.class);
        assertThat(status().zustand()).isEqualTo(LizenzZustand.LIZENZIERT);
    }
}
