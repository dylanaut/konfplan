package kreyj.konfplan.domain.lizenz;

import kreyj.konfplan.domain.lizenz.LizenzFehler.Grund;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.KeyPair;
import java.security.PublicKey;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Ruft tools/lizenz/LizenzTool.java wirklich auf und prüft die ausgestellte Lizenz mit dem echten
 * LizenzVerifier - so fällt es auf, wenn Werkzeug und Verifier (Claims, Thumbprint, Format)
 * auseinanderlaufen.
 */
class LizenzToolTest {

    private record Ergebnis(int exit, String ausgabe) {
    }

    private static final Path TOOL = Path.of("..", "tools", "lizenz", "LizenzTool.java").toAbsolutePath().normalize();

    @TempDir
    Path dir;

    private KeyPair instanz;
    private Path anforderung;

    @BeforeEach
    void setup() throws IOException {
        instanz = InstanzSchluessel.generiere();
        anforderung = dir.resolve("anforderung.json");
        Files.writeString(anforderung, anforderungJson(InstanzSchluessel.thumbprint(instanz.getPublic())));
    }

    private String anforderungJson(String thumbprint) {
        return "{\"instanzId\":\"11111111-2222-3333-4444-555555555555\",\"publicKey\":\""
            + Base64.getEncoder().encodeToString(instanz.getPublic().getEncoded())
            + "\",\"thumbprint\":\"" + thumbprint + "\"}";
    }

    private Ergebnis tool(String... args) throws Exception {
        List<String> cmd = new java.util.ArrayList<>(List.of(Path.of(System.getProperty("java.home"), "bin", "java").toString(), TOOL.toString()));
        cmd.addAll(List.of(args));
        Process p = new ProcessBuilder(cmd).directory(dir.toFile()).redirectErrorStream(true).start();
        String ausgabe = new String(p.getInputStream().readAllBytes());
        assertThat(p.waitFor(120, TimeUnit.SECONDS)).isTrue();
        return new Ergebnis(p.exitValue(), ausgabe);
    }

    /** Erzeugt den Schlüssel und liefert die Zeile "kid=publicKey" aus der Ausgabe. */
    private PublicKey keygen(String kid) throws Exception {
        Ergebnis e = tool("keygen", "--key", "signing.key", "--kid", kid);
        assertThat(e.exit()).as(e.ausgabe()).isZero();
        String zeile = e.ausgabe().lines().filter(l -> l.startsWith(kid + "=")).findFirst().orElseThrow();
        return InstanzSchluessel.publicKeyAusBase64(zeile.substring(kid.length() + 1));
    }

    private Ergebnis ausstellen(String... zusatz) throws Exception {
        List<String> args = new java.util.ArrayList<>(List.of("ausstellen", "--key", "signing.key", "--kid", "2026-1",
            "--anforderung", anforderung.toString(), "--kunde", "Schule \"X\"", "--lizenz-id", "LIC-2026-001",
            "--tage", "365", "--max-instanzen", "2", "--features", "planung, import", "--out", "license.lic"));
        args.addAll(List.of(zusatz));
        return tool(args.toArray(String[]::new));
    }

    @Test
    void ausgestellteLizenzWirdVomVerifierAkzeptiert() throws Exception {
        PublicKey signing = keygen("2026-1");
        Ergebnis e = ausstellen();
        assertThat(e.exit()).as(e.ausgabe()).isZero();

        String lizenz = Files.readString(dir.resolve("license.lic")).trim();
        LizenzVerifier verifier = new LizenzVerifier(Map.of("2026-1", signing), Set.of(), Clock.systemUTC());
        LizenzClaims c = verifier.verifiziere(lizenz, InstanzSchluessel.thumbprint(instanz.getPublic()));

        assertThat(c.lizenzId()).isEqualTo("LIC-2026-001");
        assertThat(c.kundenId()).isEqualTo("Schule \"X\"");
        assertThat(c.maxInstanzen()).isEqualTo(2);
        assertThat(c.features()).containsExactlyInAnyOrder("planung", "import");
        assertThat(c.typ()).isEqualTo(LizenzClaims.Typ.OFFLINE);
        assertThat(c.gueltigBis()).isBetween(Instant.now().plusSeconds(364L * 86400), Instant.now().plusSeconds(366L * 86400));
    }

    @Test
    void lizenzGiltNurFuerDieAngeforderteInstanz() throws Exception {
        PublicKey signing = keygen("2026-1");
        assertThat(ausstellen().exit()).isZero();

        String lizenz = Files.readString(dir.resolve("license.lic")).trim();
        LizenzVerifier verifier = new LizenzVerifier(Map.of("2026-1", signing), Set.of(), Clock.systemUTC());
        String fremd = InstanzSchluessel.thumbprint(InstanzSchluessel.generiere().getPublic());

        assertThatThrownBy(() -> verifier.verifiziere(lizenz, fremd))
            .isInstanceOf(LizenzFehler.class)
            .extracting(x -> ((LizenzFehler) x).getGrund()).isEqualTo(Grund.INSTANZ);
    }

    @Test
    void ausstellungWirdProtokolliert() throws Exception {
        keygen("2026-1");
        assertThat(ausstellen().exit()).isZero();

        assertThat(Files.readString(dir.resolve("lizenz-audit.log")))
            .contains("lizenz=LIC-2026-001")
            .contains("instanz=11111111-2222-3333-4444-555555555555")
            .contains("kid=2026-1")
            .contains("maxInstanzen=2");
    }

    @Test
    void veraenderteAnforderungsdateiWirdAbgelehnt() throws Exception {
        keygen("2026-1");
        Files.writeString(anforderung, anforderungJson("x".repeat(43)));

        Ergebnis e = ausstellen();

        assertThat(e.exit()).isEqualTo(1);
        assertThat(e.ausgabe()).contains("Thumbprint");
        assertThat(dir.resolve("license.lic")).doesNotExist();
    }

    @Test
    void schluesseldateiMitZuWeitenRechtenWirdAbgelehnt() throws Exception {
        keygen("2026-1");
        Files.setPosixFilePermissions(dir.resolve("signing.key"), PosixFilePermissions.fromString("rw-r--r--"));

        Ergebnis e = ausstellen();

        assertThat(e.exit()).isEqualTo(1);
        assertThat(e.ausgabe()).contains("chmod 600");
        assertThat(dir.resolve("license.lic")).doesNotExist();
    }

    @Test
    void keygenUeberschreibtKeinenBestehendenSchluessel() throws Exception {
        keygen("2026-1");
        String vorher = Files.readString(dir.resolve("signing.key"));

        Ergebnis e = tool("keygen", "--key", "signing.key", "--kid", "2026-1");

        assertThat(e.exit()).isEqualTo(1);
        assertThat(Files.readString(dir.resolve("signing.key"))).isEqualTo(vorher);
        assertThat(Files.getPosixFilePermissions(dir.resolve("signing.key"))).isEqualTo(PosixFilePermissions.fromString("rw-------"));
    }

    @Test
    void ungueltigeEingabenWerdenAbgelehnt() throws Exception {
        keygen("2026-1");

        assertThat(ausstellen("--tage", "0").exit()).isEqualTo(1);
        assertThat(ausstellen("--tage", "999999").exit()).isEqualTo(1);
        assertThat(ausstellen("--max-instanzen", "abc").exit()).isEqualTo(1);
        assertThat(tool("keygen", "--key", "k2.key", "--kid", "mit leerzeichen").exit()).isEqualTo(1);
        assertThat(tool("unbekannt").exit()).isEqualTo(1);
        assertThat(dir.resolve("license.lic")).doesNotExist();
    }
}
