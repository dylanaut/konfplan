package kreyj.konfplan.adapter.in.web;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.jwt.Claim;
import io.quarkus.test.security.jwt.JwtSecurity;
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
 * Deckt GET /api/referenten/plaene end-to-end ab, für eine Veranstaltung, die bereits ein
 * Planungsergebnis besitzt. Gleicher Bug-Klasse wie TeilnehmerVortraegeUndPrioritaetenTest:
 * getMyPlan() lud Veranstaltung.findById() ohne @Transactional, was in PostgreSQL (nicht in H2)
 * beim eager geladenen Planungsergebnis-LOB mit "LOB außerhalb einer Transaktion" fehlschlägt.
 */
@QuarkusTest
class ReferentPlaeneTest extends DatabaseCleaner {

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
            pe.setJsonErgebnis(result.toJson());
            pe.persist();
        });
    }

    @Test
    @TestSecurity(user = "erster.referent", roles = "REFERENT")
    @JwtSecurity(claims = {@Claim(key = "upn", value = "erster.referent")})
    void getMyPlan_fuerReferentMitBestehendemPlan_shouldSucceed() {
        given()
            .when().get("/api/referenten/plaene?vid=" + vid)
            .then().statusCode(200)
            .body("size()", org.hamcrest.Matchers.greaterThan(0));
    }
}
