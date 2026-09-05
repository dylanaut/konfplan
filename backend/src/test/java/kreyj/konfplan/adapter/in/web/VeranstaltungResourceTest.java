package kreyj.konfplan.adapter.in.web;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.InjectMock;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.common.http.TestHTTPEndpoint;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.h2.H2DatabaseTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import kreyj.konfplan.domain.service.KeycloakUserProvisioningService;
import io.restassured.http.ContentType;
import jakarta.transaction.Transactional;
import kreyj.konfplan.persistence.Organisator;
import kreyj.konfplan.persistence.Neigung;
import kreyj.konfplan.persistence.Nutzer;
import kreyj.konfplan.persistence.Planungsergebnis;
import kreyj.konfplan.persistence.Referent;
import kreyj.konfplan.persistence.Slot;
import kreyj.konfplan.persistence.Teilnehmer;
import kreyj.konfplan.persistence.Veranstaltung;
import kreyj.konfplan.persistence.Wahlvortrag;
import kreyj.konfplan.adapter.in.web.dto.NutzerDto;
import kreyj.konfplan.adapter.in.web.dto.VeranstaltungDto;
import kreyj.konfplan.adapter.in.web.dto.VortragDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URL;
import java.time.LocalDateTime;
import java.util.Set;

import static io.restassured.RestAssured.given;
import static jakarta.ws.rs.core.Response.Status.BAD_REQUEST;
import static jakarta.ws.rs.core.Response.Status.CONFLICT;
import static jakarta.ws.rs.core.Response.Status.CREATED;
import static jakarta.ws.rs.core.Response.Status.NO_CONTENT;
import static jakarta.ws.rs.core.Response.Status.OK;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.CoreMatchers.is;

@QuarkusTest
@TestSecurity(user = "admin@test.de", roles = "ORGANISATOR")
@QuarkusTestResource(H2DatabaseTestResource.class)
@TestHTTPEndpoint(VeranstaltungResource.class)
class VeranstaltungResourceTest extends DatabaseCleaner {

    @InjectMock
    KeycloakUserProvisioningService keycloakUserProvisioningService;

    @TestHTTPResource
    @TestHTTPEndpoint(OrganisatorResource.class)
    URL adminEndpoint;

    Long testVid;


    @BeforeEach
    @Transactional
    void setup() {
        Organisator admin = new Organisator();
        admin.assignLoginName("admintest");
        admin.setEmail("admin@test.de");
        admin.persist();

        Veranstaltung v = new Veranstaltung();
        v.setName("Test Event " + System.currentTimeMillis());
        v.setBeginntAm(LocalDateTime.now());
        v.persist();
        testVid = v.getId();

        admin.addVeranstaltung(v);
        admin.persist();
    }


    @Test
    void testGetVortraegeHierarchical() {
        createWahlvortrag("Test Vortrag");

        given()
                .when().get("/{vid}/vortraege", testVid)
                .then()
                .statusCode(OK.getStatusCode())
                .body("size()", is(1))
                .body("[0].titel", is("Test Vortrag"));
    }


    @Test
    void testListePlanungsergebnisse_leerOhnePlan() {
        given()
            .when().get("/{vid}/planungsergebnisse", testVid)
            .then()
            .statusCode(OK.getStatusCode())
            .body("size()", is(0));
    }


    @Test
    void testListePlanungsergebnisse_zeigtAlleUnabhaengigVomPublikationsstatus() {
        createPlanungsergebnis("erster", false);
        createPlanungsergebnis("zweiter", true);

        given()
            .when().get("/{vid}/planungsergebnisse", testVid)
            .then()
            .statusCode(OK.getStatusCode())
            .body("size()", is(2));
    }


