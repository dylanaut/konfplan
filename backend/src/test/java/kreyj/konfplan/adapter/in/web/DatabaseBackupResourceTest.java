package kreyj.konfplan.adapter.in.web;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.oidc.Claim;
import io.quarkus.test.security.oidc.OidcSecurity;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static jakarta.ws.rs.core.Response.Status.FORBIDDEN;
import static jakarta.ws.rs.core.Response.Status.OK;
import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
@TestProfile(PgDumpStubTestProfile.class)
class DatabaseBackupResourceTest {

    private static final String EXPORT_PATH = "/api/administrator/backup/export";


    @Test
    @TestSecurity(user = "tom.teilnehmer", roles = "TEILNEHMER")
    @OidcSecurity(claims = {@Claim(key = "preferred_username", value = "tom.teilnehmer")})
    void export_alsTeilnehmer_wirdAbgelehnt() {
        given()
            .when().get(EXPORT_PATH)
            .then()
            .statusCode(FORBIDDEN.getStatusCode());
    }


    @Test
    @TestSecurity(user = "otto.organisator", roles = "ORGANISATOR")
    @OidcSecurity(claims = {@Claim(key = "preferred_username", value = "otto.organisator")})
    void export_alsOrganisatorOhneAdministratorRolle_wirdAbgelehnt() {
        given()
            .when().get(EXPORT_PATH)
            .then()
            .statusCode(FORBIDDEN.getStatusCode());
    }


    @Test
    @TestSecurity(user = "anna.administrator", roles = "ADMINISTRATOR")
    @OidcSecurity(claims = {@Claim(key = "preferred_username", value = "anna.administrator")})
    void export_alsAdministrator_liefertZipMitBeidenDumps() throws Exception {
        byte[] zipBytes = given()
            .when().get(EXPORT_PATH)
            .then()
            .statusCode(OK.getStatusCode())
            .header("Content-Disposition", org.hamcrest.Matchers.containsString("konfplan-backup_"))
            .extract().asByteArray();

        Map<String, String> entries = new HashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                entries.put(entry.getName(), new String(zip.readAllBytes(), StandardCharsets.UTF_8).trim());
            }
        }

        assertThat(entries).containsOnlyKeys("konfplan.dump", "keycloak.dump");
        assertThat(entries.get("konfplan.dump")).endsWith("konfplan");
        assertThat(entries.get("keycloak.dump")).endsWith("keycloak");
    }
}
