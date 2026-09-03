package kreyj.konfplan.adapter.in.web;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.oidc.Claim;
import io.quarkus.test.security.oidc.OidcSecurity;
import jakarta.inject.Inject;
import kreyj.konfplan.domain.service.OrganisatorService;
import kreyj.konfplan.domain.service.GebaeudeService;
import kreyj.konfplan.domain.service.KeycloakUserProvisioningService;
import kreyj.konfplan.domain.service.ReferentService;
import kreyj.konfplan.domain.service.VeranstaltungService;
import kreyj.konfplan.persistence.NutzerVerfuegbarkeit;
import kreyj.konfplan.persistence.Referent;
import kreyj.konfplan.persistence.Slot;
import kreyj.konfplan.persistence.Veranstaltung;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static io.restassured.RestAssured.given;
import static kreyj.konfplan.persistence.NutzerVerfuegbarkeitId.nvIdL;

/**
 * Regression test für #143: POST /api/referenten/veranstaltungen/{vid}/verfuegbarkeiten schlug
 * mit 403 fehl (Frontend/Backend-Contract-Mismatch) bzw. mit 500 (clear() auf dem von
 * getVerfuegbareSlotIds() zurückgegebenen unveränderlichen Set), sobald der Contract stimmte.
 */
@QuarkusTest
class ReferentVerfuegbarkeitTest extends DatabaseCleaner {

    @InjectMock
    KeycloakUserProvisioningService keycloakUserProvisioningService;

    @Inject
    GebaeudeService gebaeudeService;
    @Inject
    OrganisatorService adminService;
    @Inject
    VeranstaltungService veranstaltungService;
    @Inject
    ReferentService referentService;

    Long vid;
    Long referentId;
    List<Long> slotIds;

    @BeforeEach
    void importMediumDataset() throws Exception {
        Path basePath = Paths.get("src/test/resources/csv_import/medium");

        gebaeudeService.importGebaeudeWithRaeumeFromCsv(basePath.resolve("gebaeude.csv"));
        adminService.importOrganisatorenFromCsv(basePath.resolve("organisatoren.csv"));
        veranstaltungService.importFromCsv(basePath.resolve("veranstaltungen.csv"));

        Veranstaltung event = QuarkusTransaction.requiringNew().call(() ->
            (Veranstaltung) Veranstaltung.find("ORDER BY id DESC").firstResult());
        vid = event.getId();

        adminService.importSlotsFromCsv(basePath.resolve("slots.csv"), vid);
        referentService.importFromCsv(basePath.resolve("referenten.csv"), vid);

        QuarkusTransaction.requiringNew().run(() -> {
            Referent referent = (Referent) Referent.find("loginName", "erster.referent").firstResult();
            referentId = referent.getId();
            slotIds = Slot.find("veranstaltung.id", vid).<Slot>list().stream().map(Slot::getId).toList();
        });
    }

    @Test
    @TestSecurity(user = "erster.referent", roles = "REFERENT")
    @OidcSecurity(claims = {@Claim(key = "preferred_username", value = "erster.referent")})
    void updateVerfuegbarkeit_mitKorrektemContract_persistiertNeueSlotIds() {
        Long einzelnerSlot = slotIds.get(0);

        given()
            .contentType("application/json")
            .body("""
                {"nutzerId": %d, "veranstaltungId": %d, "verfuegbareSlotIds": [%d]}
                """.formatted(referentId, vid, einzelnerSlot))
            .when().post("/api/referenten/veranstaltungen/" + vid + "/verfuegbarkeiten")
            .then().statusCode(200);

        NutzerVerfuegbarkeit nv = QuarkusTransaction.requiringNew().call(() ->
            NutzerVerfuegbarkeit.findById(nvIdL(referentId, vid)));
        org.assertj.core.api.Assertions.assertThat(nv.getVerfuegbareSlotIds()).containsExactly(einzelnerSlot);
    }
}
