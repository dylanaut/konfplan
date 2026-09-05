package kreyj.konfplan.adapter.in.web;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.io.File;
import java.net.URL;
import java.util.Map;

/**
 * Ersetzt pg_dump durch ein Stub-Skript (siehe src/test/resources/pg_dump_stub.sh), da in der
 * Test-Pipeline nur H2 zur Verfügung steht - ein echter Postgres mit konfplan- und
 * Keycloak-Datenbank existiert dort nicht (siehe DatabaseBackupResourceTest).
 */
public class PgDumpStubTestProfile implements QuarkusTestProfile {
    @Override
    public Map<String, String> getConfigOverrides() {
        URL resource = getClass().getClassLoader().getResource("pg_dump_stub.sh");
        File script = new File(resource.getPath());
        // Maven-Ressourcenkopie/Checkout bewahrt das Ausführ-Bit nicht zuverlässig.
        script.setExecutable(true);
        return Map.of("pg-dump.path", script.getAbsolutePath());
    }
}
