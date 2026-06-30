package kreyj.konfplan.adapter.rest;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import kreyj.konfplan.domain.service.ReferentService;
import kreyj.konfplan.domain.service.TeilnehmerService;
import kreyj.konfplan.domain.service.KalenderService;
import kreyj.konfplan.persistence.Referent;
import kreyj.konfplan.persistence.Teilnehmer;
import kreyj.konfplan.persistence.Veranstaltung;
import kreyj.konfplan.util.DateHelper;
import net.fortuna.ical4j.model.Calendar;
import org.eclipse.microprofile.jwt.JsonWebToken;

import static kreyj.konfplan.util.TemplateExtensions.truncTo;

@Path("/api/kalender")
public class KalenderResource {

    @Inject
    KalenderService kalenderService;

    @Inject
    TeilnehmerService teilnehmerService;

    @Inject
    ReferentService referentService;


    @GET
    @Path("/admin/{veranstaltungId}")
    @RolesAllowed("ADMIN")
    @Produces("text/calendar")
    @Transactional
    public Response getAdminKalender(@PathParam("veranstaltungId") Long veranstaltungId) {
        Veranstaltung veranstaltung = Veranstaltung.findById(veranstaltungId);
        if (null == veranstaltung) {
            return Response.status(Response.Status.NOT_FOUND).entity("Veranstaltung nicht gefunden").build();
        }
        Calendar calendar = kalenderService.generateAdminCalendar(veranstaltung);
        return Response.ok(calendar.toString())
            .header("Content-Disposition", "attachment; filename=\"KonfPlan_" +
                truncTo(veranstaltung.getName(), 15)
                + "_" + DateHelper.DATE_FORMAT.format(veranstaltung.getBeginntAm()) + ".ics\"")
            .build();
    }


    @GET
    @Path("/teilnehmer/{veranstaltungId}")
    @RolesAllowed("TEILNEHMER")
    @Produces("text/calendar")
    @Transactional
    public Response getTeilnehmerKalender(@PathParam("veranstaltungId") Long veranstaltungId, @Context JsonWebToken jwt) {
        Veranstaltung veranstaltung = Veranstaltung.findById(veranstaltungId);
        if (null == veranstaltung) {
            return Response.status(Response.Status.NOT_FOUND).entity("Veranstaltung nicht gefunden").build();
        }
        String tnEmail = jwt.getName();
        // Assuming the username is the email, which is unique
        Teilnehmer teilnehmer = teilnehmerService.findByEmail(tnEmail);
        if (null == teilnehmer) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity("Teilnehmer '" + tnEmail + "' nicht gefunden.")
                .build();
        }
        Calendar calendar = kalenderService.generateTeilnehmerCalendar(veranstaltung, teilnehmer);
        return Response.ok(calendar.toString())
            .header("Content-Disposition", "attachment; filename=\"KonfPlan_" +
                truncTo(veranstaltung.getName(), 15)
                + "_" + DateHelper.DATE_FORMAT.format(veranstaltung.getBeginntAm()) + ".ics\"")
            .build();
    }


    @GET
    @Path("/referent/{veranstaltungId}")
    @RolesAllowed("REFERENT")
    @Produces("text/calendar")
    @Transactional
    public Response getReferentKalender(@PathParam("veranstaltungId") Long veranstaltungId, @Context JsonWebToken jwt) {
        Veranstaltung veranstaltung = Veranstaltung.findById(veranstaltungId);
        if (null == veranstaltung) {
            return Response.status(Response.Status.NOT_FOUND).entity("Veranstaltung nicht gefunden").build();
        }
        String refEmail = jwt.getName();
        Referent referent = referentService.findByEmail(refEmail);
        if (null == referent) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity("Referent '" + refEmail + "' nicht gefunden").build();
        }
        Calendar calendar = kalenderService.generateReferentCalendar(veranstaltung, referent);
        return Response.ok(calendar.toString())
            .header("Content-Disposition", "attachment; filename=\"KonfPlan_" +
                truncTo(veranstaltung.getName(), 15)
                + "_" + DateHelper.DATE_FORMAT.format(veranstaltung.getBeginntAm()) + ".ics\"")
            .build();
    }
}
