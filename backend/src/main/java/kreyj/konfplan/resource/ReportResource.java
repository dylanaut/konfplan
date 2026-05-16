package kreyj.konfplan.resource;

import io.quarkus.qute.Location;
import io.quarkus.qute.Template;
import io.quarkus.qute.TemplateInstance;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import kreyj.konfplan.dto.RaumBelegungUebersichtDto;
import kreyj.konfplan.dto.RaumplanEintragDto;
import kreyj.konfplan.persistence.*;
import kreyj.konfplan.service.PlanService;
import kreyj.konfplan.service.PdfService;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Path("/api/reports")
public class ReportResource {

    @Inject
    PlanService planService;

    @Inject
    PdfService pdfService;

    @Inject
    JsonWebToken jwt;

    @Inject
    @Location("report/laufzettel-teilnehmer.ftl")
    Template laufzettelTeilnehmer;

    @Inject
    @Location("report/laufzettel-referent.ftl")
    Template laufzettelReferent;

    @Inject
    @Location("report/raumbelegungsplan.ftl")
    Template raumbelegungsplan;

    @Inject
    @Location("report/uebersicht-raeume.ftl")
    Template uebersichtRaeume;

    @Inject
    @Location("report/freie-slots-referenten.ftl")
    Template freieSlotsReferenten;

    @Inject
    @Location("report/freie-slots-teilnehmer.ftl")
    Template freieSlotsTeilnehmer;

    @GET
    @Path("/{vid}/teilnehmer/{tid}/laufzettel")
    @Produces(MediaType.TEXT_HTML)
    @RolesAllowed({"TEILNEHMER", "ADMIN"})
    public TemplateInstance getLaufzettelTeilnehmer(@PathParam("vid") Long vid, @PathParam("tid") Long tid) {
        Teilnehmer teilnehmer = Teilnehmer.findById(tid);
        Veranstaltung veranstaltung = Veranstaltung.findById(vid);

        if (teilnehmer == null || veranstaltung == null) {
            return laufzettelTeilnehmer.data("error", "Teilnehmer oder Veranstaltung nicht gefunden.");
        }

        if (!jwt.getGroups().contains("ADMIN") && !teilnehmer.email.equals(jwt.getName())) {
            return laufzettelTeilnehmer.data("error", "Zugriff verweigert.");
        }

        return laufzettelTeilnehmer.data(
                "veranstaltung", veranstaltung,
                "teilnehmer", teilnehmer,
                "plan", planService.getPlanFuerTeilnehmer(teilnehmer.email, vid)
        );
    }

    @GET
    @Path("/{vid}/teilnehmer/{tid}/laufzettel-pdf")
    @Produces("application/pdf")
    @RolesAllowed({"TEILNEHMER", "ADMIN"})
    public byte[] getLaufzettelTeilnehmerPdf(@PathParam("vid") Long vid, @PathParam("tid") Long tid) {
        Teilnehmer teilnehmer = Teilnehmer.findById(tid);
        Veranstaltung veranstaltung = Veranstaltung.findById(vid);

        if (teilnehmer == null || veranstaltung == null) {
            throw new RuntimeException("Teilnehmer oder Veranstaltung nicht gefunden.");
        }

        if (!jwt.getGroups().contains("ADMIN") && !teilnehmer.email.equals(jwt.getName())) {
            throw new RuntimeException("Zugriff verweigert.");
        }

        TemplateInstance templateInstance = laufzettelTeilnehmer.data(
                "veranstaltung", veranstaltung,
                "teilnehmer", teilnehmer,
                "plan", planService.getPlanFuerTeilnehmer(teilnehmer.email, vid)
        );

        return pdfService.generatePdf(templateInstance);
    }

    @GET
    @Path("/{vid}/referent/{rid}/laufzettel")
    @Produces(MediaType.TEXT_HTML)
    @RolesAllowed({"REFERENT", "ADMIN"})
    public TemplateInstance getLaufzettelReferent(@PathParam("vid") Long vid, @PathParam("rid") Long rid) {
        Referent referent = Referent.findById(rid);
        Veranstaltung veranstaltung = Veranstaltung.findById(vid);

        if (referent == null || veranstaltung == null) {
            return laufzettelReferent.data("error", "Referent oder Veranstaltung nicht gefunden.");
        }

        if (!jwt.getGroups().contains("ADMIN") && !referent.email.equals(jwt.getName())) {
            return laufzettelReferent.data("error", "Zugriff verweigert.");
        }

        return laufzettelReferent.data(
                "veranstaltung", veranstaltung,
                "referent", referent,
                "plan", planService.getPlanFuerReferent(referent.email, vid)
        );
    }

