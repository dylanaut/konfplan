package kreyj.konfplan.presentation;

import io.agroal.api.AgroalDataSource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import kreyj.konfplan.application.service.AdminService;
import kreyj.konfplan.application.service.GebaeudeService;
import kreyj.konfplan.application.service.PlanErstellungService;
import kreyj.konfplan.application.service.ReferentService;
import kreyj.konfplan.application.service.TeilnehmerService;
import kreyj.konfplan.application.service.TemplateService;
import kreyj.konfplan.application.service.VeranstaltungService;
import kreyj.konfplan.presentation.testprofiles.SmallTestdataProfile;

@QuarkusTest
@TestProfile(SmallTestdataProfile.class)
public class SmallTemplateRenderTest extends AbstractTemplateRenderTest {
    public SmallTemplateRenderTest(TemplateService templateService, AgroalDataSource ds, TeilnehmerService tnSvc,
                                   AdminService adminSvc, VeranstaltungService vSvc, GebaeudeService gSvc,
                                   ReferentService rSvc, PlanErstellungService planSvc) {
        super(templateService, ds, tnSvc, adminSvc, vSvc, gSvc, rSvc, planSvc);
    }
}
