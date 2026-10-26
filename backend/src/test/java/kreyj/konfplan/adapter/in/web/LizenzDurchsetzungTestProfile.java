package kreyj.konfplan.adapter.in.web;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.Map;

/** Schaltet die Lizenz-Schreibsperre scharf (siehe LizenzFilter); im Standard-Testprofil ist sie aus. */
public class LizenzDurchsetzungTestProfile implements QuarkusTestProfile {
    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of("konfplan.lizenz.durchsetzung", "true");
    }
}
