package kreyj.konfplan.application.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import kreyj.konfplan.application.port.in.AdminServiceInterface;
import kreyj.konfplan.persistence.NutzerVerfuegbarkeit;
import kreyj.konfplan.persistence.Planungsergebnis;
import kreyj.konfplan.persistence.Veranstaltung;
import kreyj.konfplan.persistence.VeranstaltungsVerfuegbarkeit;
import kreyj.konfplan.adapter.in.web.dto.templating.DashboardData;

import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import static java.util.stream.Collectors.toMap;

@ApplicationScoped
public class TemplateService {
    private final PlanService planService;
    private final DashboardService dashboardService;
    private final AdminServiceInterface adminService;

    public TemplateService(PlanService planService, DashboardService dashboardService, AdminServiceInterface adminService) {
        this.planService = planService;
        this.dashboardService = dashboardService;
        this.adminService = adminService;
    }

    @Transactional
    public DashboardData getDashboardData(Veranstaltung veranstaltung) {
        Planungsergebnis.MinizincResult result = planService.getMinizincResult(veranstaltung);

        Map<Long, Set<Long>> nvMap =
                NutzerVerfuegbarkeit.<NutzerVerfuegbarkeit>list("veranstaltungId = ?1", veranstaltung.getId())
                        .stream().collect(toMap(NutzerVerfuegbarkeit::getNutzerId,
                                VeranstaltungsVerfuegbarkeit::getVerfuegbareSlotIds));
        DashboardData dashboardData = new DashboardData(
                VeranstaltungService.mapVeranstaltungToDto(veranstaltung),
                result.besucht, result.instanz_slot, result.instanz_raum,
                nvMap,
                result.teilnehmer_oids, result.wahlvortrag_oids, result.slot_oids, result.raum_oids);
        dashboardData.teilnehmer = veranstaltung.teilnehmer().stream()
                .map(TeilnehmerService::mapToDto)
                .collect(toMap(tn -> tn.id, Function.identity()));
        dashboardData.wahlvortraege = veranstaltung.getWahlvortraege().stream()
                .map(ReferentService::mapVortragToDto)
                .collect(toMap(wv -> wv.id, Function.identity()));
        dashboardData.pflichtvortraege = veranstaltung.getPflichtvortraege().stream()
                .map(ReferentService::mapVortragToDto)
                .collect(toMap(pv -> pv.id, Function.identity()));
        dashboardData.slots = veranstaltung.getSlots().stream()
                .map(AdminService::mapSlotToDto)
                .collect(toMap(s -> s.id, Function.identity()));
        dashboardData.raeume = veranstaltung.getRaeume().stream()
                .map(VeranstaltungService::mapRaumToDto)
                .collect(toMap(r -> r.id, Function.identity()));
        dashboardData.referenten = veranstaltung.referenten().stream()
                .map(adminService::mapNutzerToDto)
                .collect(toMap(r -> r.id, Function.identity()));


        dashboardService.prepareDashboardData(dashboardData);

        return dashboardData;
    }
}
