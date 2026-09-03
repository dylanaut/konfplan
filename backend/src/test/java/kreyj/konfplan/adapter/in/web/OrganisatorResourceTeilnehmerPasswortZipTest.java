package kreyj.konfplan.adapter.in.web;

import com.opencsv.CSVReader;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.InjectMock;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.common.http.TestHTTPEndpoint;
import io.quarkus.test.h2.H2DatabaseTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import kreyj.konfplan.adapter.in.web.dto.TeilnehmerPasswortZipRequestDto;
import kreyj.konfplan.domain.exception.KeycloakProvisioningException;
import kreyj.konfplan.domain.service.KeycloakUserProvisioningService;
import kreyj.konfplan.persistence.Organisator;
import kreyj.konfplan.persistence.Teilnehmer;
import kreyj.konfplan.persistence.Veranstaltung;
import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.model.FileHeader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.time.LocalDateTime;
import java.util.List;

import static io.restassured.RestAssured.given;
import static jakarta.ws.rs.core.Response.Status.BAD_REQUEST;
import static jakarta.ws.rs.core.Response.Status.OK;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;

@QuarkusTest
@TestSecurity(user = "admin@example.com", roles = "ORGANISATOR")
@QuarkusTestResource(H2DatabaseTestResource.class)
@TestHTTPEndpoint(OrganisatorResource.class)
class OrganisatorResourceTeilnehmerPasswortZipTest extends DatabaseCleaner {

    @InjectMock
    KeycloakUserProvisioningService keycloakUserProvisioningService;

    Long veranstaltungId;
    Long teilnehmer1Id;
    Long teilnehmer2Id;


    @BeforeEach
    void setup() {
        QuarkusTransaction.requiringNew().run(() -> {
            Organisator admin = new Organisator();
            admin.assignLoginName("admin@example.com");
            admin.setEmail("admin@example.com");
            admin.persist();

            Veranstaltung v = new Veranstaltung();
            v.setName("Passwort-ZIP-Test-Event");
            v.setBeginntAm(LocalDateTime.now().plusDays(1));
            v.persist();
            veranstaltungId = v.getId();
            admin.addVeranstaltung(v);

            Teilnehmer t1 = new Teilnehmer();
            t1.assignLoginName("zip.teilnehmer1");
            t1.setFirstName("Erika");
            t1.setLastName("Musterfrau");
            t1.addGruppe("Klasse 7a");
            t1.addGruppe("AG Technik");
            t1.persist();
            t1.addVeranstaltung(v);
            teilnehmer1Id = t1.getId();

            Teilnehmer t2 = new Teilnehmer();
            t2.assignLoginName("zip.teilnehmer2");
            t2.setFirstName("Max");
            t2.setLastName("Mustermann");
            t2.persist();
            t2.addVeranstaltung(v);
            teilnehmer2Id = t2.getId();
        });
    }


    private byte[] downloadZip(List<Long> nutzerIds, String zipPassword) {
        return given()
            .contentType("application/json")
            .body(new TeilnehmerPasswortZipRequestDto(nutzerIds, zipPassword))
            .when().post("/veranstaltungen/{vid}/teilnehmer/passwoerter/zip", veranstaltungId)
            .then()
            .statusCode(OK.getStatusCode())
            .contentType("application/zip")
            .extract().asByteArray();
    }


    private List<String[]> readCsvFromZip(byte[] zipBytes, String zipPassword, Path tempDir) throws Exception {
        Path zipFile = tempDir.resolve("result.zip");
        Files.write(zipFile, zipBytes);

        byte[] csvBytes;
        try (ZipFile zip = new ZipFile(zipFile.toFile(), zipPassword.toCharArray())) {
            FileHeader header = zip.getFileHeaders().get(0);
            try (var in = zip.getInputStream(header)) {
                csvBytes = in.readAllBytes();
            }
        }

        // Fuehrendes UTF-8-BOM (fuer Excel-Kompatibilitaet mitgeschrieben) vor dem Parsen
        // entfernen, wie es auch ein echter CSV-Reader (Excel, CsvHelper) tun wuerde.
        byte[] bom = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
        byte[] withoutBom = Arrays.equals(Arrays.copyOfRange(csvBytes, 0, bom.length), bom)
            ? Arrays.copyOfRange(csvBytes, bom.length, csvBytes.length)
            : csvBytes;

        try (CSVReader csvReader = new com.opencsv.CSVReaderBuilder(
                new InputStreamReader(new ByteArrayInputStream(withoutBom), StandardCharsets.UTF_8)).withCSVParser(
                new com.opencsv.CSVParserBuilder().withSeparator(';').build()).build()) {
            return csvReader.readAll();
        }
    }


