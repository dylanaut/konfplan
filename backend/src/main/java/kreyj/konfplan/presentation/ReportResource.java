package kreyj.konfplan.presentation;

import io.quarkus.qute.Location;
import io.quarkus.qute.Template;
import io.quarkus.qute.TemplateInstance;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import kreyj.konfplan.application.service.PdfService;
import kreyj.konfplan.application.service.PlanService;
import kreyj.konfplan.persistence.Raum;
import kreyj.konfplan.persistence.Referent;
import kreyj.konfplan.persistence.Slot;
import kreyj.konfplan.persistence.Teilnehmer;
import kreyj.konfplan.persistence.Veranstaltung;
import kreyj.konfplan.presentation.dto.RaumBelegungUebersichtDto;
import kreyj.konfplan.presentation.dto.RaumplanEintragDto;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.logging.Logger;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Path("/api/reports")
@Tag(name = "Reports", description = "Endpunkte zum Generieren von Berichten und Plänen (HTML/PDF)")
public class ReportResource {
    private static final Logger LOG = Logger.getLogger(ReportResource.class);

    private final PlanService planService;
    private final PdfService pdfService;
    private final JsonWebToken jwt;

    @Location("report/laufzettel-teilnehmer.html")
    Template laufzettelTeilnehmer;

    @Location("report/laufzettel-referent.html")
    Template laufzettelReferent;

    @Location("report/raumbelegungsplan.html")
    Template raumbelegungsplan;

    @Location("report/uebersicht-raeume.html")
    Template uebersichtRaeume;

    @Location("report/freie-slots-referenten.html")
    Template freieSlotsReferenten;

    @Location("report/freie-slots-teilnehmer.html")
    Template freieSlotsTeilnehmer;

    @Location("report/raumschilder.html")
    Template raumschilder;

    public ReportResource(PlanService planService, PdfService pdfService, JsonWebToken jwt) {
        this.planService = planService;
        this.pdfService = pdfService;
        this.jwt = jwt;
    }

    @GET
    @Path("/{vid}/teilnehmer/{tid}/laufzettel")
    @Produces(MediaType.TEXT_HTML)
    @RolesAllowed({"TEILNEHMER", "ADMIN"})
    @Operation(summary = "Laufzettel für Teilnehmer (HTML)", description = "Generiert den persönlichen Laufzettel für einen Teilnehmer als HTML.")
    public Response getLaufzettelTeilnehmer(@PathParam("vid") Long vid, @PathParam("tid") Long tid) {
        Teilnehmer teilnehmer = Teilnehmer.findById(tid);
        Veranstaltung veranstaltung = Veranstaltung.findById(vid);

        if (teilnehmer == null || veranstaltung == null) {
            return Response.status(Response.Status.NOT_FOUND).entity("Teilnehmer oder Veranstaltung nicht gefunden.").build();
        }

        TemplateInstance templateInstance = laufzettelTeilnehmer.data(
                "veranstaltung", veranstaltung,
                "teilnehmer", teilnehmer,
                "plan", planService.getPlanFuerTeilnehmer(teilnehmer.getEmail(), vid)
        );
        return Response.ok(templateInstance.render()).build();
    }

