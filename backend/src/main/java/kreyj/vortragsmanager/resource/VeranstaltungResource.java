package kreyj.vortragsmanager.resource;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import kreyj.vortragsmanager.dto.*;
import kreyj.vortragsmanager.entity.*;
import kreyj.vortragsmanager.service.*;

import java.util.List;

@Path("/api/veranstaltungen")
@RolesAllowed("ADMIN")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class VeranstaltungResource {

    @Inject
    VeranstaltungService veranstaltungService;

    @Inject
    AdminService adminService;

    @Inject
    OptimierungService optimierungService;

    @Inject
    PlanService planService;

    // --- BASIS: VERANSTALTUNGEN (DTO-basiert) ---

    @GET
    public List<VeranstaltungDto> getAll() {
        return veranstaltungService.listAll();
    }

    @GET
    @Path("/{vid}")
    public Response getOne(@PathParam("vid") Long vid) {
        VeranstaltungDto v = veranstaltungService.findById(vid);
        if (v == null) return Response.status(Response.Status.NOT_FOUND).build();
        return Response.ok(v).build();
    }

    @POST
    public Response create(VeranstaltungDto vDto) {
        try {
            VeranstaltungDto saved = veranstaltungService.save(vDto);
            return Response.status(Response.Status.CREATED).entity(saved).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
        }
    }

    @PUT
    @Path("/{id}")
    public Response update(@PathParam("id") Long id, VeranstaltungDto vDto) {
        vDto.id = id;
        try {
            VeranstaltungDto updated = veranstaltungService.save(vDto);
            if (updated == null) return Response.status(Response.Status.NOT_FOUND).build();
            return Response.ok(updated).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
        }
    }

    @DELETE
    @Path("/{id}")
    public Response delete(@PathParam("id") Long id) {
        boolean deleted = veranstaltungService.delete(id);
        if (!deleted) return Response.status(Response.Status.NOT_FOUND).build();
        return Response.noContent().build();
    }

    // --- HIERARCHISCH ---

    @GET
    @Path("/{vid}/benutzer")
    public List<UserDto> getBenutzer(@PathParam("vid") Long vid) {
        return adminService.getAllUsers(vid);
    }

    @POST
    @Path("/{vid}/benutzer")
    public Response createBenutzer(@PathParam("vid") Long vid, UserDto userDto) {
        UserDto created = adminService.createUser(userDto, vid);
        return Response.status(Response.Status.CREATED).entity(created).build();
    }

    @GET
    @Path("/{vid}/vortraege")
    public List<Vortrag> getVortraege(@PathParam("vid") Long vid) {
        return adminService.getAllVortraege(vid);
    }

    @GET
    @Path("/{vid}/slots")
    public List<EventSlot> getSlots(@PathParam("vid") Long vid) {
        return adminService.getAllEventSlots(vid);
    }

    @GET
    @Path("/{vid}/stats") // Fehlender Endpunkt für den Test und das Dashboard
    public List<VortragStatDto> getStats(@PathParam("vid") Long vid) {
        return adminService.getStats(vid);
    }

    // --- PLANUNG & ERGEBNISSE ---

    @GET
    @Path("/{vid}/plan/details")
    public List<VortragBelegungDto> getDetaillierterPlan(@PathParam("vid") Long vid) {
        return planService.getDetaillierterPlan(vid);
    }

    @GET
    @Path("/{vid}/plan/qualitaet")
    public PlanQualitaetDto getPlanQualitaet(@PathParam("vid") Long vid) {
        return planService.getPlanQualitaet(vid);
    }

    @POST
    @Path("/{vid}/optimierung/start")
    public Response starteOptimierung(@PathParam("vid") Long vid, SolverConfigDto config) {
        try {
            optimierungService.starteOptimierung(vid, config);
            return Response.ok("Optimierung erfolgreich abgeschlossen.").build();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("Fehler bei der Optimierung: " + e.getMessage()).build();
        }
    }
}
