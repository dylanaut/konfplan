package kreyj.konfplan.adapter.in.web;

import jakarta.annotation.security.RolesAllowed;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import kreyj.konfplan.adapter.in.web.dto.RaumDto;
import kreyj.konfplan.adapter.in.web.dto.ReportDto;
import kreyj.konfplan.adapter.in.web.dto.SlotDto;
import kreyj.konfplan.adapter.in.web.dto.ZuweisungDto;
import kreyj.konfplan.application.service.PlanService;
import kreyj.konfplan.application.service.TeilnehmerService;
import kreyj.konfplan.application.service.TemplateService;
import kreyj.konfplan.persistence.Raum;
import kreyj.konfplan.persistence.Referent;
import kreyj.konfplan.persistence.Teilnehmer;
import kreyj.konfplan.persistence.Veranstaltung;
import kreyj.konfplan.util.JwtHelper;
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

    private final TemplateService templateService;
    private final TeilnehmerService teilnehmerService;
    private final PlanService planService;
    private final JsonWebToken jwt;


    public ReportResource(TemplateService templateService, TeilnehmerService teilnehmerService, PlanService planService, JsonWebToken jwt) {
        this.templateService = templateService;
        this.teilnehmerService = teilnehmerService;
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
        if (veranstaltung == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        Map<Long, List<ZuweisungDto>> plaene = veranstaltung.teilnehmer().stream()
            .collect(Collectors.toMap(t -> t.id, t -> planService.getPlanFuerTeilnehmer(t, veranstaltung)));

        return Response.ok(plaene).build();
    }


    @GET
    @Path("/{vid}/teilnehmer/{tid}/laufzettel-data")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed({"TEILNEHMER", "ADMIN"})
    @Operation(summary = "Daten für Teilnehmer-Laufzettel (JSON)")
    public Response getLaufzettelTeilnehmerData(@PathParam("vid") Long vid, @PathParam("tid") Long tid) {
        Veranstaltung veranstaltung = Veranstaltung.findById(vid);
        Teilnehmer teilnehmer = Teilnehmer.findById(tid);
        if (teilnehmer == null || veranstaltung == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        if (!jwt.getGroups().contains("ADMIN") && !teilnehmer.getEmail().equals(jwt.getName())) {
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
        if (referent == null || veranstaltung == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        if (!jwt.getGroups().contains("ADMIN") && !referent.getEmail().equals(jwt.getName())) {
            return Response.status(Response.Status.FORBIDDEN).build();
        }
        List<ZuweisungDto> plan = planService.getPlanFuerReferent(referent, veranstaltung);
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
        if (raum == null || veranstaltung == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        var belegung = planService.getRaumbelegungsplan(veranstaltung).getOrDefault(raum.getId(), Map.of());
        return Response.ok(new ReportDto.RaumbelegungsplanDto(veranstaltung, RaumDto.from(raum), belegung)).build();
    }


    @GET
    @Path("/{vid}/raeume-data")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed("ADMIN")
    @Operation(summary = "Daten für Übersicht aller Räume (JSON)")
    public Response getUebersichtRaeumeData(@PathParam("vid") Long vid) {
        Veranstaltung veranstaltung = Veranstaltung.findById(vid);
        if (veranstaltung == null) {
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
        if (veranstaltung == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        var raeume = veranstaltung.getRaeume().stream().map(RaumDto::from).toList();
        var slots = veranstaltung.getSlots().stream().map(SlotDto::from).toList();
        return Response.ok(new ReportDto.RaumschilderDto(veranstaltung, planService.getRaumbelegungsplan(veranstaltung), raeume, slots)).build();
    }


    @GET
    @Path("/{vid}/freie-slots-referenten-data")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed("ADMIN")
    @Operation(summary = "Daten für freie Slots der Referenten (JSON)")
    public Response getFreieSlotsReferentenData(@PathParam("vid") Long vid) {
        Veranstaltung veranstaltung = Veranstaltung.findById(vid);
        if (veranstaltung == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.ok(new ReportDto.FreieSlotsDto(veranstaltung, planService.getFreieSlotsReferenten(veranstaltung), veranstaltung.referenten())).build();
    }


    @GET
    @Path("/{vid}/freie-slots-teilnehmer-data")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed("ADMIN")
    @Operation(summary = "Daten für freie Slots der Teilnehmer (JSON)")
    public Response getFreieSlotsTeilnehmerData(@PathParam("vid") Long vid) {
        Veranstaltung veranstaltung = Veranstaltung.findById(vid);
        if (veranstaltung == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.ok(new ReportDto.FreieSlotsDto(veranstaltung, planService.getFreieSlotsTeilnehmer(veranstaltung), veranstaltung.teilnehmer())).build();
    }


    @GET
    @Path("/{vid}/admin-dashboard-data")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed("ADMIN")
    @Operation(summary = "Daten für Admin-Dashboard (JSON)")
    public Response getStundenplanDashboardData(@PathParam("vid") Long vid) {
        Veranstaltung veranstaltung = Veranstaltung.findById(vid);
        if (veranstaltung == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.ok(templateService.getDashboardData(veranstaltung).stundenplan).build();
    }


    @GET
    @Path("/{vid}/teilnehmer-dashboard-data")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed({"ADMIN", "TEILNEHMER"})
    @Operation(summary = "Daten für Teilnehmer-Dashboard (JSON)")
    public Response getTeilnehmerDashboardData(@PathParam("vid") Long vid) {
        Veranstaltung veranstaltung = Veranstaltung.findById(vid);
        if (veranstaltung == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        Teilnehmer teilnehmer = teilnehmerService.findByEmail(JwtHelper.getUserPrincipalName(jwt));
        if (null == teilnehmer) {
            throw new WebApplicationException("Teilnehmer not found", Response.Status.NOT_FOUND);
        }
        return Response.ok(templateService.getDashboardData(veranstaltung).teilnehmerDashboard).build();
    }


    @GET
    @Path("/{vid}/prios-dashboard-data")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed("ADMIN")
    @Operation(summary = "Daten für Prioritäten-Dashboard (JSON)")
    public Response getPriosDashboardData(@PathParam("vid") Long vid) {
        Veranstaltung veranstaltung = Veranstaltung.findById(vid);
        if (veranstaltung == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.ok(templateService.getDashboardData(veranstaltung).prioDashboard).build();
    }
}
