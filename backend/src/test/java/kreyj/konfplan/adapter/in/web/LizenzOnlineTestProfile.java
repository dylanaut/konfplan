package kreyj.konfplan.adapter.in.web;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.List;
import java.util.Map;

/** Scharfe Schreibsperre plus ein (Fake-)Lizenzserver - fuer die Online-Endpunkte von LizenzResource. */
public class LizenzOnlineTestProfile implements QuarkusTestProfile {
    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of("konfplan.lizenz.durchsetzung", "true");
    }

    @Override
    public List<TestResourceEntry> testResources() {
        return List.of(new TestResourceEntry(FakeLizenzServerResource.class));
    }
}
