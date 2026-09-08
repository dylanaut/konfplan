package kreyj.konfplan.adapter.in.web;

import jakarta.annotation.security.RolesAllowed;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import kreyj.konfplan.adapter.in.web.dto.NutzerDto;
import kreyj.konfplan.adapter.in.web.dto.RaumDto;
import kreyj.konfplan.adapter.in.web.dto.RaumplanEintragDto;
import kreyj.konfplan.adapter.in.web.dto.ReferentVortragDto;
import kreyj.konfplan.adapter.in.web.dto.ReportDto;
import kreyj.konfplan.adapter.in.web.dto.SlotDto;
import kreyj.konfplan.adapter.in.web.dto.ZuweisungDto;
import kreyj.konfplan.domain.service.DashboardService;
import kreyj.konfplan.domain.service.PlanService;
import kreyj.konfplan.persistence.IdEntity;
import kreyj.konfplan.persistence.Prioritaet;
import kreyj.konfplan.persistence.Raum;
import kreyj.konfplan.persistence.Referent;
import kreyj.konfplan.persistence.Teilnehmer;
import kreyj.konfplan.persistence.Veranstaltung;
import kreyj.konfplan.persistence.Vortrag;
import kreyj.konfplan.persistence.Wahlvortrag;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.logging.Logger;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Path("/api/reports")
@Tag(name = "Reports", description = "Endpunkte zum Generieren von Berichten und Plänen (HTML/PDF)")
@Transactional
public class ReportResource {
    private static final Logger LOG = Logger.getLogger(ReportResource.class);

    private final DashboardService dashboardService;
    private final PlanService planService;
    private final JsonWebToken jwt;

    @SuppressWarnings("CdiInjectionPointsInspection")
    public ReportResource(DashboardService dashboardService, PlanService planService, JsonWebToken jwt) {
        this.dashboardService = dashboardService;
        this.planService = planService;
        this.jwt = jwt;
    }


    private boolean isOrganisatorOderAdmin() {
        return jwt.getGroups().contains("ORGANISATOR") || jwt.getGroups().contains("ADMINISTRATOR");
    }


    @GET
    @Path("/{vid}/laufzettel-alle-data")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed({"ORGANISATOR", "ADMINISTRATOR"})
    @Operation(summary = "Daten für alle Laufzettel (JSON)")
    public Response getAlleLaufzettelData(@PathParam("vid") Long vid, @QueryParam("ergebnisId") Long ergebnisId) {
        Veranstaltung veranstaltung = Veranstaltung.findById(vid);
        if (null == veranstaltung) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        Map<Long, List<ZuweisungDto>> plaene = veranstaltung.teilnehmer().stream()
            .collect(Collectors.toMap(IdEntity::getId, t -> planService.getPlanFuerTeilnehmer(t, veranstaltung, ergebnisId)));
        List<NutzerDto> teilnehmerDtos = veranstaltung.teilnehmer().stream().map(NutzerDto::from).toList();

        return Response.ok(new ReportDto.LaufzettelAlleDto(veranstaltung, plaene, teilnehmerDtos)).build();
    }


    @GET
    @Path("/{vid}/laufzettel-alle-referenten-data")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed({"ORGANISATOR", "ADMINISTRATOR"})
    @Operation(summary = "Daten für alle Laufzettel der Referenten (JSON)")
    public Response getAlleLaufzettelReferentenData(@PathParam("vid") Long vid, @QueryParam("ergebnisId") Long ergebnisId) {
        Veranstaltung veranstaltung = Veranstaltung.findById(vid);
        if (null == veranstaltung) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        Map<Long, List<ReferentVortragDto>> plaene = veranstaltung.referenten().stream()
            .collect(Collectors.toMap(IdEntity::getId, r -> planService.getPlanFuerReferent(r, veranstaltung, ergebnisId)));
        List<NutzerDto> referentenDtos = veranstaltung.referenten().stream().map(NutzerDto::from).toList();

        return Response.ok(new ReportDto.LaufzettelAlleReferentenDto(veranstaltung, plaene, referentenDtos)).build();
    }


