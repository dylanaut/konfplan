package kreyj.konfplan.presentation;

import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import kreyj.konfplan.application.service.PdfService;
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

@Path("/api/reports")
@Tag(name = "Reports", description = "Endpunkte zum Generieren von Berichten und Plänen (HTML/PDF)")
public class ReportResource {
    private static final Logger LOG = Logger.getLogger(ReportResource.class);

    private final PdfService pdfService;
    private final TemplateService templateService;
    private final TeilnehmerService teilnehmerService;
    private final JsonWebToken jwt;

    public ReportResource(PdfService pdfService, TemplateService templateService, TeilnehmerService teilnehmerService, JsonWebToken jwt) {
        this.pdfService = pdfService;
        this.templateService = templateService;
        this.teilnehmerService = teilnehmerService;
        this.jwt = jwt;
    }

    @GET
    @Path("/{vid}/laufzettel")
    @Produces(MediaType.TEXT_HTML)
    @RolesAllowed("ADMIN")
    @Operation(summary = "Laufzettel für alle (HTML)", description = "Generiert die persönlichen Laufzettel einer Veranstaltung als HTML.")
    public Response getAlleLaufzettel(@PathParam("vid") Long vid) {
        Veranstaltung veranstaltung = Veranstaltung.findById(vid);
        if (veranstaltung == null) {
            return Response.status(Response.Status.NOT_FOUND).entity("Veranstaltung nicht gefunden.").build();
        }
        return Response.ok(templateService.prepareAlleLaufzettelTemplate(veranstaltung).render()).build();
    }

    @GET
    @Path("/{vid}/laufzettel-pdf")
    @Produces("application/pdf")
    @RolesAllowed("ADMIN")
    @Operation(summary = "Laufzettel für alle Teilnehmer (PDF)", description = "Generiert die persönlichen Laufzettel einer Veranstaltung als PDF.")
    public Response getAlleLaufzettelPdf(@PathParam("vid") Long vid) {
        Veranstaltung veranstaltung = Veranstaltung.findById(vid);
        if (veranstaltung == null) {
            return Response.status(Response.Status.NOT_FOUND).entity("Veranstaltung nicht gefunden.").build();
        }
        return Response.ok(pdfService.generatePdf(templateService.prepareAlleLaufzettelTemplate(veranstaltung))).build();
    }

    @GET
    @Path("/{vid}/teilnehmer/{tid}/laufzettel")
    @Produces(MediaType.TEXT_HTML)
    @RolesAllowed({"TEILNEHMER", "ADMIN"})
    @Operation(summary = "Laufzettel für Teilnehmer (HTML)", description = "Generiert den persönlichen Laufzettel für einen Teilnehmer als HTML.")
    public Response getLaufzettelTeilnehmer(@PathParam("vid") Long vid, @PathParam("tid") Long tid) {
        Veranstaltung veranstaltung = Veranstaltung.findById(vid);
        Teilnehmer teilnehmer = Teilnehmer.findById(tid);
        if (teilnehmer == null || veranstaltung == null) {
            return Response.status(Response.Status.NOT_FOUND).entity("Teilnehmer oder Veranstaltung nicht gefunden.").build();
        }
        if (!jwt.getGroups().contains("ADMIN") && !teilnehmer.getEmail().equals(jwt.getName())) {
            return Response.status(Response.Status.FORBIDDEN).build();
        }
        return Response.ok(templateService.prepareTnLaufzettelTemplate(veranstaltung, teilnehmer).render()).build();
    }

    @GET
    @Path("/{vid}/teilnehmer/{tid}/laufzettel-pdf")
    @Produces("application/pdf")
    @RolesAllowed({"TEILNEHMER", "ADMIN"})
    @Operation(summary = "Laufzettel für Teilnehmer (PDF)", description = "Generiert den persönlichen Laufzettel für einen Teilnehmer als PDF.")
    public Response getLaufzettelTeilnehmerPdf(@PathParam("vid") Long vid, @PathParam("tid") Long tid) {
        Veranstaltung veranstaltung = Veranstaltung.findById(vid);
        Teilnehmer teilnehmer = Teilnehmer.findById(tid);
        if (teilnehmer == null || veranstaltung == null) {
            return Response.status(Response.Status.NOT_FOUND).entity("Teilnehmer oder Veranstaltung nicht gefunden.").build();
        }
        if (!jwt.getGroups().contains("ADMIN") && !teilnehmer.getEmail().equals(jwt.getName())) {
            return Response.status(Response.Status.FORBIDDEN).build();
        }
        return Response.ok(pdfService.generatePdf(templateService.prepareTnLaufzettelTemplate(veranstaltung, teilnehmer))).build();
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
        return Response.ok(templateService.prepareRefLaufzettelTemplate(veranstaltung, referent).render()).build();
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
        if (!jwt.getGroups().contains("ADMIN") && !referent.getEmail().equals(jwt.getName())) {
            return Response.status(Response.Status.FORBIDDEN).build();
        }
        return Response.ok(pdfService.generatePdf(templateService.prepareRefLaufzettelTemplate(veranstaltung, referent))).build();
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
        return Response.ok(templateService.prepareRaumbelegungTemplate(veranstaltung, raum).render()).build();
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
        return Response.ok(pdfService.generatePdf(templateService.prepareRaumbelegungTemplate(veranstaltung, raum))).build();
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
        return Response.ok(templateService.prepareUebersichtRaeumeTemplate(veranstaltung).render()).build();
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
        return Response.ok(pdfService.generatePdf(templateService.prepareUebersichtRaeumeTemplate(veranstaltung))).build();
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
        return Response.ok(templateService.prepareRaumschilderTemplate(veranstaltung).render()).build();
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
        return Response.ok(pdfService.generatePdf(templateService.prepareRaumschilderTemplate(veranstaltung))).build();
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
        return Response.ok(templateService.prepareFreieSlotsReferentenReport(veranstaltung).render()).build();
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
        return Response.ok(pdfService.generatePdf(templateService.prepareFreieSlotsReferentenReport(veranstaltung))).build();
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
        return Response.ok(templateService.prepareFreieSlotsTeilnehmerTemplate(veranstaltung).render()).build();
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
        return Response.ok(pdfService.generatePdf(templateService.prepareFreieSlotsTeilnehmerTemplate(veranstaltung))).build();
    }

