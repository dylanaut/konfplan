package kreyj.konfplan.adapter.in.web;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import kreyj.konfplan.domain.lizenz.FakeLizenzServer;
import kreyj.konfplan.domain.lizenz.FakeLizenzServer.Lizenz;

import java.time.Instant;
import java.util.Map;

/** Startet den Fake-Lizenzserver und haengt ihn per konfplan.lizenz.server-url in die Anwendung ein. */
public class FakeLizenzServerResource implements QuarkusTestResourceLifecycleManager {

    public static final String SCHLUESSEL = "TEST-KEY";
    static volatile FakeLizenzServer fake;

    @Override
    public Map<String, String> start() {
        fake = new FakeLizenzServer(Instant::now);
        fake.lizenz(SCHLUESSEL, new Lizenz("LIC-ONLINE", "Schule Online", 1));
        return Map.of("konfplan.lizenz.server-url", fake.url());
    }

    @Override
    public void stop() {
        if (null != fake) {
            fake.close();
        }
    }
}