    @GET
    @Path("/{vid}/teilnehmer/{tid}/laufzettel-data")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed({"TEILNEHMER", "ORGANISATOR", "ADMINISTRATOR"})
    @Operation(summary = "Daten für Teilnehmer-Laufzettel (JSON)")
    public Response getLaufzettelTeilnehmerData(@PathParam("vid") Long vid, @PathParam("tid") Long tid, @QueryParam("ergebnisId") Long ergebnisId) {
        Veranstaltung veranstaltung = Veranstaltung.findById(vid);
        Teilnehmer teilnehmer = Teilnehmer.findById(tid);
        if (null == teilnehmer || null == veranstaltung) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        if (!isOrganisatorOderAdmin() && !teilnehmer.getLoginName().equals(jwt.getName())) {
            return Response.status(Response.Status.FORBIDDEN).build();
        }
        // ergebnisId (Vorschau eines unveröffentlichten Ergebnisses) ist nur für Organisatoren/
        // Administratoren wirksam, damit ein Teilnehmer nicht per URL-Parameter ein noch nicht
        // veröffentlichtes Ergebnis einsehen kann.
        List<ZuweisungDto> plan = planService.getPlanFuerTeilnehmer(teilnehmer, veranstaltung, isOrganisatorOderAdmin() ? ergebnisId : null);
        return Response.ok(new ReportDto.LaufzettelTeilnehmerDto(veranstaltung, teilnehmer, plan)).build();
    }


    @GET
    @Path("/{vid}/referent/{rid}/laufzettel-data")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed({"REFERENT", "ORGANISATOR", "ADMINISTRATOR"})
    @Operation(summary = "Daten für Referenten-Laufzettel (JSON)")
    public Response getLaufzettelReferentData(@PathParam("vid") Long vid, @PathParam("rid") Long refId, @QueryParam("ergebnisId") Long ergebnisId) {
        Veranstaltung veranstaltung = Veranstaltung.findById(vid);
        Referent referent = Referent.findById(refId);
        if (null == referent || null == veranstaltung) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        if (!isOrganisatorOderAdmin() && !referent.getLoginName().equals(jwt.getName())) {
            return Response.status(Response.Status.FORBIDDEN).build();
        }
        List<ReferentVortragDto> plan = planService.getPlanFuerReferent(referent, veranstaltung, isOrganisatorOderAdmin() ? ergebnisId : null);
        return Response.ok(new ReportDto.LaufzettelReferentDto(veranstaltung, referent, plan)).build();
    }


    @GET
    @Path("/{vid}/raum/{rid}/belegungsplan-data")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed({"ORGANISATOR", "ADMINISTRATOR"})
    @Operation(summary = "Daten für Raumbelegungsplan (JSON)")
    public Response getRaumbelegungsplanData(@PathParam("vid") Long vid, @PathParam("rid") Long rid, @QueryParam("ergebnisId") Long ergebnisId) {
        Veranstaltung veranstaltung = Veranstaltung.findById(vid);
        Raum raum = Raum.findById(rid);
        if (null == raum || null == veranstaltung) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        Map<Long, Map<Long, RaumplanEintragDto>> belegung = planService.getRaumbelegungsplan(veranstaltung, ergebnisId);
        return Response.ok(new ReportDto.RaumbelegungsplanDto(veranstaltung, RaumDto.from(raum), belegung)).build();
    }


    @GET
    @Path("/{vid}/raeume-data")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed({"ORGANISATOR", "ADMINISTRATOR"})
    @Operation(summary = "Daten für Übersicht aller Räume (JSON)")
    public Response getUebersichtRaeumeData(@PathParam("vid") Long vid, @QueryParam("ergebnisId") Long ergebnisId) {
        Veranstaltung veranstaltung = Veranstaltung.findById(vid);
        if (null == veranstaltung) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.ok(new ReportDto.UebersichtRaeumeDto(veranstaltung, planService.getDetaillierterPlan(veranstaltung, ergebnisId))).build();
    }


    @GET
    @Path("/{vid}/raumschilder-data")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed({"ORGANISATOR", "ADMINISTRATOR"})
    @Operation(summary = "Daten für alle Raumschilder (JSON)")
    public Response getAlleRaumschilderData(@PathParam("vid") Long vid, @QueryParam("ergebnisId") Long ergebnisId) {
        Veranstaltung veranstaltung = Veranstaltung.findById(vid);
        if (null == veranstaltung) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        var raeume = veranstaltung.getRaeume().stream().map(RaumDto::from).toList();
        var slots = veranstaltung.getSlots().stream().map(SlotDto::from).toList();
        return Response.ok(new ReportDto.RaumschilderDto(veranstaltung,
            planService.getRaumbelegungsplan(veranstaltung, ergebnisId), raeume, slots)).build();
    }