    @GET
    @Path("/{vid}/referent/{rid}/laufzettel-pdf")
    @Produces("application/pdf")
    @RolesAllowed({"REFERENT", "ADMIN"})
    public byte[] getLaufzettelReferentPdf(@PathParam("vid") Long vid, @PathParam("rid") Long rid) {
        Referent referent = Referent.findById(rid);
        Veranstaltung veranstaltung = Veranstaltung.findById(vid);

        if (referent == null || veranstaltung == null) {
            throw new RuntimeException("Referent oder Veranstaltung nicht gefunden.");
        }

        if (!jwt.getGroups().contains("ADMIN") && !referent.email.equals(jwt.getName())) {
            throw new RuntimeException("Zugriff verweigert.");
        }

        TemplateInstance templateInstance = laufzettelReferent.data(
                "veranstaltung", veranstaltung,
                "referent", referent,
                "plan", planService.getPlanFuerReferent(referent.email, vid)
        );

        return pdfService.generatePdf(templateInstance);
    }

    @GET
    @Path("/{vid}/raum/{rid}/belegungsplan")
    @Produces(MediaType.TEXT_HTML)
    @RolesAllowed("ADMIN")
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
    public TemplateInstance getFreieSlotsReferenten(@PathParam("vid") Long vid) {
        Veranstaltung veranstaltung = Veranstaltung.findById(vid);
        if (veranstaltung == null) {
            return freieSlotsReferenten.data("error", "Veranstaltung nicht gefunden.");
        }
        Map<Long, List<EventSlot>> freieSlots = planService.getFreieSlotsReferenten(vid);
        List<Referent> referenten = Referent.find("SELECT r FROM Referent r JOIN r.veranstaltungen v WHERE v.id = ?1", vid).list();
        return freieSlotsReferenten.data("veranstaltung", veranstaltung)
                .data("freieSlots", freieSlots)
                .data("referenten", referenten);
    }

    @GET
    @Path("/{vid}/freie-slots-referenten-pdf")
    @Produces("application/pdf")
    @RolesAllowed("ADMIN")
    public byte[] getFreieSlotsReferentenPdf(@PathParam("vid") Long vid) {
        Veranstaltung veranstaltung = Veranstaltung.findById(vid);
        if (veranstaltung == null) {
            throw new RuntimeException("Veranstaltung nicht gefunden.");
        }
        Map<Long, List<EventSlot>> freieSlots = planService.getFreieSlotsReferenten(vid);
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
    public TemplateInstance getFreieSlotsTeilnehmer(@PathParam("vid") Long vid) {
        Veranstaltung veranstaltung = Veranstaltung.findById(vid);
        if (veranstaltung == null) {
            return freieSlotsTeilnehmer.data("error", "Veranstaltung nicht gefunden.");
        }
        Map<Long, List<EventSlot>> freieSlots = planService.getFreieSlotsTeilnehmer(vid);
        List<Teilnehmer> teilnehmer = Teilnehmer.find("SELECT t FROM Teilnehmer t JOIN t.veranstaltungen v WHERE v.id = ?1", vid).list();
        return freieSlotsTeilnehmer.data("veranstaltung", veranstaltung)
                .data("freieSlots", freieSlots)
                .data("teilnehmer", teilnehmer);
    }

    @GET
    @Path("/{vid}/freie-slots-teilnehmer-pdf")
    @Produces("application/pdf")
    @RolesAllowed("ADMIN")
    public byte[] getFreieSlotsTeilnehmerPdf(@PathParam("vid") Long vid) {
        Veranstaltung veranstaltung = Veranstaltung.findById(vid);
        if (veranstaltung == null) {
            throw new RuntimeException("Veranstaltung nicht gefunden.");
        }
        Map<Long, List<EventSlot>> freieSlots = planService.getFreieSlotsTeilnehmer(vid);
        List<Teilnehmer> teilnehmer = Teilnehmer.find("SELECT t FROM Teilnehmer t JOIN t.veranstaltungen v WHERE v.id = ?1", vid).list();
        TemplateInstance templateInstance = freieSlotsTeilnehmer.data("veranstaltung", veranstaltung)
                .data("freieSlots", freieSlots)
                .data("teilnehmer", teilnehmer);
        return pdfService.generatePdf(templateInstance);
    }
}