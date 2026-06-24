package kreyj.konfplan.application.service;

import io.quarkus.qute.Location;
import io.quarkus.qute.Template;
import io.quarkus.qute.TemplateInstance;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import kreyj.konfplan.persistence.NutzerVerfuegbarkeit;
import kreyj.konfplan.persistence.Planungsergebnis;
import kreyj.konfplan.persistence.Raum;
import kreyj.konfplan.persistence.Referent;
import kreyj.konfplan.persistence.Slot;
import kreyj.konfplan.persistence.Teilnehmer;
import kreyj.konfplan.persistence.Veranstaltung;
import kreyj.konfplan.persistence.VeranstaltungsVerfuegbarkeit;
import kreyj.konfplan.presentation.dto.RaumBelegungUebersicht;
import kreyj.konfplan.presentation.dto.RaumplanEintragDto;
import kreyj.konfplan.presentation.dto.templating.DashboardData;
import kreyj.konfplan.presentation.dto.templating.PrioDashboard;
import kreyj.konfplan.presentation.dto.templating.Stundenplan;
import kreyj.konfplan.presentation.dto.templating.TeilnehmerDashboard;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import static java.util.stream.Collectors.toMap;

@ApplicationScoped
public class TemplateService {
    private final PlanService planService;
    private final DashboardService dashboardService;


    // Templates for standard reports
    @Location("report/laufzettel-teilnehmer.html")
    Template laufzettelTeilnehmerTemplate;

    @Location("report/laufzettel-referent.html")
    Template laufzettelReferentTemplate;

    @Location("report/raumbelegungsplan.html")
    Template raumbelegungsplanTemplate;

    @Location("report/uebersicht-raeume.html")
    Template uebersichtRaeumeTemplate;

    @Location("report/raumschilder.html")
    Template raumschilderTemplate;

    @Location("report/freie-slots-referenten.html")
    Template freieSlotsReferentenTemplate;

    @Location("report/freie-slots-teilnehmer.html")
    Template freieSlotsTeilnehmerTemplate;

    // Templates for dashboards (migrated from Python)
    @Location("report/dashboard_stundenplan_template.html")
    Template stundenplanDashboardTemplate;

    @Location("report/dashboard_teilnehmer_template.html")
    Template teilnehmerDashboardTemplate;

    @Location("report/dashboard_prios_template.html")
    Template priosDashboardTemplate;


    public TemplateService(PlanService planService, DashboardService dashboardService) {
        this.planService = planService;
        this.dashboardService = dashboardService;
    }

    // --- Methods for standard reports ---


    public TemplateInstance prepareAlleLaufzettelTemplate(Veranstaltung veranstaltung) {
        // This method would need a new template that iterates over all participants
        // For now, returning a placeholder.
        throw new UnsupportedOperationException("prepareAlleLaufzettelTemplate noch nicht implementiert");

//        return laufzettelTeilnehmerTemplate
//                .data("error", "Not implemented yet")
//                .data("teilnehmer", veranstaltung.teilnehmer());
    }


    public TemplateInstance prepareTnLaufzettelTemplate(Veranstaltung veranstaltung, Teilnehmer teilnehmer) {
        return laufzettelTeilnehmerTemplate.data("veranstaltung", veranstaltung)
                .data("teilnehmer", teilnehmer)
                .data("plan", planService.getPlanFuerTeilnehmer(teilnehmer, veranstaltung));
    }


    public TemplateInstance prepareRefLaufzettelTemplate(Veranstaltung veranstaltung, Referent referent) {
        return laufzettelReferentTemplate
                .data("veranstaltung", veranstaltung)
                .data("referent", referent)
                .data("plan", planService.getPlanFuerReferent(referent, veranstaltung));
    }


    public TemplateInstance prepareRaumbelegungTemplate(Veranstaltung veranstaltung, Raum raum) {
        Map<Long, Map<Long, RaumplanEintragDto>> raumplan = planService.getRaumbelegungsplan(veranstaltung);
        Map<Long, RaumplanEintragDto> belegungFuerRaum = raumplan.getOrDefault(raum.getId(), Map.of());
        return raumbelegungsplanTemplate
                .data("veranstaltung", veranstaltung)
                .data("raum", raum)
                .data("belegung", belegungFuerRaum);
    }


    public TemplateInstance prepareUebersichtRaeumeTemplate(Veranstaltung veranstaltung) {
        List<RaumBelegungUebersicht> plan = planService.getDetaillierterPlan(veranstaltung);
        return uebersichtRaeumeTemplate
                .data("veranstaltung", veranstaltung)
                .data("plan", plan);
    }