    @GET
    @Path("/{vid}/freie-slots-referenten-data")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed({"ORGANISATOR", "ADMINISTRATOR"})
    @Operation(summary = "Daten für freie Slots der Referenten (JSON)")
    public Response getFreieSlotsReferentenData(@PathParam("vid") Long vid, @QueryParam("ergebnisId") Long ergebnisId) {
        Veranstaltung veranstaltung = Veranstaltung.findById(vid);
        if (null == veranstaltung) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.ok(new ReportDto.FreieSlotsDto(veranstaltung, planService.getFreieSlotsReferenten(veranstaltung, ergebnisId),
            veranstaltung.referenten().stream().map(NutzerDto::from).toList()
        )).build();
    }


    @GET
    @Path("/{vid}/freie-slots-teilnehmer-data")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed({"ORGANISATOR", "ADMINISTRATOR"})
    @Transactional
    @Operation(summary = "Daten für freie Slots der Teilnehmer (JSON)")
    public Response getFreieSlotsTeilnehmerData(@PathParam("vid") Long vid, @QueryParam("ergebnisId") Long ergebnisId) {
        Veranstaltung veranstaltung = Veranstaltung.findById(vid);
        if (null == veranstaltung) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.ok(new ReportDto.FreieSlotsDto(veranstaltung,
            planService.getFreieSlotsTeilnehmer(veranstaltung, ergebnisId),
            veranstaltung.teilnehmer().stream().map(NutzerDto::from).toList())).build();
    }


    @GET
    @Path("/{vid}/abstimmungsfragebogen-data")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed({"ORGANISATOR", "ADMINISTRATOR"})
    @Operation(summary = "Daten für Abstimmungsfragebögen aller Teilnehmer (JSON)")
    public Response getAbstimmungsfragebogenData(@PathParam("vid") Long vid) {
        Veranstaltung veranstaltung = Veranstaltung.findById(vid);
        if (null == veranstaltung) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.ok(new ReportDto.AbstimmungsfragebogenDto(veranstaltung)).build();
    }


    @GET
    @Path("/{vid}/stundenplan-data")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed({"ORGANISATOR", "ADMINISTRATOR"})
    @Operation(summary = "Daten für Organisator-Dashboard / Tab ErgebnisVue")
    public Response getStundenplanData(@PathParam("vid") Long vid, @QueryParam("ergebnisId") Long ergebnisId) {
        Veranstaltung veranstaltung = Veranstaltung.findById(vid);
        if (null == veranstaltung) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.ok(dashboardService.getStundenplan(veranstaltung, ergebnisId)).build();
    }


    @GET
    @Path("/{vid}/teilnehmer-dashboard-data")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed({"ORGANISATOR", "ADMINISTRATOR", "TEILNEHMER"})
    @Operation(summary = "Daten für Teilnehmer-Dashboard / Tab ErgebnisVue")
    public Response getTeilnehmerDashboardData(@PathParam("vid") Long vid, @QueryParam("ergebnisId") Long ergebnisId) {
        Veranstaltung veranstaltung = Veranstaltung.findById(vid);
        if (null == veranstaltung) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        // ergebnisId (Vorschau eines unveröffentlichten Ergebnisses) ist nur für Organisatoren/
        // Administratoren wirksam, siehe getLaufzettelTeilnehmerData.
        return Response.ok(dashboardService.getTeilnehmerReport(veranstaltung, isOrganisatorOderAdmin() ? ergebnisId : null)).build();
    }


    @GET
    @Path("/{vid}/prios-dashboard-data")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed({"ORGANISATOR", "ADMINISTRATOR"})
    @Operation(summary = "Daten für Prioritäten-Dashboard / Tab ErgebnisVue")
    @Transactional
    public Response getPriosDashboardData(@PathParam("vid") Long vid, @QueryParam("ergebnisId") Long ergebnisId) {
        Veranstaltung veranstaltung = Veranstaltung.findById(vid);
        if (null == veranstaltung) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.ok(dashboardService.getPrioReport(veranstaltung, ergebnisId)).build();
    }