    @Test
    void testPubliziereErgebnis_entziehtVorherigemDenStatus() {
        Long ersteId = createPlanungsergebnis("erster", true);
        Long zweiteId = createPlanungsergebnis("zweiter", false);

        given()
            .when().put("/{vid}/planungsergebnisse/{ergebnisId}/publizieren", testVid, zweiteId)
            .then()
            .statusCode(OK.getStatusCode());

        given()
            .when().get("/{vid}/planungsergebnisse", testVid)
            .then()
            .statusCode(OK.getStatusCode())
            .body("find { it.id == " + ersteId + " }.publiziert", is(false))
            .body("find { it.id == " + zweiteId + " }.publiziert", is(true));
    }


    @Test
    void testLoescheErgebnis_veroeffentlichtesWirdAbgelehnt() {
        Long id = createPlanungsergebnis("erster", true);

        given()
            .when().delete("/{vid}/planungsergebnisse/{ergebnisId}", testVid, id)
            .then()
            .statusCode(BAD_REQUEST.getStatusCode());
    }


    @Test
    void testLoescheErgebnis_unveroeffentlichtesWirdGeloescht() {
        Long id = createPlanungsergebnis("erster", false);

        given()
            .when().delete("/{vid}/planungsergebnisse/{ergebnisId}", testVid, id)
            .then()
            .statusCode(NO_CONTENT.getStatusCode());
    }


    @Transactional
    Long createPlanungsergebnis(String ersteller, boolean publiziert) {
        Veranstaltung v = Veranstaltung.findById(testVid);
        Planungsergebnis pe = new Planungsergebnis();
        pe.setVeranstaltung(v);
        pe.setErsteller(ersteller);
        pe.setErstelltAm(LocalDateTime.now());
        pe.setPubliziert(publiziert);
        pe.setJsonErgebnis("{\"guete\": 42}");
        pe.persist();
        return pe.getId();
    }


    @Test
    void testSendeNachricht_anMitgliedDerVeranstaltung_wirdZugestellt() {
        Long empfaengerId = createTeilnehmerInVeranstaltung("nachrichten-tn");

        given()
            .contentType(ContentType.JSON)
            .body("{\"empfaengerIds\": [" + empfaengerId + "], \"titel\": \"Hallo\", \"inhalt\": \"Wichtige Info\"}")
            .when().post("/{vid}/nachrichten", testVid)
            .then()
            .statusCode(OK.getStatusCode());
    }


    @Test
    void testSendeNachricht_anFremdenNutzer_wirdAbgelehnt() {
        Long fremderId = createTeilnehmer("fremder-tn");

        given()
            .contentType(ContentType.JSON)
            .body("{\"empfaengerIds\": [" + fremderId + "], \"titel\": \"Hallo\", \"inhalt\": \"Wichtige Info\"}")
            .when().post("/{vid}/nachrichten", testVid)
            .then()
            .statusCode(BAD_REQUEST.getStatusCode());
    }


    @Transactional
    Long createTeilnehmerInVeranstaltung(String loginName) {
        Long id = createTeilnehmer(loginName);
        Teilnehmer tn = Teilnehmer.findById(id);
        tn.addVeranstaltung(Veranstaltung.findById(testVid));
        return id;
    }


    @Transactional
    Long createTeilnehmer(String loginName) {
        Teilnehmer tn = new Teilnehmer();
        tn.assignLoginName(loginName);
        tn.setEmail(loginName + "@test.de");
        tn.persist();
        return tn.getId();
    }


    @Transactional
    public void createWahlvortrag(String titel) {
        Referent r = new Referent();
        String uniqueSuffix = System.nanoTime() + "";
        r.assignLoginName("ref-" + uniqueSuffix);
        r.setEmail("ref-" + uniqueSuffix + "@vresource.de");
        r.setLastName("Mustermann");
        r.persist();

        Wahlvortrag v = new Wahlvortrag();
        v.setTitel(titel);
        v.setReferent(r);
        v.setVeranstaltung(Veranstaltung.findById(testVid));
        v.persist();
    }