    public TemplateInstance prepareRaumschilderTemplate(Veranstaltung veranstaltung) {
        Map<Long, Map<Long, RaumplanEintragDto>> raumplan = planService.getRaumbelegungsplan(veranstaltung);
        return raumschilderTemplate
                .data("veranstaltung", veranstaltung)
                .data("raumplan", raumplan)
                .data("raeume", veranstaltung.getRaeume())
                .data("slots", veranstaltung.getSlots());
    }


    public TemplateInstance prepareFreieSlotsReferentenReport(Veranstaltung veranstaltung) {
        Map<Long, List<Slot>> freieSlots = planService.getFreieSlotsReferenten(veranstaltung);
        List<Referent> referenten = veranstaltung.referenten();
        return freieSlotsReferentenTemplate
                .data("veranstaltung", veranstaltung)
                .data("freieSlots", freieSlots)
                .data("referenten", referenten);
    }


    public TemplateInstance prepareFreieSlotsTeilnehmerTemplate(Veranstaltung veranstaltung) {
        Map<Long, List<Slot>> freieSlots = planService.getFreieSlotsTeilnehmer(veranstaltung);
        return freieSlotsTeilnehmerTemplate
                .data("veranstaltung", veranstaltung)
                .data("freieSlots", freieSlots)
                .data("teilnehmer", veranstaltung.teilnehmer());
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
                .map(AdminService::mapNutzerToDto)
                .collect(toMap(r -> r.id, Function.identity()));


        dashboardService.prepareDashboardData(dashboardData);

        return dashboardData;
    }


    public TemplateInstance prepareStundenplanDashboard(Veranstaltung veranstaltung) {
        DashboardData dd = getDashboardData(veranstaltung);
        Stundenplan stundenplan = dd.stundenplan;
        return stundenplanDashboardTemplate.data(Map.of(
                "veranstaltung", veranstaltung,
                "slots", stundenplan.getSlots(),
                "raeume", stundenplan.getRaeume(),
                "belegung_details", stundenplan.getBelegung_details(),
                "freie_tn_je_slot", stundenplan.getFreieTnProSlot(),
                "stats", stundenplan.getStats(),
                "wahl_erfuellung_stats", stundenplan.getWahlErfuellungStats(),
                "wv_dict", stundenplan.getWahlvortraege(),
                "geplantAm", stundenplan.getGeplantAm()
        ));
    }


    public TemplateInstance prepareTeilnehmerDashboard(Veranstaltung veranstaltung, Teilnehmer teilnehmer) {
        DashboardData dd = getDashboardData(veranstaltung);
        TeilnehmerDashboard db = dd.teilnehmerDashboard;

        TeilnehmerDashboard.TeilnehmerInfoDto teilnehmerInfo = new TeilnehmerDashboard.TeilnehmerInfoDto(
                teilnehmer.getFirstName(),
                teilnehmer.getLastName(),
                teilnehmer.getGruppen()
        );

        return teilnehmerDashboardTemplate.data(Map.of(
                "veranstaltung", veranstaltung,
                "teilnehmer", teilnehmerInfo,
                "slots", dd.slots,
                "teilnehmer_stundenplan", db.teilnehmer_stundenplan(),
                "gruppen", db.gruppen(),
                "geplantAm", dd.geplantAm
        ));
    }


    public TemplateInstance preparePriosDashboard(Veranstaltung veranstaltung) {
        DashboardData dd = getDashboardData(veranstaltung);
        PrioDashboard prioDashboard = dd.prioDashboard;

        HashMap<String, Object> templateDataMap = new HashMap<>();

        templateDataMap.put("veranstaltung", veranstaltung);
        templateDataMap.put("slots", prioDashboard.slots());
        templateDataMap.put("raeume", prioDashboard.raeume());
        templateDataMap.put("wvOids", dd.mzWahlvortragOids);
        templateDataMap.put("slotOids", dd.mzSlotOids);
        templateDataMap.put("raumOids", dd.mzRaumOids);
        templateDataMap.put("instanz_raum", prioDashboard.instanz_raum());
        templateDataMap.put("instanz_slot", prioDashboard.instanz_slot());
        templateDataMap.put("num_instanzen_pro_wv", prioDashboard.num_instanzen_pro_wv());
        templateDataMap.put("teilnehmer_erfuellung", prioDashboard.teilnehmer_erfuellung());
        templateDataMap.put("wv_dict", prioDashboard.wv_dict());
        templateDataMap.put("ref_dict", prioDashboard.ref_dict());
        templateDataMap.put("gruppen", prioDashboard.gruppen());
        templateDataMap.put("geplantAm", prioDashboard.geplantAm());

        return priosDashboardTemplate.data(templateDataMap);
    }
}