    // -------------------------------------------------------------------
    // Python-Skript Migration: Dashboard Endpoints
    // -------------------------------------------------------------------

    @GET
    @Path("/{vid}/dashboard/stundenplan")
    @Produces(MediaType.TEXT_HTML)
    @RolesAllowed("ADMIN")
    @Operation(summary = "Dashboard: Stundenplan (HTML)")
    public Response getStundenplanDashboard(@PathParam("vid") Long vid) {
        Veranstaltung veranstaltung = Veranstaltung.findById(vid);
        if (veranstaltung == null) return Response.status(Response.Status.NOT_FOUND).build();
        return Response.ok(templateService.prepareStundenplanDashboard(veranstaltung).render()).build();
    }

    @GET
    @Path("/{vid}/dashboard/stundenplan-pdf")
    @Produces("application/pdf")
    @RolesAllowed("ADMIN")
    @Operation(summary = "Dashboard: Stundenplan (PDF)")
    public Response getStundenplanDashboardPdf(@PathParam("vid") Long vid) {
        Veranstaltung veranstaltung = Veranstaltung.findById(vid);
        if (veranstaltung == null) return Response.status(Response.Status.NOT_FOUND).build();
        return Response.ok(pdfService.generatePdf(templateService.prepareStundenplanDashboard(veranstaltung))).build();
    }

    @GET
    @Path("/{vid}/dashboard/teilnehmer")
    @Produces(MediaType.TEXT_HTML)
    @RolesAllowed({"ADMIN", "TEILNEHMER"})
    @Operation(summary = "Dashboard: Teilnehmerübersicht (HTML)")
    public Response getTeilnehmerDashboard(@PathParam("vid") Long vid) {
        Veranstaltung veranstaltung = Veranstaltung.findById(vid);
        if (veranstaltung == null) return Response.status(Response.Status.NOT_FOUND).build();
        Teilnehmer teilnehmer = teilnehmerService.findByEmail(JwtHelper.getUserPrincipalName(jwt));
        if (null == teilnehmer) {
            throw new WebApplicationException("Teilnehmer not found", Response.Status.NOT_FOUND);
        }
        return Response.ok(templateService.prepareTeilnehmerDashboard(veranstaltung, teilnehmer).render()).build();
    }

    @GET
    @Path("/{vid}/dashboard/teilnehmer-pdf")
    @Produces("application/pdf")
    @RolesAllowed({"ADMIN","TEILNEHMER"})
    @Operation(summary = "Dashboard: Teilnehmerübersicht (PDF)")
    public Response getTeilnehmerDashboardPdf(@PathParam("vid") Long vid) {
        Veranstaltung veranstaltung = Veranstaltung.findById(vid);
        if (veranstaltung == null) return Response.status(Response.Status.NOT_FOUND).build();
        Teilnehmer teilnehmer = teilnehmerService.findByEmail(JwtHelper.getUserPrincipalName(jwt));
        if (null == teilnehmer) {
            throw new WebApplicationException("Teilnehmer not found", Response.Status.NOT_FOUND);
        }
        return Response.ok(pdfService.generatePdf(templateService.prepareTeilnehmerDashboard(veranstaltung, teilnehmer))).build();
    }

    @GET
    @Path("/{vid}/dashboard/prios")
    @Produces(MediaType.TEXT_HTML)
    @RolesAllowed("ADMIN")
    @Operation(summary = "Dashboard: Prioritätenanalyse (HTML)")
    public Response getPriosDashboard(@PathParam("vid") Long vid) {
        Veranstaltung veranstaltung = Veranstaltung.findById(vid);
        if (veranstaltung == null) return Response.status(Response.Status.NOT_FOUND).build();
        return Response.ok(templateService.preparePriosDashboard(veranstaltung).render()).build();
    }

    @GET
    @Path("/{vid}/dashboard/prios-pdf")
    @Produces("application/pdf")
    @RolesAllowed("ADMIN")
    @Operation(summary = "Dashboard: Prioritätenanalyse (PDF)")
    public Response getPriosDashboardPdf(@PathParam("vid") Long vid) {
        Veranstaltung veranstaltung = Veranstaltung.findById(vid);
        if (veranstaltung == null) return Response.status(Response.Status.NOT_FOUND).build();
        return Response.ok(pdfService.generatePdf(templateService.preparePriosDashboard(veranstaltung))).build();
    }
}
