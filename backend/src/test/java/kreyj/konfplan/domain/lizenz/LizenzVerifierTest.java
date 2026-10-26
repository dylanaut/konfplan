package kreyj.konfplan.domain.lizenz;

import kreyj.konfplan.domain.lizenz.LizenzFehler.Grund;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static kreyj.konfplan.domain.lizenz.LizenzTestSupport.KID;
import static kreyj.konfplan.domain.lizenz.LizenzTestSupport.PRIVATE_1;
import static kreyj.konfplan.domain.lizenz.LizenzTestSupport.PRIVATE_2;
import static kreyj.konfplan.domain.lizenz.LizenzTestSupport.b64;
import static kreyj.konfplan.domain.lizenz.LizenzTestSupport.claims;
import static kreyj.konfplan.domain.lizenz.LizenzTestSupport.header;
import static kreyj.konfplan.domain.lizenz.LizenzTestSupport.token;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LizenzVerifierTest {

    private static final long JETZT = 1_800_000_000L;

    private final Clock clock = Clock.fixed(Instant.ofEpochSecond(JETZT), ZoneOffset.UTC);
    private LizenzVerifier verifier;
    private String jkt;

    @BeforeEach
    void setup() {
        verifier = new LizenzVerifier(Map.of(KID, LizenzTestSupport.public1()), Set.of(), clock);
        jkt = InstanzSchluessel.thumbprint(LizenzTestSupport.public1());
    }

    private void assertAbgelehnt(String token, Grund erwartet) {
        assertThatThrownBy(() -> verifier.verifiziere(token, jkt))
                .isInstanceOf(LizenzFehler.class)
                .extracting(e -> ((LizenzFehler) e).getGrund())
                .isEqualTo(erwartet);
    }

    @Test
    void gueltigeLizenzWirdAkzeptiert() {
        LizenzClaims c = verifier.verifiziere(token(PRIVATE_1, header(), claims(jkt, JETZT)), jkt);

        assertThat(c.lizenzId()).isEqualTo("LIC-1");
        assertThat(c.kundenId()).isEqualTo("CUST-1");
        assertThat(c.maxInstanzen()).isEqualTo(3);
        assertThat(c.features()).containsExactly("planung");
        assertThat(c.typ()).isEqualTo(LizenzClaims.Typ.OFFLINE);
        assertThat(c.kid()).isEqualTo(KID);
        assertThat(c.gueltigBis()).isEqualTo(Instant.ofEpochSecond(JETZT + 86400));
    }

    @Test
    void audienceAlsListeWirdAkzeptiert() {
        Map<String, Object> c = claims(jkt, JETZT);
        c.put("aud", List.of("anderes", LizenzVerifier.ZIELGRUPPE));

        assertThat(verifier.verifiziere(token(PRIVATE_1, header(), c), jkt)).isNotNull();
    }

    @Test
    void signaturEinesFremdenSchluesselsWirdAbgelehnt() {
        assertAbgelehnt(token(PRIVATE_2, header(), claims(jkt, JETZT)), Grund.SIGNATUR);
    }

    @Test
    void nachtraeglichVeraenderteClaimsWerdenAbgelehnt() {
        String[] teile = token(PRIVATE_1, header(), claims(jkt, JETZT)).split("\\.");
        Map<String, Object> manipuliert = claims(jkt, JETZT);
        manipuliert.put("maxInstances", 999);
        String payload = b64(toJson(manipuliert));

        assertAbgelehnt(teile[0] + "." + payload + "." + teile[2], Grund.SIGNATUR);
    }

    @Test
    void algorithmusNoneWirdAbgelehnt() {
        Map<String, Object> h = header();
        h.put("alg", "none");
        String token = b64(toJson(h)) + "." + b64(toJson(claims(jkt, JETZT))) + ".";

        assertAbgelehnt(token, Grund.ALGORITHMUS);
    }

    @Test
    void symmetrischerAlgorithmusWirdAbgelehnt() {
        Map<String, Object> h = header();
        h.put("alg", "HS256");
        // Klassischer Algorithm-Confusion-Angriff: HMAC mit dem Public Key als Secret.
        String token = b64(toJson(h)) + "." + b64(toJson(claims(jkt, JETZT))) + "." + b64(new byte[32]);

        assertAbgelehnt(token, Grund.ALGORITHMUS);
    }

    @Test
    void kritischeHeaderParameterWerdenAbgelehnt() {
        Map<String, Object> h = header();
        h.put("crit", List.of("exp"));

        assertAbgelehnt(token(PRIVATE_1, h, claims(jkt, JETZT)), Grund.FORMAT);
    }

    @Test
    void unbekannteKidWirdAbgelehnt() {
        Map<String, Object> h = header();
        h.put("kid", "unbekannt");

        assertAbgelehnt(token(PRIVATE_1, h, claims(jkt, JETZT)), Grund.UNBEKANNTER_SCHLUESSEL);
    }

    @Test
    void fehlendeKidWirdAbgelehnt() {
        Map<String, Object> h = header();
        h.remove("kid");

        assertAbgelehnt(token(PRIVATE_1, h, claims(jkt, JETZT)), Grund.UNVOLLSTAENDIG);
    }

    @Test
    void gesperrterSchluesselWirdAbgelehnt() {
        LizenzVerifier gesperrt = new LizenzVerifier(Map.of(KID, LizenzTestSupport.public1()), Set.of(KID), clock);

        assertThatThrownBy(() -> gesperrt.verifiziere(token(PRIVATE_1, header(), claims(jkt, JETZT)), jkt))
                .isInstanceOf(LizenzFehler.class)
                .extracting(e -> ((LizenzFehler) e).getGrund())
                .isEqualTo(Grund.GESPERRTER_SCHLUESSEL);
    }

    @Test
    void abgelaufeneLizenzWirdAbgelehnt() {
        Map<String, Object> c = claims(jkt, JETZT);
        c.put("exp", JETZT - 3600);

        assertAbgelehnt(token(PRIVATE_1, header(), c), Grund.ABGELAUFEN);
    }

    @Test
    void lizenzInDerZukunftWirdAbgelehnt() {
        Map<String, Object> c = claims(jkt, JETZT);
        c.put("nbf", JETZT + 3600);

        assertAbgelehnt(token(PRIVATE_1, header(), c), Grund.NOCH_NICHT_GUELTIG);
    }

    @Test
    void lizenzEinerAnderenInstanzWirdAbgelehnt() {
        KeyPair andere = InstanzSchluessel.generiere();
        String fremderJkt = InstanzSchluessel.thumbprint(andere.getPublic());

        assertAbgelehnt(token(PRIVATE_1, header(), claims(fremderJkt, JETZT)), Grund.INSTANZ);
    }

    @Test
    void falscherAusstellerUndFalscheZielgruppeWerdenAbgelehnt() {
        Map<String, Object> iss = claims(jkt, JETZT);
        iss.put("iss", "jemand-anderes");
        Map<String, Object> aud = claims(jkt, JETZT);
        aud.put("aud", "anderes-produkt");

        assertAbgelehnt(token(PRIVATE_1, header(), iss), Grund.AUSSTELLER);
        assertAbgelehnt(token(PRIVATE_1, header(), aud), Grund.ZIELGRUPPE);
    }

    @Test
    void unbekannterTypWirdAbgelehnt() {
        Map<String, Object> c = claims(jkt, JETZT);
        c.put("type", "trial");

        assertAbgelehnt(token(PRIVATE_1, header(), c), Grund.TYP);
    }

    @Test
    void fehlendeOderUngueltigePflichtclaimsWerdenAbgelehnt() {
        for (String claim : List.of("sub", "customerId", "cnf", "exp", "iat", "maxInstances")) {
            Map<String, Object> c = claims(jkt, JETZT);
            c.remove(claim);
            assertAbgelehnt(token(PRIVATE_1, header(), c), Grund.UNVOLLSTAENDIG);
        }
        Map<String, Object> keineInstanz = claims(jkt, JETZT);
        keineInstanz.put("maxInstances", 0);
        assertAbgelehnt(token(PRIVATE_1, header(), keineInstanz), Grund.UNVOLLSTAENDIG);

        Map<String, Object> zeit = claims(jkt, JETZT);
        zeit.put("exp", Long.MAX_VALUE);
        assertAbgelehnt(token(PRIVATE_1, header(), zeit), Grund.UNVOLLSTAENDIG);
    }

    @Test
    void onlineLizenzTraegtZaehlerUndRevalidierungsfrist() {
        Map<String, Object> c = claims(jkt, JETZT);
        c.put("type", "online");
        c.put("seq", 7);
        c.put("rev", JETZT + 3600);

        LizenzClaims geprueft = verifier.verifiziere(token(PRIVATE_1, header(), c), jkt);

        assertThat(geprueft.typ()).isEqualTo(LizenzClaims.Typ.ONLINE);
        assertThat(geprueft.zaehler()).isEqualTo(7L);
        assertThat(geprueft.naechsteRevalidierung()).isEqualTo(Instant.ofEpochSecond(JETZT + 3600));
    }

    @Test
    void offlineLizenzHatKeinenZaehler() {
        LizenzClaims c = verifier.verifiziere(token(PRIVATE_1, header(), claims(jkt, JETZT)), jkt);

        assertThat(c.zaehler()).isNull();
        assertThat(c.naechsteRevalidierung()).isNull();
    }

    @Test
    void onlineLizenzOhneZaehlerOderFristOderMitFristNachAblaufWirdAbgelehnt() {
        Map<String, Object> ohneSeq = claims(jkt, JETZT);
        ohneSeq.put("type", "online");
        ohneSeq.put("rev", JETZT + 3600);
        Map<String, Object> ohneRev = claims(jkt, JETZT);
        ohneRev.put("type", "online");
        ohneRev.put("seq", 1);
        Map<String, Object> negativ = claims(jkt, JETZT);
        negativ.put("type", "online");
        negativ.put("seq", -1);
        negativ.put("rev", JETZT + 3600);
        Map<String, Object> revNachExp = claims(jkt, JETZT);
        revNachExp.put("type", "online");
        revNachExp.put("seq", 1);
        revNachExp.put("rev", JETZT + 2 * 86400);

        assertAbgelehnt(token(PRIVATE_1, header(), ohneSeq), Grund.UNVOLLSTAENDIG);
        assertAbgelehnt(token(PRIVATE_1, header(), ohneRev), Grund.UNVOLLSTAENDIG);
        assertAbgelehnt(token(PRIVATE_1, header(), negativ), Grund.UNVOLLSTAENDIG);
        assertAbgelehnt(token(PRIVATE_1, header(), revNachExp), Grund.UNVOLLSTAENDIG);
    }

    @Test
    void kaputtesFormatWirdAbgelehnt() {
        assertAbgelehnt(null, Grund.FORMAT);
        assertAbgelehnt("", Grund.FORMAT);
        assertAbgelehnt("a.b", Grund.FORMAT);
        assertAbgelehnt("a.b.c.d", Grund.FORMAT);
        assertAbgelehnt("!!!.!!!.!!!", Grund.FORMAT);
        assertAbgelehnt(b64("[]".getBytes()) + "." + b64("{}".getBytes()) + ".", Grund.FORMAT);
    }

    @Test
    void kaputteSignaturWirdAbgelehnt() {
        String[] teile = token(PRIVATE_1, header(), claims(jkt, JETZT)).split("\\.");

        assertAbgelehnt(teile[0] + "." + teile[1] + ".AAAA", Grund.SIGNATUR);
        assertAbgelehnt(teile[0] + "." + teile[1] + ".", Grund.SIGNATUR);
    }

    @Test
    void schluesselWerdenAusDerClasspathResourceGeladen() {
        LizenzVerifier ausDatei = LizenzVerifier.ausClasspath("license/test-public-keys.properties", clock);

        assertThat(ausDatei.verifiziere(token(PRIVATE_1, header(), claims(jkt, JETZT)), jkt)).isNotNull();
    }

    @Test
    void fehlendeSchluesselResourceLehntAlleLizenzenAb() {
        LizenzVerifier leer = LizenzVerifier.ausClasspath("license/gibt-es-nicht.properties", clock);

        assertThatThrownBy(() -> leer.verifiziere(token(PRIVATE_1, header(), claims(jkt, JETZT)), jkt))
                .isInstanceOf(LizenzFehler.class)
                .extracting(e -> ((LizenzFehler) e).getGrund())
                .isEqualTo(Grund.UNBEKANNTER_SCHLUESSEL);
    }

    @Test
    void thumbprintIstStabilUndInstanzspezifisch() {
        KeyPair a = InstanzSchluessel.generiere();
        KeyPair b = InstanzSchluessel.generiere();

        assertThat(InstanzSchluessel.thumbprint(a.getPublic())).isEqualTo(InstanzSchluessel.thumbprint(a.getPublic()));
        assertThat(InstanzSchluessel.thumbprint(a.getPublic())).isNotEqualTo(InstanzSchluessel.thumbprint(b.getPublic()));
        // SHA-256 als base64url ohne Padding
        assertThat(InstanzSchluessel.thumbprint(a.getPublic())).hasSize(43).matches("[A-Za-z0-9_-]+");
        assertThat(Base64.getUrlDecoder().decode(InstanzSchluessel.thumbprint(a.getPublic()))).hasSize(32);
    }

    private static byte[] toJson(Map<String, Object> map) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsBytes(map);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