    @Test
    void testGetSlotsHierarchical() {
        LocalDateTime now = LocalDateTime.now();

        QuarkusTransaction.requiringNew().run(() -> {
            Veranstaltung veranstaltung = Veranstaltung.findById(testVid);
            Slot s1 = new Slot("Slot A", now, now.plusHours(1), veranstaltung);
            s1.persistAndFlush();
            veranstaltung.addSlot(s1);
        });

        given().when()
                .get("/{vid}/slots", testVid)
                .then()
                .statusCode(OK.getStatusCode())
                .body("size()", is(1))
                .body("[0].description", is("Slot A"));
    }


    @Test
    void testGetStatsHierarchical() {
        createWahlvortrag("Vortrag 1");

        given()
                .when().get("/{vid}/stats", testVid)
                .then()
                .statusCode(OK.getStatusCode())
                .body("size()", is(1))
                .body("[0].titel", is("Vortrag 1"));
    }


    @Test
    void testCreateNutzerHierarchical() {
        String nutzerEmail = "new@test.de";
        NutzerDto tn = NutzerDto.teilnehmer(nutzerEmail, "Neu", "Nutzer");
        tn.loginName = "newtest";

        given().contentType(ContentType.JSON)
                .body(tn)
                .when().post("/{vid}/nutzer", testVid)
                .then()
                .statusCode(CREATED.getStatusCode())
                .body("email", is(nutzerEmail));

        assertThat(Nutzer.findByEmail(nutzerEmail)).isNotNull();
    }


    @Test
    void testOptimisticLockingForVortrag() {
        final String referentEmail = "referent@test.de";

        // 1. Create a Referent and a Wahlvortrag
        NutzerDto refDto = NutzerDto.referent(referentEmail, "Referent", "Test");
        refDto.loginName = referentEmail;
        NutzerDto referent =
                given()
                    .baseUri(adminEndpoint.toString())
                    .basePath("/nutzer")
                    .contentType(ContentType.JSON)
                        .body(refDto)
                        .when().post()
                        .then()
                        .statusCode(OK.getStatusCode())
                        .extract().as(NutzerDto.class);

        VortragDto wvDto = new VortragDto(false, "Original Vortrag Titel", "Inhalt",
            referent.id, testVid);
        VortragDto w =
                given().contentType(ContentType.JSON)
                        .body(wvDto)
                        .when().post("/{vid}/vortraege", testVid)
                        .then()
                        .statusCode(CREATED.getStatusCode())
                        .extract().as(VortragDto.class);

        // 2. Organisator (via @TestSecurity) fetches vortrag data
        Long vortragId = w.id;
        VortragDto adminFetchedVortrag = given()
                .when()
                .get("/{vid}/vortraege/{vortragId}", testVid, vortragId)
                .then()
                .statusCode(OK.getStatusCode())
                .extract().as(VortragDto.class);
        assertThat(adminFetchedVortrag.version).isNotNull().isEqualTo(0L);

        // 3. Zweiter Fetch (simuliert eine zweite, parallele Session) vor Admins Update
        VortragDto referentFetchedVortrag = given()
                .when().get("/{vid}/vortraege/{vortragId}", testVid, vortragId)
                .then()
                .statusCode(OK.getStatusCode())
                .extract().as(VortragDto.class);

        // Ensure versions are the same initially
        assertThat(referentFetchedVortrag.version).isEqualTo(adminFetchedVortrag.version);

        // 4. Organisator updates vortrag title (successful, increments version)
        adminFetchedVortrag.titel = "Organisator Updated Vortrag Titel";

        VortragDto adminUpdate =
                given().contentType(ContentType.JSON)
                        .body(adminFetchedVortrag)
                        .when()
                        .put("/{vid}/vortraege/{vortragId}",
                                testVid, vortragId)
                        .then()
                        .statusCode(OK.getStatusCode())
                        .extract().as(VortragDto.class);

        assertThat(adminUpdate.titel).isEqualTo("Organisator Updated Vortrag Titel");
        assertThat(adminUpdate.version).isEqualTo(adminFetchedVortrag.version + 1);

        // 5. Zweite Session versucht Update mit veralteter Version (muss CONFLICT liefern)
        referentFetchedVortrag.titel = "Referent Updated Vortrag Titel"; // This change should not be saved
        given()
                .contentType(ContentType.JSON)
                .body(referentFetchedVortrag) // This DTO has the old version
                .when()
                .put("/{vid}/vortraege/{vortragId}", testVid, vortragId)
                .then()
                .statusCode(CONFLICT.getStatusCode()); // Expect conflict

        // 6. Verify data integrity: only Organisator's changes should be present
        VortragDto finalVortrag = given().when()
                .get("/{vid}/vortraege/{vortragId}", testVid, vortragId)
                .then()
                .statusCode(OK.getStatusCode())
                .extract().as(VortragDto.class);

        assertThat(finalVortrag.titel).isEqualTo(adminUpdate.titel);
        assertThat(finalVortrag.version)
                .describedAs("Version should be the same as admin's update")
                .isEqualTo(adminUpdate.version);
    }


