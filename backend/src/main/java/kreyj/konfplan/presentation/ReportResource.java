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
import kreyj.konfplan.application.service.PdfService;
import kreyj.konfplan.application.service.PlanService;
import kreyj.konfplan.presentation.dto.RaumBelegungUebersichtDto;
import kreyj.konfplan.presentation.dto.RaumplanEintragDto;
import kreyj.konfplan.persistence.Raum;
import kreyj.konfplan.persistence.Referent;
import kreyj.konfplan.persistence.Slot;
import kreyj.konfplan.persistence.Teilnehmer;
import kreyj.konfplan.persistence.Veranstaltung;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.logging.Logger;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Path("/api/reports")
@Tag(name = "Reports", description = "Endpunkte zum Generieren von Berichten und Plänen (HTML/PDF)")
public class ReportResource {
    private static final Logger LOG = Logger.getLogger(ReportResource.class);

    private final PlanService planService;

    private final PdfService pdfService;

    private final JsonWebToken jwt;

    private final Template laufzettelTeilnehmer;

    private final Template laufzettelReferent;

    private final Template raumbelegungsplan;

    private final Template uebersichtRaeume;

    private final Template freieSlotsReferenten;

    private final Template freieSlotsTeilnehmer;

    public ReportResource(PlanService planService, PdfService pdfService, JsonWebToken jwt,
                          @Location("report/laufzettel-teilnehmer.ftl") Template laufzettelTeilnehmer,
                          @Location("report/laufzettel-referent.ftl") Template laufzettelReferent,
                          @Location("report/raumbelegungsplan.ftl") Template raumbelegungsplan,
                          @Location("report/uebersicht-raeume.ftl") Template uebersichtRaeume,
                          @Location("report/freie-slots-referenten.ftl") Template freieSlotsReferenten,
                          @Location("report/freie-slots-teilnehmer.ftl") Template freieSlotsTeilnehmer) {
        this.planService = planService;
        this.pdfService = pdfService;
        this.jwt = jwt;
        this.laufzettelTeilnehmer = laufzettelTeilnehmer;
        this.laufzettelReferent = laufzettelReferent;
        this.raumbelegungsplan = raumbelegungsplan;
        this.uebersichtRaeume = uebersichtRaeume;
        this.freieSlotsReferenten = freieSlotsReferenten;
        this.freieSlotsTeilnehmer = freieSlotsTeilnehmer;
    }

    @GET
    @Path("/{vid}/teilnehmer/{tid}/laufzettel")
    @Produces(MediaType.TEXT_HTML)
    @RolesAllowed({"TEILNEHMER", "ADMIN"})
    @Operation(summary = "Laufzettel für Teilnehmer (HTML)", description = "Generiert den persönlichen Laufzettel für einen Teilnehmer als HTML.")
    public TemplateInstance getLaufzettelTeilnehmer(@PathParam("vid") Long vid, @PathParam("tid") Long tid) {
        Teilnehmer teilnehmer = Teilnehmer.findById(tid);
        Veranstaltung veranstaltung = Veranstaltung.findById(vid);

        if (teilnehmer == null || veranstaltung == null) {
            return laufzettelTeilnehmer.data("error", "Teilnehmer oder Veranstaltung nicht gefunden.");
        }

        if (!jwt.getGroups().contains("ADMIN") && !teilnehmer.getEmail().equals(jwt.getName())) {
            return laufzettelTeilnehmer.data("error", "Zugriff verweigert.");
        }

        return laufzettelTeilnehmer.data(
                "veranstaltung", veranstaltung,
                "teilnehmer", teilnehmer,
                "plan", planService.getPlanFuerTeilnehmer(teilnehmer.getEmail(), vid)
        );
    }

