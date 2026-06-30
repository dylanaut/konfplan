package kreyj.konfplan.domain.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import kreyj.konfplan.adapter.in.web.dto.NutzerDto;
import kreyj.konfplan.adapter.in.web.dto.RaumDto;
import kreyj.konfplan.adapter.in.web.dto.SlotDto;
import kreyj.konfplan.adapter.in.web.dto.TeilnehmerDto;
import kreyj.konfplan.adapter.in.web.dto.VeranstaltungDto;
import kreyj.konfplan.adapter.in.web.dto.VortragDto;
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
                VeranstaltungDto.from(veranstaltung),
                result.besucht, result.instanz_slot, result.instanz_raum,
                nvMap,
                result.teilnehmer_oids, result.wahlvortrag_oids, result.slot_oids, result.raum_oids);
        dashboardData.teilnehmer = veranstaltung.teilnehmer().stream()
                .map(TeilnehmerDto::from)
                .collect(toMap(tn -> tn.id, Function.identity()));
        dashboardData.wahlvortraege = veranstaltung.getWahlvortraege().stream()
                .map(VortragDto::from)
                .collect(toMap(wv -> wv.id, Function.identity()));
        dashboardData.pflichtvortraege = veranstaltung.getPflichtvortraege().stream()
                .map(VortragDto::from)
                .collect(toMap(pv -> pv.id, Function.identity()));
        dashboardData.slots = veranstaltung.getSlots().stream()
                .map(SlotDto::from)
                .collect(toMap(s -> s.id, Function.identity()));
        dashboardData.raeume = veranstaltung.getRaeume().stream()
                .map(RaumDto::from)
                .collect(toMap(r -> r.id, Function.identity()));
        dashboardData.referenten = veranstaltung.referenten().stream()
                .map(NutzerDto::from)
                .collect(toMap(r -> r.id, Function.identity()));


        dashboardService.prepareDashboardData(dashboardData);

        return dashboardData;
    }
}