    @Test
    void testWahlvortrag_NeigungenRoundTripAndReplace() {
        final String referentEmail = "neigungen-referent@test.de";
        NutzerDto refDto = NutzerDto.referent(referentEmail, "Referent", "Test");
        refDto.loginName = referentEmail;
        NutzerDto referent =
                given()
                        .baseUri(adminEndpoint.toString())
                        .basePath("/nutzer")
                        .contentType(ContentType.JSON)
                        .body(refDto)
                        .when().post()
                        .then()
                        .statusCode(OK.getStatusCode())
                        .extract().as(NutzerDto.class);

        VortragDto wvDto = new VortragDto(false, "Vortrag mit Neigungen", "Inhalt", referent.id, testVid);
        wvDto.neigungen = Set.of(Neigung.SOZIAL, Neigung.ORGANISATORISCH);

        VortragDto created =
                given().contentType(ContentType.JSON)
                        .body(wvDto)
                        .when().post("/{vid}/vortraege", testVid)
                        .then()
                        .statusCode(CREATED.getStatusCode())
                        .extract().as(VortragDto.class);

        assertThat(created.neigungen).containsExactlyInAnyOrder(Neigung.SOZIAL, Neigung.ORGANISATORISCH);

        // Update mit anderer Auswahl muss die vorherige vollstaendig ersetzen (Checkbox-UI).
        created.neigungen = Set.of(Neigung.TECHNISCH);
        VortragDto updated =
                given().contentType(ContentType.JSON)
                        .body(created)
                        .when().put("/{vid}/vortraege/{vortragId}", testVid, created.id)
                        .then()
                        .statusCode(OK.getStatusCode())
                        .extract().as(VortragDto.class);

        assertThat(updated.neigungen).containsExactly(Neigung.TECHNISCH);
    }


    @Test
    void testUpdateVeranstaltung() {
        // 1. Veranstaltung abrufen
        VeranstaltungDto fetchedVeranstaltung = given().when()
                .get("/{id}", testVid)
                .then()
                .statusCode(OK.getStatusCode())
                .extract().as(VeranstaltungDto.class);

        // Sicherstellen, dass eine Version vorhanden ist
        assertThat(fetchedVeranstaltung.version).isNotNull();
        Long initialVersion = fetchedVeranstaltung.version;

        // 2. Veranstaltung aktualisieren
        String updatedName = "Updated Event Name " + System.currentTimeMillis();
        fetchedVeranstaltung.setName(updatedName);

        given().contentType(ContentType.JSON)
                .body(fetchedVeranstaltung)
                .when()
                .put("/{id}", testVid)
                .then()
                .statusCode(OK.getStatusCode())
                .body("name", is(updatedName))
                .body("version", is(initialVersion.intValue() + 1)); // Version sollte inkrementiert werden

        // 3. Überprüfen, ob die Änderungen persistent sind
        VeranstaltungDto finalVeranstaltung = given()
                .when().get("/{id}", testVid)
                .then()
                .statusCode(OK.getStatusCode())
                .extract().as(VeranstaltungDto.class);

        assertThat(finalVeranstaltung.getName()).isEqualTo(updatedName);
        assertThat(finalVeranstaltung.version).isEqualTo(initialVersion + 1);
    }

