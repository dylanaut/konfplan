package kreyj.konfplan.presentation;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.agroal.api.AgroalDataSource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.h2.H2DatabaseTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import kreyj.konfplan.application.devsupport.DevDataInitService;
import kreyj.konfplan.application.service.AdminService;
import kreyj.konfplan.application.service.GebaeudeService;
import kreyj.konfplan.application.service.PlanErstellungService;
import kreyj.konfplan.application.service.ReferentService;
import kreyj.konfplan.application.service.TeilnehmerService;
import kreyj.konfplan.application.service.TemplateService;
import kreyj.konfplan.application.service.VeranstaltungService;
import kreyj.konfplan.persistence.Raum;
import kreyj.konfplan.persistence.Referent;
import kreyj.konfplan.persistence.Teilnehmer;
import kreyj.konfplan.persistence.Veranstaltung;
import kreyj.konfplan.presentation.dto.SolverConfig;
import kreyj.konfplan.presentation.testprofiles.MinimalDataTestProfile;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.awt.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

@QuarkusTest
@QuarkusTestResource(H2DatabaseTestResource.class)
@TestProfile(MinimalDataTestProfile.class)
@RequiredArgsConstructor
class TemplateRenderTest {
    private final TemplateService templateService;

    private final ObjectMapper mapper = new ObjectMapper();

    private final AgroalDataSource ds;
    private final TeilnehmerService tnSvc;
    private final AdminService adminSvc;
    private final VeranstaltungService vSvc;
    private final GebaeudeService gSvc;
    private final ReferentService rSvc;

    private final PlanErstellungService planSvc;

    private Veranstaltung veranstaltung;


    // -------------------------------------------------------------------
    // Test Setup
    // -------------------------------------------------------------------


    @BeforeEach
    void initData() throws Exception {
        DevDataInitService initService = new DevDataInitService(ds, tnSvc, adminSvc, vSvc, gSvc, rSvc);
        initService.onStart(null);

        veranstaltung = Veranstaltung.<Veranstaltung>listAll().getFirst();

        planSvc.erstellePlan(veranstaltung.getId(), new SolverConfig(60, 4, 1, true));
    }

    // -------------------------------------------------------------------
    // Test methods
    // -------------------------------------------------------------------


    //    @Test
    void
    testAlleLaufzettelTemplate() {
        showInBrowser(templateService.prepareAlleLaufzettelTemplate(veranstaltung).render());
    }


    @Test
    void testTnLaufzettelTemplate() {
        Teilnehmer teilnehmer1 = veranstaltung.teilnehmer().getFirst();
        showInBrowser(templateService.prepareTnLaufzettelTemplate(veranstaltung, teilnehmer1).render());
    }


    @Test
    void testRefLaufzettelTemplate() {
        Referent referent1 = veranstaltung.referenten().getFirst();
        showInBrowser(templateService.prepareRefLaufzettelTemplate(veranstaltung, referent1).render());
    }


    @Test
    void testRaumbelegungTemplate() {
        Raum raum1 = veranstaltung.getRaeume().getFirst();
        showInBrowser(templateService.prepareRaumbelegungTemplate(veranstaltung, raum1).render());
    }


    @Test
    void testUebersichtRaeumeTemplate() {
        showInBrowser(templateService.prepareUebersichtRaeumeTemplate(veranstaltung).render());
    }


    @Test
    void testRaumschilderTemplate() {
        showInBrowser(templateService.prepareRaumschilderTemplate(veranstaltung).render());
    }


    @Test
    void testFreieSlotsReferentenReport() {
        showInBrowser(templateService.prepareFreieSlotsReferentenReport(veranstaltung).render());
    }


    @Test
    void testFreieSlotsTeilnehmerTemplate() {
        showInBrowser(templateService.prepareFreieSlotsTeilnehmerTemplate(veranstaltung).render());
    }


    @Test
    void testStundenplanDashboard() {
        showInBrowser(templateService.prepareStundenplanDashboard(veranstaltung).render());
    }


    @Test
    void testTeilnehmerDashboard() {
        showInBrowser(templateService.prepareTeilnehmerDashboard(veranstaltung).render());
    }


    @Test
    void testPriosDashboard() {
        showInBrowser(templateService.preparePriosDashboard(veranstaltung).render());
    }


    public static void showInBrowser(String html) {
        try {
            Path tempFile = Files.createTempFile("konfplan-preview-", ".html");
            tempFile.toFile().deleteOnExit();

            Files.writeString(tempFile, html, StandardCharsets.UTF_8);

            if (Desktop.isDesktopSupported()) {
                Desktop desktop = Desktop.getDesktop();
                if (desktop.isSupported(Desktop.Action.BROWSE)) {
                    desktop.browse(tempFile.toUri());
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