    @GET
    @Path("/{vid}/teilnehmer/{tid}/laufzettel-pdf")
    @Produces("application/pdf")
    @RolesAllowed({"TEILNEHMER", "ADMIN"})
    @Operation(summary = "Laufzettel für Teilnehmer (PDF)", description = "Generiert den persönlichen Laufzettel für einen Teilnehmer als PDF.")
    public byte[] getLaufzettelTeilnehmerPdf(@PathParam("vid") Long vid, @PathParam("tid") Long tid) {
        Teilnehmer teilnehmer = Teilnehmer.findById(tid);
        Veranstaltung veranstaltung = Veranstaltung.findById(vid);

        if (teilnehmer == null || veranstaltung == null) {
            throw new RuntimeException("Teilnehmer oder Veranstaltung nicht gefunden.");
        }

        if (!jwt.getGroups().contains("ADMIN") && !teilnehmer.getEmail().equals(jwt.getName())) {
            throw new RuntimeException("Zugriff verweigert.");
        }

        TemplateInstance templateInstance = laufzettelTeilnehmer.data(
                "veranstaltung", veranstaltung,
                "teilnehmer", teilnehmer,
                "plan", planService.getPlanFuerTeilnehmer(teilnehmer.getEmail(), vid)
        );

        return pdfService.generatePdf(templateInstance);
    }

    @GET
    @Path("/{vid}/referent/{rid}/laufzettel")
    @Produces(MediaType.TEXT_HTML)
    @RolesAllowed({"REFERENT", "ADMIN"})
    @Operation(summary = "Laufzettel für Referent (HTML)", description = "Generiert den persönlichen Laufzettel für einen Referenten als HTML.")
    public TemplateInstance getLaufzettelReferent(@PathParam("vid") Long vid, @PathParam("rid") Long rid) {
        Referent referent = Referent.findById(rid);
        Veranstaltung veranstaltung = Veranstaltung.findById(vid);

        if (referent == null || veranstaltung == null) {
            return laufzettelReferent.data("error", "Referent oder Veranstaltung nicht gefunden.");
        }

        if (!jwt.getGroups().contains("ADMIN") && !referent.getEmail().equals(jwt.getName())) {
            return laufzettelReferent.data("error", "Zugriff verweigert.");
        }

        return laufzettelReferent.data(
                "veranstaltung", veranstaltung,
                "referent", referent,
                "plan", planService.getPlanFuerReferent(referent.getEmail(), vid)
        );
    }

    @GET
    @Path("/{vid}/referent/{rid}/laufzettel-pdf")
    @Produces("application/pdf")
    @RolesAllowed({"REFERENT", "ADMIN"})
    @Operation(summary = "Laufzettel für Referent (PDF)", description = "Generiert den persönlichen Laufzettel für einen Referenten als PDF.")
    public byte[] getLaufzettelReferentPdf(@PathParam("vid") Long vid, @PathParam("rid") Long rid) {
        Referent referent = Referent.findById(rid);
        Veranstaltung veranstaltung = Veranstaltung.findById(vid);

        if (referent == null || veranstaltung == null) {
            throw new RuntimeException("Referent oder Veranstaltung nicht gefunden.");
        }

        if (!jwt.getGroups().contains("ADMIN") && !referent.getEmail().equals(jwt.getName())) {
            throw new RuntimeException("Zugriff verweigert.");
        }

        TemplateInstance templateInstance = laufzettelReferent.data(
                "veranstaltung", veranstaltung,
                "referent", referent,
                "plan", planService.getPlanFuerReferent(referent.getEmail(), vid)
        );

        return pdfService.generatePdf(templateInstance);
    }

    @GET
    @Path("/{vid}/raum/{rid}/belegungsplan")
    @Produces(MediaType.TEXT_HTML)
    @RolesAllowed("ADMIN")
    @Operation(summary = "Raumbelegungsplan (HTML)", description = "Generiert den Belegungsplan für einen einzelnen Raum als HTML.")
    public TemplateInstance getRaumbelegungsplan(@PathParam("vid") Long vid, @PathParam("rid") Long rid) {
        Veranstaltung veranstaltung = Veranstaltung.findById(vid);
        Raum raum = Raum.findById(rid);

        if (raum == null || veranstaltung == null) {
            return raumbelegungsplan.data("error", "Raum oder Veranstaltung nicht gefunden.");
        }

        Map<Long, Map<Long, RaumplanEintragDto>> raumplan = planService.getRaumbelegungsplan(vid);
        Map<Long, RaumplanEintragDto> belegungFuerRaum = raumplan.getOrDefault(rid, Collections.emptyMap());

        return raumbelegungsplan.data(
                "veranstaltung", veranstaltung,
                "raum", raum,
                "belegung", belegungFuerRaum
        );
    }

