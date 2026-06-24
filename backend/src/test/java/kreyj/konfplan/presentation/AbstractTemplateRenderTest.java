package kreyj.konfplan.presentation;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.agroal.api.AgroalDataSource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.h2.H2DatabaseTestResource;
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
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.awt.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;


@QuarkusTestResource(H2DatabaseTestResource.class)
abstract class AbstractTemplateRenderTest {
    @ConfigProperty(name = "test.render-template.open-browser", defaultValue = "true")
    boolean openBrowser;

    protected final TemplateService templateService;
    protected final AgroalDataSource ds;
    protected final TeilnehmerService tnSvc;
    protected final AdminService adminSvc;
    protected final VeranstaltungService vSvc;
    protected final GebaeudeService gSvc;
    protected final ReferentService rSvc;

    protected final PlanErstellungService planSvc;

    private Veranstaltung veranstaltung;

    // -------------------------------------------------------------------
    // Konstruktor
    // -------------------------------------------------------------------


    public AbstractTemplateRenderTest(TemplateService templateService, AgroalDataSource ds, TeilnehmerService tnSvc,
                                      AdminService adminSvc, VeranstaltungService vSvc, GebaeudeService gSvc,
                                      ReferentService rSvc, PlanErstellungService planSvc) {
        this.templateService = templateService;
        this.ds = ds;
        this.tnSvc = tnSvc;
        this.adminSvc = adminSvc;
        this.vSvc = vSvc;
        this.gSvc = gSvc;
        this.rSvc = rSvc;
        this.planSvc = planSvc;
    }


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
        String html = templateService.prepareAlleLaufzettelTemplate(veranstaltung).render();
        assertThat(html).contains("<body>");

        showInBrowser(html);
    }


    @Test
    void testTnLaufzettelTemplate() {
        Teilnehmer teilnehmer1 = veranstaltung.teilnehmer().getFirst();
        String html = templateService.prepareTnLaufzettelTemplate(veranstaltung, teilnehmer1).render();
        assertThat(html).contains("<body>");

        showInBrowser(html);
    }


    @Test
    void testRefLaufzettelTemplate() {
        Referent referent1 = veranstaltung.referenten().getFirst();
        String html = templateService.prepareRefLaufzettelTemplate(veranstaltung, referent1).render();
        assertThat(html).contains("<body>");

        showInBrowser(html);
    }


    @Test
    void testRaumbelegungTemplate() {
        Raum raum1 = veranstaltung.getRaeume().getFirst();
        String html = templateService.prepareRaumbelegungTemplate(veranstaltung, raum1).render();
        assertThat(html).contains("<body>");

        showInBrowser(html);
    }


    @Test
    void testUebersichtRaeumeTemplate() {
        String html = templateService.prepareUebersichtRaeumeTemplate(veranstaltung).render();
        assertThat(html).contains("<body>");

        showInBrowser(html);
    }


    @Test
    void testRaumschilderTemplate() {
        String html = templateService.prepareRaumschilderTemplate(veranstaltung).render();
        assertThat(html).contains("<body>");

        showInBrowser(html);
    }


    @Test
    void testFreieSlotsReferentenReport() {
        String html = templateService.prepareFreieSlotsReferentenReport(veranstaltung).render();
        assertThat(html).contains("<body>");

        showInBrowser(html);
    }


    @Test
    void testFreieSlotsTeilnehmerTemplate() {
        String html = templateService.prepareFreieSlotsTeilnehmerTemplate(veranstaltung).render();
        assertThat(html).contains("<body>");

        showInBrowser(html);
    }


    @Test
    void testStundenplanDashboard() {
        String html = templateService.prepareStundenplanDashboard(veranstaltung).render();
        assertThat(html).contains("<body>");

        showInBrowser(html);
    }


    @Test
    void testTeilnehmerDashboard() {
        Teilnehmer teilnehmer1 = veranstaltung.teilnehmer().getFirst();
        String html = templateService.prepareTeilnehmerDashboard(veranstaltung, teilnehmer1).render();
        assertThat(html).contains("<body>");

        showInBrowser(html);
    }


    @Test
    void testPriosDashboard() {
        String html = templateService.preparePriosDashboard(veranstaltung).render();
        assertThat(html).contains("<body>");

        showInBrowser(html);
    }


    public void showInBrowser(String html) {
        try {
            Path tempFile = Files.createTempFile("konfplan-preview-", ".html");
            tempFile.toFile().deleteOnExit();

            Files.writeString(tempFile, html, StandardCharsets.UTF_8);

            if (openBrowser && Desktop.isDesktopSupported()) {
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
