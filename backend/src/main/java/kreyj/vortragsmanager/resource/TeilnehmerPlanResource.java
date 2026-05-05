package kreyj.vortragsmanager.resource;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import kreyj.vortragsmanager.dto.PrioritaetRequest;
import kreyj.vortragsmanager.dto.VeranstaltungDto;
import kreyj.vortragsmanager.dto.VerfuegbarkeitDto;
import kreyj.vortragsmanager.dto.ZuweisungDto;
import kreyj.vortragsmanager.entity.*;
import kreyj.vortragsmanager.service.PlanService;
import kreyj.vortragsmanager.service.PrioritaetService;
import kreyj.vortragsmanager.util.JwtHelper;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Path("/api/teilnehmer")
@RolesAllowed({"TEILNEHMER", "ADMIN"})
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class TeilnehmerPlanResource {

    @Inject
    JsonWebToken jwt;

    @Inject
    PlanService planService;

    @Inject
    PrioritaetService prioritaetService;

    @GET
    @Path("/veranstaltungen")
    public List<VeranstaltungDto> getMeineVeranstaltungen() {
        String email = JwtHelper.getUserPrincipalName(jwt);
        Teilnehmer t = Teilnehmer.find("email", email).firstResult();
        if (t == null) return List.of();
        return t.veranstaltungen.stream()
                .map(VeranstaltungResource::mapVeranstaltungToDto)
                .collect(Collectors.toList());
    }

    @GET
    @Path("/zuweisungen")
    public List<ZuweisungDto> getPlan(@QueryParam("vid") Long vid) {
        // Hinweis: Aktuell ignoriert PlanService vid und gibt alles zurück. 
        // Für Multi-Event-Support müsste PlanService angepasst werden.
        return planService.getPlanFuerTeilnehmer(JwtHelper.getUserPrincipalName(jwt));
    }

    @GET
    @Path("/prios")
    public List<Prioritaet> getPrios(@QueryParam("vid") Long vid) {
        return prioritaetService.getPrioritaetenForUser(JwtHelper.getUserPrincipalName(jwt));
    }

    @POST
    @Path("/prios")
    public Response savePriorities(List<PrioritaetRequest> requests) {
        prioritaetService.savePrioritaeten(JwtHelper.getUserPrincipalName(jwt), requests);
        return Response.ok().build();
    }

    @GET
    @Path("/vortraege")
    public List<Vortrag> getVortraege(@QueryParam("vid") Long vid) {
        if (vid == null) return List.of();
        return Vortrag.find("veranstaltung.id", vid).list();
    }

    @GET
    @Path("/veranstaltungen/{vid}/verfuegbarkeiten")
    public List<VerfuegbarkeitDto> getVerfuegbarkeiten(@PathParam("vid") Long vid) {
        Nutzer nutzer = Nutzer.findByEmail(JwtHelper.getUserPrincipalName(jwt));
        if (!(nutzer instanceof Teilnehmer)) throw new WebApplicationException("Kein Teilnehmer", 403);

        return Verfuegbarkeit.find("nutzer = ?1 and slot.veranstaltung.id = ?2", nutzer, vid).stream()
                .map(v -> {
                    Verfuegbarkeit vf = (Verfuegbarkeit) v;
                    return new VerfuegbarkeitDto(vf.nutzer.id, vf.slot.id, vf.isAvailable);
                })
                .collect(Collectors.toList());
    }

    @POST
    @Path("/veranstaltungen/{vid}/verfuegbarkeiten")
    @Transactional
    public Response updateVerfuegbarkeit(@PathParam("vid") Long vid, VerfuegbarkeitDto dto) {
        Nutzer nutzer = Nutzer.findByEmail(JwtHelper.getUserPrincipalName(jwt));
        if (!(nutzer instanceof Teilnehmer)) return Response.status(Response.Status.FORBIDDEN).build();

        Veranstaltung veranstaltung = Veranstaltung.findById(vid);
        if (veranstaltung == null) return Response.status(Response.Status.NOT_FOUND).build();

        // Deadline Check
        if (veranstaltung.deadlineTeilnehmer != null && veranstaltung.deadlineTeilnehmer.isBefore(LocalDateTime.now())) {
            return Response.status(Response.Status.FORBIDDEN)
                    .entity("Die Deadline für Teilnehmer ist bereits abgelaufen.").build();
        }

        EventSlot slot = EventSlot.findById(dto.slotId);
        if (slot == null || !slot.veranstaltung.id.equals(vid)) return Response.status(Response.Status.BAD_REQUEST).build();

        Verfuegbarkeit v = Verfuegbarkeit.find("nutzer = ?1 and slot = ?2", nutzer, slot).firstResult();
        if (v == null) {
            v = new Verfuegbarkeit();
            v.nutzer = nutzer;
            v.slot = slot;
        }
        v.isAvailable = dto.isAvailable;
        v.persist();
        return Response.ok().build();
    }
}