    @GET
    @Path("/{vid}/raum/{rid}/belegungsplan-pdf")
    @Produces("application/pdf")
    @RolesAllowed("ADMIN")
    @Operation(summary = "Raumbelegungsplan (PDF)", description = "Generiert den Belegungsplan für einen einzelnen Raum als PDF.")
    public byte[] getRaumbelegungsplanPdf(@PathParam("vid") Long vid, @PathParam("rid") Long rid) {
        Veranstaltung veranstaltung = Veranstaltung.findById(vid);
        Raum raum = Raum.findById(rid);

        if (raum == null || veranstaltung == null) {
            throw new RuntimeException("Raum oder Veranstaltung nicht gefunden.");
        }

        Map<Long, Map<Long, RaumplanEintragDto>> raumplan = planService.getRaumbelegungsplan(vid);
        Map<Long, RaumplanEintragDto> belegungFuerRaum = raumplan.getOrDefault(rid, Collections.emptyMap());

        TemplateInstance templateInstance = raumbelegungsplan.data(
                "veranstaltung", veranstaltung,
                "raum", raum,
                "belegung", belegungFuerRaum
        );

        return pdfService.generatePdf(templateInstance);
    }

    @GET
    @Path("/{vid}/raeume")
    @Produces(MediaType.TEXT_HTML)
    @RolesAllowed("ADMIN")
    @Operation(summary = "Übersicht aller Räume (HTML)", description = "Generiert eine detaillierte Belegungsübersicht aller Räume als HTML.")
    public TemplateInstance getUebersichtRaeume(@PathParam("vid") Long vid) {
        Veranstaltung veranstaltung = Veranstaltung.findById(vid);
        if (veranstaltung == null) {
            return uebersichtRaeume.data("error", "Veranstaltung nicht gefunden.");
        }
        List<RaumBelegungUebersichtDto> plan = planService.getDetaillierterPlan(vid);
        return uebersichtRaeume.data("veranstaltung", veranstaltung).data("plan", plan);
    }

    @GET
    @Path("/{vid}/raeume-pdf")
    @Produces("application/pdf")
    @RolesAllowed("ADMIN")
    @Operation(summary = "Übersicht aller Räume (PDF)", description = "Generiert eine detaillierte Belegungsübersicht aller Räume als PDF.")
    public byte[] getUebersichtRaeumePdf(@PathParam("vid") Long vid) {
        Veranstaltung veranstaltung = Veranstaltung.findById(vid);
        if (veranstaltung == null) {
            throw new RuntimeException("Veranstaltung nicht gefunden.");
        }
        List<RaumBelegungUebersichtDto> plan = planService.getDetaillierterPlan(vid);
        TemplateInstance templateInstance = uebersichtRaeume.data("veranstaltung", veranstaltung).data("plan", plan);
        return pdfService.generatePdf(templateInstance);
    }

    @GET
    @Path("/{vid}/freie-slots-referenten")
    @Produces(MediaType.TEXT_HTML)
    @RolesAllowed("ADMIN")
    @Operation(summary = "Freie Slots für Referenten (HTML)", description = "Zeigt eine Übersicht der freien Slots für alle Referenten einer Veranstaltung als HTML.")
    public TemplateInstance getFreieSlotsReferenten(@PathParam("vid") Long vid) {
        Veranstaltung veranstaltung = Veranstaltung.findById(vid);
        if (veranstaltung == null) {
            return freieSlotsReferenten.data("error", "Veranstaltung nicht gefunden.");
        }
        Map<Long, List<Slot>> freieSlots = planService.getFreieSlotsReferenten(vid);
        List<Referent> referenten = Referent.find("SELECT r FROM Referent r JOIN r.veranstaltungen v WHERE v.id = ?1", vid).list();
        return freieSlotsReferenten.data("veranstaltung", veranstaltung)
                .data("freieSlots", freieSlots)
                .data("referenten", referenten);
    }

