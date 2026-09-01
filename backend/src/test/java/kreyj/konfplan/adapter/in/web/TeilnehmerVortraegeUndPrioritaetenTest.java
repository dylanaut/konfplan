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
import kreyj.konfplan.persistence.Prioritaet;
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


    @Test
    @TestSecurity(user = "alex.alfa", roles = "TEILNEHMER")
    @OidcSecurity(claims = {@Claim(key = "preferred_username", value = "alex.alfa")})
    void speichernDerPrioritaeten_zweiSeparateSpeicherAufrufe_beideBleibenErhalten() {
        // Reproduziert den gemeldeten Bug: Teilnehmer speichert Prio fuer Vortrag A, dann in
        // einem SEPARATEN Request nur Prio fuer Vortrag B (so sendet die UI es tatsaechlich -
        // TeilnehmerDashboard.vue schickt nur die seit dem letzten Speichern geaenderten
        // Eintraege, nicht den gesamten Zustand). A darf durch den zweiten Aufruf nicht verloren
        // gehen.
        Vortrag wv1 = (Vortrag) Vortrag.find("titel", "Traumberuf Informatiker?").firstResult();
        Vortrag wv2 = (Vortrag) Vortrag.find("titel", "Mechatroniker").firstResult();

        given()
            .contentType("application/json")
            .body("[{\"vortragId\": " + wv1.getId() + ", \"prioWert\": 7}]")
            .when().post("/api/prios")
            .then().statusCode(200);

        given()
            .contentType("application/json")
            .body("[{\"vortragId\": " + wv2.getId() + ", \"prioWert\": 4}]")
            .when().post("/api/prios")
            .then().statusCode(200);

        given()
            .when().get("/api/prios/{vid}", vid)
            .then().statusCode(200)
            .body("'" + wv1.getId() + "'", org.hamcrest.Matchers.equalTo(7))
            .body("'" + wv2.getId() + "'", org.hamcrest.Matchers.equalTo(4));
    }


    @Test
    @TestSecurity(user = "alex.alfa", roles = "TEILNEHMER")
    @OidcSecurity(claims = {@Claim(key = "preferred_username", value = "alex.alfa")})
    void speichernDerPrioritaeten_mehrereVortraegeAufKeinInteresse_shouldSucceed() {
        Vortrag wv1 = (Vortrag) Vortrag.find("titel", "Traumberuf Informatiker?").firstResult();
        Vortrag wv2 = (Vortrag) Vortrag.find("titel", "Mechatroniker").firstResult();

        given()
            .contentType("application/json")
            .body("[{\"vortragId\": " + wv1.getId() + ", \"prioWert\": 0}, {\"vortragId\": " + wv2.getId() + ", \"prioWert\": 0}]")
            .when().post("/api/prios")
            .then().statusCode(200);
    }


    @Test
    @TestSecurity(user = "alex.alfa", roles = "TEILNEHMER")
    @OidcSecurity(claims = {@Claim(key = "preferred_username", value = "alex.alfa")})
    void speichernDerPrioritaeten_derselbeRangZweimalVergeben_shouldSucceed() {
        // Die Eindeutigkeits-Pruefung fuer Rangwerte wurde bewusst entfernt - mehrere Vortraege
        // duerfen denselben Rang erhalten.
        Vortrag wv1 = (Vortrag) Vortrag.find("titel", "Traumberuf Informatiker?").firstResult();
        Vortrag wv2 = (Vortrag) Vortrag.find("titel", "Mechatroniker").firstResult();

        given()
            .contentType("application/json")
            .body("[{\"vortragId\": " + wv1.getId() + ", \"prioWert\": 5}, {\"vortragId\": " + wv2.getId() + ", \"prioWert\": 5}]")
            .when().post("/api/prios")
            .then().statusCode(200);
    }


    @Test
    @TestSecurity(user = "alex.alfa", roles = "TEILNEHMER")
    @OidcSecurity(claims = {@Claim(key = "preferred_username", value = "alex.alfa")})
    void speichernDerPrioritaeten_ueberschreitetMaximumUeberZweiSeparateAufrufe_shouldFehlschlagen() {
        // Die Obergrenze muss den bereits bestehenden (aus einem frueheren Aufruf gespeicherten)
        // Bestand mitzaehlen, nicht nur die im aktuellen Request enthaltenen Eintraege.
        QuarkusTransaction.requiringNew().run(() -> {
            Veranstaltung veranstaltung = Veranstaltung.findById(vid);
            veranstaltung.setMaxPrioritaeten(1);
            // alex.alfa hat aus tn_prios.csv bereits zwei Prioritaeten vorbelegt - fuer diesen
            // Test auf einen sauberen Ausgangszustand zuruecksetzen.
            Teilnehmer alexAlfa = (Teilnehmer) Teilnehmer.find("loginName", "alex.alfa").firstResult();
            Prioritaet.delete("teilnehmer", alexAlfa);
        });

        Vortrag wv1 = (Vortrag) Vortrag.find("titel", "Traumberuf Informatiker?").firstResult();
        Vortrag wv2 = (Vortrag) Vortrag.find("titel", "Mechatroniker").firstResult();

        given()
            .contentType("application/json")
            .body("[{\"vortragId\": " + wv1.getId() + ", \"prioWert\": 5}]")
            .when().post("/api/prios")
            .then().statusCode(200);

        given()
            .contentType("application/json")
            .body("[{\"vortragId\": " + wv2.getId() + ", \"prioWert\": 3}]")
            .when().post("/api/prios")
            .then().statusCode(400);
    }


    @Test
    @TestSecurity(user = "alex.alfa", roles = "TEILNEHMER")
    @OidcSecurity(claims = {@Claim(key = "preferred_username", value = "alex.alfa")})
    void speichernDerPrioritaeten_ueberschreitetKonfiguriertesMaximum_shouldFehlschlagen() {
        QuarkusTransaction.requiringNew().run(() -> {
            Veranstaltung veranstaltung = Veranstaltung.findById(vid);
            veranstaltung.setMaxPrioritaeten(1);
        });

        Vortrag wv1 = (Vortrag) Vortrag.find("titel", "Traumberuf Informatiker?").firstResult();
        Vortrag wv2 = (Vortrag) Vortrag.find("titel", "Mechatroniker").firstResult();

        given()
            .contentType("application/json")
            .body("[{\"vortragId\": " + wv1.getId() + ", \"prioWert\": 5}, {\"vortragId\": " + wv2.getId() + ", \"prioWert\": 3}]")
            .when().post("/api/prios")
            .then().statusCode(400);
    }
}