    @Test
    void testOptimisticLockingForVeranstaltungUpdate() {
        final Long[] vIdArray = {0L};

        QuarkusTransaction.requiringNew().run(() -> {
            Organisator organisator = new Organisator();
            organisator.assignLoginName("opt-lock-organisator" + System.nanoTime());
            organisator.setEmail(organisator.getLoginName() + "@test.de");
            organisator.persist();

            Veranstaltung v = new Veranstaltung();
            v.setName("Original Event Name");
            v.setBeginntAm(LocalDateTime.now().plusDays(1));
            v.setEndetAm(LocalDateTime.now().plusDays(2));
            v.persist();
            vIdArray[0] = v.getId();

            organisator.addVeranstaltung(v);
            organisator.persist();
        });
        Long vId = vIdArray[0];

        given()
            .when().get()
            .then()
            .statusCode(OK.getStatusCode())
            .extract().body().jsonPath().getList(".", VeranstaltungDto.class);

        // 2. Organisator 1 (via @TestSecurity) fetches veranstaltung data
        VeranstaltungDto admin1FetchedVeranstaltung = given()
            .when().get("/{id}", vId)
            .then()
            .statusCode(OK.getStatusCode())
            .extract().as(VeranstaltungDto.class);

        // 3. Zweite Session fetcht veranstaltung data vor Organisator 1's Update
        VeranstaltungDto admin2FetchedVeranstaltung = given()
            .when().get("/{id}", vId)
            .then()
            .statusCode(OK.getStatusCode())
            .extract().as(VeranstaltungDto.class);

        // Ensure versions are the same initially
        assertThat(admin1FetchedVeranstaltung.version).isEqualTo(admin2FetchedVeranstaltung.version);
        assertThat(admin1FetchedVeranstaltung.version).isNotNull();

        // 4. Organisator 1 updates veranstaltung name (successful, increments version)
        admin1FetchedVeranstaltung.setName("Admin1 Updated Event Name");
        VeranstaltungDto updatedVDto = given()
            .contentType(ContentType.JSON)
            .body(admin1FetchedVeranstaltung)
            .when().put("/{id}", vId)
            .then()
            .statusCode(OK.getStatusCode())
            .extract().as(VeranstaltungDto.class);

        assertThat(updatedVDto.getName()).isEqualTo("Admin1 Updated Event Name");
        assertThat(updatedVDto.version)
            .describedAs("Version should be incremented")
            .isEqualTo(admin1FetchedVeranstaltung.version + 1);

        // 5. Zweite Session versucht Update mit veralteter Version (muss CONFLICT liefern)
        admin2FetchedVeranstaltung.setName("Admin2 Updated Event Name"); // This change should not be saved
        given()
            .contentType(ContentType.JSON)
            .body(admin2FetchedVeranstaltung) // This DTO has the old version
            .when().put("/{id}", vId)
            .then()
            .statusCode(CONFLICT.getStatusCode());

        // 6. Verify data integrity: only Organisator 1's changes should be present
        VeranstaltungDto finalVeranstaltung = given()
            .when().get("/{id}", vId)
            .then()
            .statusCode(OK.getStatusCode())
            .extract().as(VeranstaltungDto.class);

        assertThat(finalVeranstaltung.getName()).isEqualTo("Admin1 Updated Event Name");
        assertThat(finalVeranstaltung.version)
            .describedAs("Version should be the one after Organisator 1's update")
            .isEqualTo(admin1FetchedVeranstaltung.version + 1);
    }
}
