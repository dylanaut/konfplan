package kreyj.konfplan.adapter.in.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.oidc.Claim;
import io.quarkus.test.security.oidc.OidcSecurity;
import kreyj.konfplan.domain.service.KeycloakUserProvisioningService;
import jakarta.inject.Inject;
import kreyj.konfplan.domain.service.AdminService;
import kreyj.konfplan.domain.service.GebaeudeService;
import kreyj.konfplan.domain.service.ReferentService;
import kreyj.konfplan.domain.service.TeilnehmerService;
import kreyj.konfplan.domain.service.VeranstaltungService;
import kreyj.konfplan.persistence.Planungsergebnis;
import kreyj.konfplan.persistence.Raum;
import kreyj.konfplan.persistence.Referent;
import kreyj.konfplan.persistence.Slot;
import kreyj.konfplan.persistence.Teilnehmer;
import kreyj.konfplan.persistence.Veranstaltung;
import kreyj.konfplan.persistence.Vortrag;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;

import static io.restassured.RestAssured.given;

/**
 * Deckt den "Vorträge & Prioritäten"-Abschnitt des Teilnehmer-Dashboards end-to-end ab, für eine
 * Veranstaltung, die bereits ein Planungsergebnis besitzt (import via des gleichen "medium"-CSV-
 * Datensets, das DevDataInitService für die Demo-Veranstaltung verwendet). Nur mit einem
 * existierenden Planungsergebnis erreicht getPlanFuerTeilnehmer() den Code-Pfad, der die (lazy
 * geladenen) Vortrag-/Slot-/Raum-Collections der Veranstaltung tatsächlich anfasst.
 */
@QuarkusTest
class TeilnehmerVortraegeUndPrioritaetenTest extends DatabaseCleaner {

    @InjectMock
    KeycloakUserProvisioningService keycloakUserProvisioningService;

    @Inject
    GebaeudeService gebaeudeService;
    @Inject
    AdminService adminService;
    @Inject
    VeranstaltungService veranstaltungService;
    @Inject
    ReferentService referentService;
    @Inject
    TeilnehmerService teilnehmerService;
    @Inject
    ObjectMapper mapper;

    Long vid;

    @BeforeEach
    void importMediumDataset() throws Exception {
        Path basePath = Paths.get("src/test/resources/csv_import/medium");

        gebaeudeService.importGebaeudeWithRaeumeFromCsv(basePath.resolve("gebaeude.csv"));
        adminService.importAdminsFromCsv(basePath.resolve("organisatoren.csv"));
        veranstaltungService.importFromCsv(basePath.resolve("veranstaltungen.csv"));

        Veranstaltung event = QuarkusTransaction.requiringNew().call(() ->
            (Veranstaltung) Veranstaltung.find("ORDER BY id DESC").firstResult());
        vid = event.getId();

        adminService.importSlotsFromCsv(basePath.resolve("slots.csv"), vid);
        referentService.importFromCsv(basePath.resolve("referenten.csv"), vid);
        teilnehmerService.importFromCsv(basePath.resolve("teilnehmer.csv"), vid);
        adminService.importVortraegeFromCsv(basePath.resolve("wahl_vortraege.csv"), vid);
        adminService.importVortraegeFromCsv(basePath.resolve("pflicht_vortraege.csv"), vid);
        adminService.importTeilnehmerWvPriosFromCsv(basePath.resolve("tn_prios.csv"), vid);
        adminService.importRaumVerfuegbarkeitenFromCsv(basePath.resolve("raum_verfuegbarkeiten.csv"), vid);
        adminService.importNutzerVerfuegbarkeitenFromCsv(basePath.resolve("teilnehmer_verfuegbarkeiten.csv"), Teilnehmer.class, vid);
        adminService.importNutzerVerfuegbarkeitenFromCsv(basePath.resolve("ref_verfuegbarkeiten.csv"), Referent.class, vid);

        QuarkusTransaction.requiringNew().run(() -> {
            Veranstaltung veranstaltung = Veranstaltung.findById(vid);
            Teilnehmer alexAlfa = (Teilnehmer) Teilnehmer.find("loginName", "alex.alfa").firstResult();
            Vortrag wahlvortrag = (Vortrag) Vortrag.find("titel", "Traumberuf Informatiker?").firstResult();
            Slot slot = (Slot) Slot.find("veranstaltung", veranstaltung).firstResultOptional().orElseThrow();
            Raum raum = (Raum) Raum.findAll().firstResultOptional().orElseThrow();

            Planungsergebnis.MinizincResult result = new Planungsergebnis.MinizincResult();
            result.teilnehmer_oids = new long[]{alexAlfa.getId()};
            result.wahlvortrag_oids = new long[]{wahlvortrag.getId()};
            result.slot_oids = new long[]{slot.getId()};
            result.raum_oids = new long[]{raum.getId()};
            result.instanz_slot = new int[][]{{1}};
            result.instanz_raum = new int[][]{{1}};
            result.besucht = new boolean[][][]{{{true}}};
            result.guete = 100;
            result.zuweisungen = 1;
            result.raumwechsel = 0;

            Planungsergebnis pe = new Planungsergebnis();
            pe.setVeranstaltung(veranstaltung);
            pe.setJsonErgebnis(result.toJson(mapper));
            pe.persist();
        });
    }

    @Test
    @TestSecurity(user = "alex.alfa", roles = "TEILNEHMER")
    @OidcSecurity(claims = {@Claim(key = "preferred_username", value = "alex.alfa")})
    void vortraegeUndPrioritaeten_fuerTeilnehmerMitBestehendemPlan_shouldSucceed() {
        given()
            .when().get("/api/teilnehmer/veranstaltungen/{vid}/vortraege", vid)
            .then().statusCode(200);

        given()
            .when().get("/api/prios/{vid}", vid)
            .then().statusCode(200);

        given()
            .when().get("/api/teilnehmer/veranstaltungen/{vid}/zuweisungen", vid)
            .then().statusCode(200)
            .body("size()", org.hamcrest.Matchers.greaterThan(0));
    }


    @Test
    @TestSecurity(user = "alex.alfa", roles = "TEILNEHMER")
    @OidcSecurity(claims = {@Claim(key = "preferred_username", value = "alex.alfa")})
    void speichernDerPrioritaeten_ueberDenVonDerUiVerwendetenPfad_shouldPersistieren() {
        Vortrag wahlvortrag = (Vortrag) Vortrag.find("titel", "Traumberuf Informatiker?").firstResult();

        given()
            .contentType("application/json")
            .body("[{\"vortragId\": " + wahlvortrag.getId() + ", \"prioWert\": 7}]")
            .when().post("/api/prios")
            .then().statusCode(200);

        given()
            .when().get("/api/prios/{vid}", vid)
            .then().statusCode(200)
            .body("'" + wahlvortrag.getId() + "'", org.hamcrest.Matchers.equalTo(7));
    }
}