    @GET
    @Path("/{vid}/freie-slots-referenten-pdf")
    @Produces("application/pdf")
    @RolesAllowed("ADMIN")
    @Operation(summary = "Freie Slots für Referenten (PDF)", description = "Zeigt eine Übersicht der freien Slots für alle Referenten einer Veranstaltung als PDF.")
    public byte[] getFreieSlotsReferentenPdf(@PathParam("vid") Long vid) {
        Veranstaltung veranstaltung = Veranstaltung.findById(vid);
        if (veranstaltung == null) {
            throw new RuntimeException("Veranstaltung nicht gefunden.");
        }
        Map<Long, List<Slot>> freieSlots = planService.getFreieSlotsReferenten(vid);
        List<Referent> referenten = Referent.find("SELECT r FROM Referent r JOIN r.veranstaltungen v WHERE v.id = ?1", vid).list();
        TemplateInstance templateInstance = freieSlotsReferenten.data("veranstaltung", veranstaltung)
                .data("freieSlots", freieSlots)
                .data("referenten", referenten);
        return pdfService.generatePdf(templateInstance);
    }

    @GET
    @Path("/{vid}/freie-slots-teilnehmer")
    @Produces(MediaType.TEXT_HTML)
    @RolesAllowed("ADMIN")
    @Operation(summary = "Freie Slots für Teilnehmer (HTML)", description = "Zeigt eine Übersicht der freien Slots für alle Teilnehmer einer Veranstaltung als HTML.")
    public TemplateInstance getFreieSlotsTeilnehmer(@PathParam("vid") Long vid) {
        Veranstaltung veranstaltung = Veranstaltung.findById(vid);
        if (veranstaltung == null) {
            return freieSlotsTeilnehmer.data("error", "Veranstaltung nicht gefunden.");
        }
        Map<Long, List<Slot>> freieSlots = planService.getFreieSlotsTeilnehmer(vid);
        List<Teilnehmer> teilnehmer = Teilnehmer.find("SELECT t FROM Teilnehmer t JOIN t.veranstaltungen v WHERE v.id = ?1", vid).list();
        return freieSlotsTeilnehmer.data("veranstaltung", veranstaltung)
                .data("freieSlots", freieSlots)
                .data("teilnehmer", teilnehmer);
    }

    @GET
    @Path("/{vid}/freie-slots-teilnehmer-pdf")
    @Produces("application/pdf")
    @RolesAllowed("ADMIN")
    @Operation(summary = "Freie Slots für Teilnehmer (PDF)", description = "Zeigt eine Übersicht der freien Slots für alle Teilnehmer einer Veranstaltung als PDF.")
    public byte[] getFreieSlotsTeilnehmerPdf(@PathParam("vid") Long vid) {
        Veranstaltung veranstaltung = Veranstaltung.findById(vid);
        if (veranstaltung == null) {
            throw new RuntimeException("Veranstaltung nicht gefunden.");
        }
        Map<Long, List<Slot>> freieSlots = planService.getFreieSlotsTeilnehmer(vid);
        List<Teilnehmer> teilnehmer = Teilnehmer.find("SELECT t FROM Teilnehmer t JOIN t.veranstaltungen v WHERE v.id = ?1", vid).list();
        TemplateInstance templateInstance = freieSlotsTeilnehmer.data("veranstaltung", veranstaltung)
                .data("freieSlots", freieSlots)
                .data("teilnehmer", teilnehmer);
        return pdfService.generatePdf(templateInstance);
    }
}