    @GET
    @Path("/{vid}/teilnehmer/{tid}/laufzettel-pdf")
    @Produces("application/pdf")
    @RolesAllowed({"TEILNEHMER", "ADMIN"})
    @Operation(summary = "Laufzettel für Teilnehmer (PDF)", description = "Generiert den persönlichen Laufzettel für einen Teilnehmer als PDF.")
    public Response getLaufzettelTeilnehmerPdf(@PathParam("vid") Long vid, @PathParam("tid") Long tid) {
        Teilnehmer teilnehmer = Teilnehmer.findById(tid);
        Veranstaltung veranstaltung = Veranstaltung.findById(vid);

        if (teilnehmer == null || veranstaltung == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        TemplateInstance templateInstance = laufzettelTeilnehmer.data(
                "veranstaltung", veranstaltung,
                "teilnehmer", teilnehmer,
                "plan", planService.getPlanFuerTeilnehmer(teilnehmer.getEmail(), vid)
        );

        return Response.ok(pdfService.generatePdf(templateInstance)).build();
    }

    @GET
    @Path("/{vid}/referent/{rid}/laufzettel")
    @Produces(MediaType.TEXT_HTML)
    @RolesAllowed({"REFERENT", "ADMIN"})
    @Operation(summary = "Laufzettel für Referent (HTML)", description = "Generiert den persönlichen Laufzettel für einen Referenten als HTML.")
    public Response getLaufzettelReferent(@PathParam("vid") Long vid, @PathParam("rid") Long refId) {
        Referent referent = Referent.findById(refId);
        Veranstaltung veranstaltung = Veranstaltung.findById(vid);

        if (referent == null || veranstaltung == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        if (!jwt.getGroups().contains("ADMIN") && !referent.getEmail().equals(jwt.getName())) {
            return Response.status(Response.Status.FORBIDDEN).build();
        }

        TemplateInstance templateInstance = laufzettelReferent.data(
                "veranstaltung", veranstaltung,
                "referent", referent,
                "plan", planService.getPlanFuerReferent(referent.getEmail(), veranstaltung)
        );
        return Response.ok(templateInstance.render()).build();
    }

    @GET
    @Path("/{vid}/referent/{rid}/laufzettel-pdf")
    @Produces("application/pdf")
    @RolesAllowed({"REFERENT", "ADMIN"})
    @Operation(summary = "Laufzettel für Referent (PDF)", description = "Generiert den persönlichen Laufzettel für einen Referenten als PDF.")
    public Response getLaufzettelReferentPdf(@PathParam("vid") Long vid, @PathParam("rid") Long rid) {
        Referent referent = Referent.findById(rid);
        Veranstaltung veranstaltung = Veranstaltung.findById(vid);

        if (referent == null || veranstaltung == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        TemplateInstance templateInstance = laufzettelReferent.data(
                "veranstaltung", veranstaltung,
                "referent", referent,
                "plan", planService.getPlanFuerReferent(referent.getEmail(), veranstaltung)
        );

        return Response.ok(pdfService.generatePdf(templateInstance)).build();
    }

    @GET
    @Path("/{vid}/raum/{rid}/belegungsplan")
    @Produces(MediaType.TEXT_HTML)
    @RolesAllowed("ADMIN")
    @Operation(summary = "Raumbelegungsplan (HTML)", description = "Generiert den Belegungsplan für einen einzelnen Raum als HTML.")
    public Response getRaumbelegungsplan(@PathParam("vid") Long vid, @PathParam("rid") Long rid) {
        Veranstaltung veranstaltung = Veranstaltung.findById(vid);
        Raum raum = Raum.findById(rid);

        if (raum == null || veranstaltung == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        Map<Long, Map<Long, RaumplanEintragDto>> raumplan = planService.getRaumbelegungsplan(veranstaltung);
        Map<Long, RaumplanEintragDto> belegungFuerRaum = raumplan.getOrDefault(rid, Collections.emptyMap());

        TemplateInstance templateInstance = raumbelegungsplan.data(
                "veranstaltung", veranstaltung,
                "raum", raum,
                "belegung", belegungFuerRaum
        );
        return Response.ok(templateInstance.render()).build();
    }

    @GET
    @Path("/{vid}/raum/{rid}/belegungsplan-pdf")
    @Produces("application/pdf")
    @RolesAllowed("ADMIN")
    @Operation(summary = "Raumbelegungsplan (PDF)", description = "Generiert den Belegungsplan für einen einzelnen Raum als PDF.")
    public Response getRaumbelegungsplanPdf(@PathParam("vid") Long vid, @PathParam("rid") Long rid) {
        Veranstaltung veranstaltung = Veranstaltung.findById(vid);
        Raum raum = Raum.findById(rid);

        if (raum == null || veranstaltung == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        Map<Long, Map<Long, RaumplanEintragDto>> raumplan = planService.getRaumbelegungsplan(veranstaltung);
        Map<Long, RaumplanEintragDto> belegungFuerRaum = raumplan.getOrDefault(rid, Collections.emptyMap());

        TemplateInstance templateInstance = raumbelegungsplan.data(
                "veranstaltung", veranstaltung,
                "raum", raum,
                "belegung", belegungFuerRaum
        );

        return Response.ok(pdfService.generatePdf(templateInstance)).build();
    }

    @GET
    @Path("/{vid}/raeume")
    @Produces(MediaType.TEXT_HTML)
    @RolesAllowed("ADMIN")
    @Operation(summary = "Übersicht aller Räume (HTML)", description = "Generiert eine detaillierte Belegungsübersicht aller Räume als HTML.")
    public Response getUebersichtRaeume(@PathParam("vid") Long vid) {
        Veranstaltung veranstaltung = Veranstaltung.findById(vid);
        if (veranstaltung == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        List<RaumBelegungUebersichtDto> plan = planService.getDetaillierterPlan(veranstaltung);

        TemplateInstance templateInstance = uebersichtRaeume.data("veranstaltung", veranstaltung)
                .data("plan", plan);

        return Response.ok(templateInstance.render()).build();
    }

    @GET
    @Path("/{vid}/raeume-pdf")
    @Produces("application/pdf")
    @RolesAllowed("ADMIN")
    @Operation(summary = "Übersicht aller Räume (PDF)", description = "Generiert eine detaillierte Belegungsübersicht aller Räume als PDF.")
    public Response getUebersichtRaeumePdf(@PathParam("vid") Long vid) {
        Veranstaltung veranstaltung = Veranstaltung.findById(vid);
        if (veranstaltung == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        List<RaumBelegungUebersichtDto> plan = planService.getDetaillierterPlan(veranstaltung);
        TemplateInstance templateInstance = uebersichtRaeume.data("veranstaltung", veranstaltung).data("plan", plan);

        return Response.ok(pdfService.generatePdf(templateInstance)).build();
    }

    @GET
    @Path("/{vid}/raumschilder")
    @Produces(MediaType.TEXT_HTML)
    @RolesAllowed("ADMIN")
    @Operation(summary = "Alle Raumschilder als HTML", description = "Generiert eine Vorschau der Türschilder für alle Räume einer Veranstaltung.")
    public Response getAlleRaumschilder(@PathParam("vid") Long vid) {
        Veranstaltung veranstaltung = Veranstaltung.findById(vid);
        if (veranstaltung == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        Map<Long, Map<Long, RaumplanEintragDto>> raumplan = planService.getRaumbelegungsplan(veranstaltung);
        List<Raum> raeume = veranstaltung.getRaeume();
        Set<Slot> slots = veranstaltung.getSlots();

        TemplateInstance templateInstance = raumschilder.data(
                "veranstaltung", veranstaltung,
                "raumplan", raumplan,
                "raeume", raeume,
                "slots", slots
        );
        return Response.ok(templateInstance.render()).build();
    }

    @GET
    @Path("/{vid}/raumschilder-pdf")
    @Produces("application/pdf")
    @RolesAllowed("ADMIN")
    @Operation(summary = "Alle Raumschilder als PDF", description = "Generiert ein einziges PDF mit den Türschildern für alle Räume einer Veranstaltung.")
    public Response getAlleRaumschilderPdf(@PathParam("vid") Long vid) {
        Veranstaltung veranstaltung = Veranstaltung.findById(vid);
        if (veranstaltung == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.ok(planService.generiereAlleRaumschilderPdf(veranstaltung)).build();
    }

    @GET
    @Path("/{vid}/freie-slots-referenten")
    @Produces(MediaType.TEXT_HTML)
    @RolesAllowed("ADMIN")
    @Operation(summary = "Freie Slots für Referenten (HTML)", description = "Zeigt eine Übersicht der freien Slots für alle Referenten einer Veranstaltung als HTML.")
    public Response getFreieSlotsReferenten(@PathParam("vid") Long vid) {
        Veranstaltung veranstaltung = Veranstaltung.findById(vid);
        if (veranstaltung == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        Map<Long, List<Slot>> freieSlots = planService.getFreieSlotsReferenten(vid);
        List<Referent> referenten = veranstaltung.referenten();
        TemplateInstance templateInstance = freieSlotsReferenten.data("veranstaltung", veranstaltung)
                .data("freieSlots", freieSlots)
                .data("referenten", referenten);

        return Response.ok(templateInstance.render()).build();
    }

    @GET
    @Path("/{vid}/freie-slots-referenten-pdf")
    @Produces("application/pdf")
    @RolesAllowed("ADMIN")
    @Operation(summary = "Freie Slots für Referenten (PDF)", description = "Zeigt eine Übersicht der freien Slots für alle Referenten einer Veranstaltung als PDF.")
    public Response getFreieSlotsReferentenPdf(@PathParam("vid") Long vid) {
        Veranstaltung veranstaltung = Veranstaltung.findById(vid);
        if (veranstaltung == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        Map<Long, List<Slot>> freieSlots = planService.getFreieSlotsReferenten(vid);
        List<Referent> referenten = veranstaltung.referenten();
        TemplateInstance templateInstance = freieSlotsReferenten.data("veranstaltung", veranstaltung)
                .data("freieSlots", freieSlots)
                .data("referenten", referenten);

        return Response.ok(pdfService.generatePdf(templateInstance)).build();
    }

    @GET
    @Path("/{vid}/freie-slots-teilnehmer")
    @Produces(MediaType.TEXT_HTML)
    @RolesAllowed("ADMIN")
    @Operation(summary = "Freie Slots für Teilnehmer (HTML)", description = "Zeigt eine Übersicht der freien Slots für alle Teilnehmer einer Veranstaltung als HTML.")
    public Response getFreieSlotsTeilnehmer(@PathParam("vid") Long vid) {
        Veranstaltung veranstaltung = Veranstaltung.findById(vid);
        if (veranstaltung == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        Map<Long, List<Slot>> freieSlots = planService.getFreieSlotsTeilnehmer(veranstaltung);
        List<Teilnehmer> teilnehmer = veranstaltung.teilnehmer();

        TemplateInstance templateInstance = freieSlotsTeilnehmer.data("veranstaltung", veranstaltung)
                .data("freieSlots", freieSlots)
                .data("teilnehmer", teilnehmer);

        return Response.ok(templateInstance.render()).build();
    }

    @GET
    @Path("/{vid}/freie-slots-teilnehmer-pdf")
    @Produces("application/pdf")
    @RolesAllowed("ADMIN")
    @Operation(summary = "Freie Slots für Teilnehmer (PDF)", description = "Zeigt eine Übersicht der freien Slots für alle Teilnehmer einer Veranstaltung als PDF.")
    public Response getFreieSlotsTeilnehmerPdf(@PathParam("vid") Long vid) {
        Veranstaltung veranstaltung = Veranstaltung.findById(vid);
        if (veranstaltung == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        Map<Long, List<Slot>> freieSlots = planService.getFreieSlotsTeilnehmer(veranstaltung);
        List<Teilnehmer> teilnehmer = veranstaltung.teilnehmer();
        TemplateInstance templateInstance = freieSlotsTeilnehmer.data("veranstaltung", veranstaltung)
                .data("freieSlots", freieSlots)
                .data("teilnehmer", teilnehmer);

        return Response.ok(pdfService.generatePdf(templateInstance)).build();
    }
}