package kreyj.vortragsmanager.resource;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import kreyj.vortragsmanager.dto.FileUploadDto;
import kreyj.vortragsmanager.dto.SolverConfigDto;
import kreyj.vortragsmanager.dto.VortragStatDto;
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
    ReferentService referentService;

    @Inject
    TeilnehmerService teilnehmerService;

    @Inject
    OptimierungService optimierungService;

    // --- BASIS: VERANSTALTUNGEN ---

    @GET
    public List<Veranstaltung> getAll() {
        return veranstaltungService.listAll();
    }

    @POST
    public Response create(Veranstaltung v) {
        try {
            Veranstaltung saved = veranstaltungService.save(v);
            return Response.status(Response.Status.CREATED).entity(saved).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
        }
    }

    // --- HIERARCHISCH: OPTIMIERUNG ---

    @POST
    @Path("/{vid}/optimierung/start")
    public Response starteOptimierung(@PathParam("vid") Long vid, SolverConfigDto config) {
        try {
            optimierungService.starteOptimierung(vid, config);
            return Response.ok("Optimierungsprozess wurde gestartet.").build();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("Fehler beim Starten der Optimierung: " + e.getMessage()).build();
        }
    }

    // --- HIERARCHISCH: BENUTZER ---

    @GET
    @Path("/{vid}/benutzer")
    public List<User> getBenutzer(@PathParam("vid") Long vid) {
        return adminService.getAllUsers(); // Hier ggf. vid-Filterung im Service ergänzen
    }

    @POST
    @Path("/{vid}/benutzer")
    public Response createBenutzer(@PathParam("vid") Long vid, User user) {
        User created = adminService.createUser(user, vid);
        return Response.status(Response.Status.CREATED).entity(created).build();
    }

    // --- HIERARCHISCH: VORTRÄGE ---

    @GET
    @Path("/{vid}/vortraege")
    public List<Vortrag> getVortraege(@PathParam("vid") Long vid) {
        return adminService.getAllVortraege(vid);
    }

    // --- HIERARCHISCH: SLOTS ---

    @GET
    @Path("/{vid}/slots")
    public List<EventSlot> getSlots(@PathParam("vid") Long vid) {
        return adminService.getAllEventSlots(vid);
    }

    // ... (Weitere Endpunkte wie bisher)
}