    @Test
    void happyPath_returnsEncryptedZipWithGroupColumns(@TempDir Path tempDir) throws Exception {
        byte[] zip = downloadZip(List.of(teilnehmer1Id, teilnehmer2Id), "geheimgeheim");
        assertThat(zip).isNotEmpty();

        List<String[]> rows = readCsvFromZip(zip, "geheimgeheim", tempDir);
        // Header + 2 Datenzeilen; positionsbasierte Gruppen-Spalten: max. Gruppenanzahl ist 2
        // (Teilnehmer1 hat 2 Gruppen, Teilnehmer2 keine).
        assertThat(rows.get(0)).containsExactly("Name", "Login", "Temporäres Passwort", "Gruppe 1", "Gruppe 2");
        assertThat(rows).hasSize(3);

        String[] zeile1 = rows.stream().filter(r -> r[1].equals("zip.teilnehmer1")).findFirst().orElseThrow();
        assertThat(zeile1[0]).isEqualTo("Musterfrau, Erika");
        assertThat(zeile1[2]).isNotBlank();
        assertThat(zeile1).contains("AG Technik", "Klasse 7a");

        String[] zeile2 = rows.stream().filter(r -> r[1].equals("zip.teilnehmer2")).findFirst().orElseThrow();
        assertThat(zeile2[3]).isEmpty();
        assertThat(zeile2[4]).isEmpty();
    }


    @Test
    void partialFailure_stillReturnsZipAndListsFailedLogin() {
        Teilnehmer t2 = Teilnehmer.findById(teilnehmer2Id);
        doThrow(new KeycloakProvisioningException("Keycloak nicht erreichbar"))
            .when(keycloakUserProvisioningService).resetPassword(eq(t2), any());

        byte[] zip = given()
            .contentType("application/json")
            .body(new TeilnehmerPasswortZipRequestDto(List.of(teilnehmer1Id, teilnehmer2Id), "geheimgeheim"))
            .when().post("/veranstaltungen/{vid}/teilnehmer/passwoerter/zip", veranstaltungId)
            .then()
            .statusCode(OK.getStatusCode())
            .contentType("application/zip")
            .header("X-KonfPlan-Failed-Teilnehmer", org.hamcrest.Matchers.equalTo("zip.teilnehmer2"))
            .extract().asByteArray();

        assertThat(zip).isNotEmpty();
    }


    @Test
    void totalFailure_returnsBadRequest() {
        Teilnehmer t1 = Teilnehmer.findById(teilnehmer1Id);
        Teilnehmer t2 = Teilnehmer.findById(teilnehmer2Id);
        doThrow(new KeycloakProvisioningException("Keycloak nicht erreichbar"))
            .when(keycloakUserProvisioningService).resetPassword(eq(t1), any());
        doThrow(new KeycloakProvisioningException("Keycloak nicht erreichbar"))
            .when(keycloakUserProvisioningService).resetPassword(eq(t2), any());

        given()
            .contentType("application/json")
            .body(new TeilnehmerPasswortZipRequestDto(List.of(teilnehmer1Id, teilnehmer2Id), "geheimgeheim"))
            .when().post("/veranstaltungen/{vid}/teilnehmer/passwoerter/zip", veranstaltungId)
            .then()
            .statusCode(BAD_REQUEST.getStatusCode());
    }


    @Test
    void teilnehmerNotInVeranstaltung_returnsBadRequest() {
        Long[] otherTeilnehmerId = {0L};
        QuarkusTransaction.requiringNew().run(() -> {
            Teilnehmer t = new Teilnehmer();
            t.assignLoginName("zip.fremd");
            t.persist();
            otherTeilnehmerId[0] = t.getId();
        });

        given()
            .contentType("application/json")
            .body(new TeilnehmerPasswortZipRequestDto(List.of(otherTeilnehmerId[0]), "geheimgeheim"))
            .when().post("/veranstaltungen/{vid}/teilnehmer/passwoerter/zip", veranstaltungId)
            .then()
            .statusCode(BAD_REQUEST.getStatusCode());
    }


    @Test
    void shortZipPassword_returnsBadRequest() {
        given()
            .contentType("application/json")
            .body(new TeilnehmerPasswortZipRequestDto(List.of(teilnehmer1Id), "zu kurz"))
            .when().post("/veranstaltungen/{vid}/teilnehmer/passwoerter/zip", veranstaltungId)
            .then()
            .statusCode(BAD_REQUEST.getStatusCode());
    }
}
