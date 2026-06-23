package kreyj.konfplan.presentation.testprofiles;

import io.quarkus.test.junit.QuarkusTestProfile;

public class MediumTestdataProfile implements QuarkusTestProfile {
    @Override
    public java.util.Map<String, String> getConfigOverrides() {
        return java.util.Map.of(
                "konfplan.dev-data.init", "true",
                "konfplan.dev-data.datasets", "medium"
        );
    }
}
