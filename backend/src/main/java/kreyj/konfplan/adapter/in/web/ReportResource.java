package kreyj.konfplan.adapter.in.web;

import jakarta.annotation.security.RolesAllowed;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
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
import kreyj.konfplan.persistence.Raum;
import kreyj.konfplan.persistence.Referent;
import kreyj.konfplan.persistence.Teilnehmer;
import kreyj.konfplan.persistence.Veranstaltung;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.logging.Logger;

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


    @GET
    @Path("/{vid}/laufzettel-alle-data")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed("ADMIN")
    @Operation(summary = "Daten für alle Laufzettel (JSON)")
    public Response getAlleLaufzettelData(@PathParam("vid") Long vid) {
        Veranstaltung veranstaltung = Veranstaltung.findById(vid);
        if (null == veranstaltung) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        Map<Long, List<ZuweisungDto>> plaene = veranstaltung.teilnehmer().stream()
            .collect(Collectors.toMap(IdEntity::getId, t -> planService.getPlanFuerTeilnehmer(t, veranstaltung)));

        return Response.ok(new ReportDto.LaufzettelAlleDto(veranstaltung, plaene)).build();
    }


    @GET
    @Path("/{vid}/teilnehmer/{tid}/laufzettel-data")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed({"TEILNEHMER", "ADMIN"})
    @Operation(summary = "Daten für Teilnehmer-Laufzettel (JSON)")
    public Response getLaufzettelTeilnehmerData(@PathParam("vid") Long vid, @PathParam("tid") Long tid) {
        Veranstaltung veranstaltung = Veranstaltung.findById(vid);
        Teilnehmer teilnehmer = Teilnehmer.findById(tid);
        if (null == teilnehmer || null == veranstaltung) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        if (!jwt.getGroups().contains("ADMIN") && !teilnehmer.getLoginName().equals(jwt.getName())) {
            return Response.status(Response.Status.FORBIDDEN).build();
        }
        List<ZuweisungDto> plan = planService.getPlanFuerTeilnehmer(teilnehmer, veranstaltung);
        return Response.ok(new ReportDto.LaufzettelTeilnehmerDto(veranstaltung, teilnehmer, plan)).build();
    }


    @GET
    @Path("/{vid}/referent/{rid}/laufzettel-data")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed({"REFERENT", "ADMIN"})
    @Operation(summary = "Daten für Referenten-Laufzettel (JSON)")
    public Response getLaufzettelReferentData(@PathParam("vid") Long vid, @PathParam("rid") Long refId) {
        Veranstaltung veranstaltung = Veranstaltung.findById(vid);
        Referent referent = Referent.findById(refId);
        if (null == referent || null == veranstaltung) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        if (!jwt.getGroups().contains("ADMIN") && !referent.getLoginName().equals(jwt.getName())) {
            return Response.status(Response.Status.FORBIDDEN).build();
        }
        List<ReferentVortragDto> plan = planService.getPlanFuerReferent(referent, veranstaltung);
        return Response.ok(new ReportDto.LaufzettelReferentDto(veranstaltung, referent, plan)).build();
    }


    @GET
    @Path("/{vid}/raum/{rid}/belegungsplan-data")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed("ADMIN")
    @Operation(summary = "Daten für Raumbelegungsplan (JSON)")
    public Response getRaumbelegungsplanData(@PathParam("vid") Long vid, @PathParam("rid") Long rid) {
        Veranstaltung veranstaltung = Veranstaltung.findById(vid);
        Raum raum = Raum.findById(rid);
        if (null == raum || null == veranstaltung) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        Map<Long, Map<Long, RaumplanEintragDto>> belegung = planService.getRaumbelegungsplan(veranstaltung);
        return Response.ok(new ReportDto.RaumbelegungsplanDto(veranstaltung, RaumDto.from(raum), belegung)).build();
    }


    @GET
    @Path("/{vid}/raeume-data")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed("ADMIN")
    @Operation(summary = "Daten für Übersicht aller Räume (JSON)")
    public Response getUebersichtRaeumeData(@PathParam("vid") Long vid) {
        Veranstaltung veranstaltung = Veranstaltung.findById(vid);
        if (null == veranstaltung) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.ok(new ReportDto.UebersichtRaeumeDto(veranstaltung, planService.getDetaillierterPlan(veranstaltung))).build();
    }


    @GET
    @Path("/{vid}/raumschilder-data")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed("ADMIN")
    @Operation(summary = "Daten für alle Raumschilder (JSON)")
    public Response getAlleRaumschilderData(@PathParam("vid") Long vid) {
        Veranstaltung veranstaltung = Veranstaltung.findById(vid);
        if (null == veranstaltung) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        var raeume = veranstaltung.getRaeume().stream().map(RaumDto::from).toList();
        var slots = veranstaltung.getSlots().stream().map(SlotDto::from).toList();
        return Response.ok(new ReportDto.RaumschilderDto(veranstaltung,
            planService.getRaumbelegungsplan(veranstaltung), raeume, slots)).build();
    }


    @GET
    @Path("/{vid}/freie-slots-referenten-data")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed("ADMIN")
    @Operation(summary = "Daten für freie Slots der Referenten (JSON)")
    public Response getFreieSlotsReferentenData(@PathParam("vid") Long vid) {
        Veranstaltung veranstaltung = Veranstaltung.findById(vid);
        if (null == veranstaltung) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.ok(new ReportDto.FreieSlotsDto(veranstaltung, planService.getFreieSlotsReferenten(veranstaltung),
            veranstaltung.referenten().stream().map(NutzerDto::from).toList()
        )).build();
    }


    @GET
    @Path("/{vid}/freie-slots-teilnehmer-data")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed("ADMIN")
    @Transactional
    @Operation(summary = "Daten für freie Slots der Teilnehmer (JSON)")
    public Response getFreieSlotsTeilnehmerData(@PathParam("vid") Long vid) {
        Veranstaltung veranstaltung = Veranstaltung.findById(vid);
        if (null == veranstaltung) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.ok(new ReportDto.FreieSlotsDto(veranstaltung,
            planService.getFreieSlotsTeilnehmer(veranstaltung),
            veranstaltung.teilnehmer().stream().map(NutzerDto::from).toList())).build();
    }


    @GET
    @Path("/{vid}/stundenplan-data")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed("ADMIN")
    @Operation(summary = "Daten für Admin-Dashboard / Tab ErgebnisVue")
    public Response getStundenplanData(@PathParam("vid") Long vid) {
        Veranstaltung veranstaltung = Veranstaltung.findById(vid);
        if (null == veranstaltung) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.ok(dashboardService.getStundenplan(veranstaltung)).build();
    }


    @GET
    @Path("/{vid}/teilnehmer-dashboard-data")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed({"ADMIN", "TEILNEHMER"})
    @Operation(summary = "Daten für Teilnehmer-Dashboard / Tab ErgebnisVue")
    public Response getTeilnehmerDashboardData(@PathParam("vid") Long vid) {
        Veranstaltung veranstaltung = Veranstaltung.findById(vid);
        if (null == veranstaltung) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.ok(dashboardService.getTeilnehmerReport(veranstaltung)).build();
    }


    @GET
    @Path("/{vid}/prios-dashboard-data")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed("ADMIN")
    @Operation(summary = "Daten für Prioritäten-Dashboard / Tab ErgebnisVue")
    @Transactional
    public Response getPriosDashboardData(@PathParam("vid") Long vid) {
        Veranstaltung veranstaltung = Veranstaltung.findById(vid);
        if (null == veranstaltung) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.ok(dashboardService.getPrioReport(veranstaltung)).build();
    }
}