    @GET
    @Path("/{vid}/vortrag/{vortragId}/anmeldungen-data")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed({"ORGANISATOR", "ADMINISTRATOR"})
    @Operation(summary = "Daten für die Anmeldungen-Übersicht eines Wahlvortrags (Teilnehmer + Priorität)")
    public Response getVortragAnmeldungenData(@PathParam("vid") Long vid, @PathParam("vortragId") Long vortragId) {
        Veranstaltung veranstaltung = Veranstaltung.findById(vid);
        Vortrag vortrag = Vortrag.findById(vortragId);
        if (null == veranstaltung || !(vortrag instanceof Wahlvortrag wahlvortrag)
            || !wahlvortrag.getVeranstaltung().getId().equals(vid)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        // prioWert = 0 bedeutet "keine Präferenz", zaehlt also nicht als Bewerbung/Anmeldung.
        List<Prioritaet> prioritaeten = Prioritaet.<Prioritaet>find(
            "vortrag.id = ?1 and prioWert > 0 order by prioWert desc, teilnehmer.loginName", vortragId).list();
        return Response.ok(new ReportDto.VortragAnmeldungenDto(veranstaltung, wahlvortrag, prioritaeten)).build();
    }


    @GET
    @Path("/{vid}/wahlvortraege-anmeldungen-uebersicht-data")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed({"ORGANISATOR", "ADMINISTRATOR"})
    @Operation(summary = "Daten für die Anmeldungen-Übersicht aller Wahlvorträge (Anzahl + Ø-Priorität je Wahlvortrag)")
    public Response getWahlvortraegeAnmeldungenUebersichtData(@PathParam("vid") Long vid) {
        Veranstaltung veranstaltung = Veranstaltung.findById(vid);
        if (null == veranstaltung) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        // prioWert = 0 bedeutet "keine Präferenz", zaehlt also nicht als Anmeldung (siehe getVortragAnmeldungenData).
        List<Prioritaet> prioritaeten = Prioritaet.<Prioritaet>find(
            "vortrag.veranstaltung.id = ?1 and prioWert > 0", vid).list();

        Map<Long, List<Prioritaet>> prioByVortragId = prioritaeten.stream()
            .collect(Collectors.groupingBy(p -> p.getVortrag().getId()));

        List<Wahlvortrag> wahlvortraege = veranstaltung.getWahlvortraege();

        List<ReportDto.WahlvortragAnmeldungenZeileDto> zeilen = wahlvortraege.stream()
            .filter(wv -> prioByVortragId.containsKey(wv.getId()))
            .map(wv -> {
                List<Prioritaet> prios = prioByVortragId.get(wv.getId());
                double durchschnitt = prios.stream().mapToInt(Prioritaet::getPrioWert).average().orElse(0.0);
                List<ReportDto.PrioSegmentDto> segmente = prios.stream()
                    .collect(Collectors.groupingBy(Prioritaet::getPrioWert, Collectors.counting()))
                    .entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .map(e -> new ReportDto.PrioSegmentDto(e.getKey(), e.getValue()))
                    .toList();
                return new ReportDto.WahlvortragAnmeldungenZeileDto(wv.getId(), wv.getTitel(), prios.size(), durchschnitt, segmente);
            })
            .sorted(Comparator.comparingLong((ReportDto.WahlvortragAnmeldungenZeileDto z) -> z.anzahlAnmeldungen).reversed())
            .toList();

        List<ReportDto.WahlvortragOhneAnmeldungenDto> ohneAnmeldungen = wahlvortraege.stream()
            .filter(wv -> !prioByVortragId.containsKey(wv.getId()))
            .sorted(Comparator.comparing(Wahlvortrag::getTitel, String.CASE_INSENSITIVE_ORDER))
            .map(wv -> new ReportDto.WahlvortragOhneAnmeldungenDto(wv.getId(), wv.getTitel()))
            .toList();

        return Response.ok(new ReportDto.WahlvortraegeAnmeldungenUebersichtDto(veranstaltung, zeilen, ohneAnmeldungen)).build();
    }
}
