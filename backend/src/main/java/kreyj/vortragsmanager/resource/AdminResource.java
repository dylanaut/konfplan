package kreyj.vortragsmanager.resource;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import kreyj.vortragsmanager.dto.VortragStatDto;
import kreyj.vortragsmanager.entity.Prioritaet;
import kreyj.vortragsmanager.entity.Vortrag;
import kreyj.vortragsmanager.entity.User;
import kreyj.vortragsmanager.service.AdminService;

import java.util.List;

@Path("/api/admin")
@RolesAllowed("ADMIN")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AdminResource {

    @Inject
    AdminService adminService;

    // --- BENUTZER VERWALTUNG ---

    @GET
    @Path("/users")
    public List<User> getAllUsers() {
        return adminService.getAllUsers();
    }

    @POST
    @Path("/users")
    public Response createUser(User user) {
        User created = adminService.createUser(user);
        return Response.status(Response.Status.CREATED).entity(created).build();
    }

    @PUT
    @Path("/users/{id}")
    public Response updateUser(@PathParam("id") Long id, User user) {
        User updated = adminService.updateUser(id, user);
        if (updated == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.ok(updated).build();
    }

    @DELETE
    @Path("/users/{id}")
    public Response deleteUser(@PathParam("id") Long id) {
        boolean deleted = adminService.deleteUser(id);
        if (!deleted) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.noContent().build();
    }

    @PATCH
    @Path("/users/{id}/toggle")
    public Response toggleUserStatus(@PathParam("id") Long id) {
        adminService.toggleUserStatus(id);
        return Response.ok().build();
    }

    // --- VORTRAGS VERWALTUNG ---

    @GET
    @Path("/vortraege")
    public List<Vortrag> getAllVortraege() {
        return adminService.getAllVortraege();
    }

    @GET
    @Path("/referenten")
    public List<User> getAllReferenten() {
        return adminService.getAllReferenten();
    }

    @PUT
    @Path("/vortraege/{id}")
    @Transactional
    public Response updateVortrag(@PathParam("id") Long id, Vortrag updatedVortrag) {
        Vortrag saved = adminService.updateVortrag(id, updatedVortrag);
        if (saved == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.ok(saved).build();
    }

    @PUT
    @Path("/teilnehmer/{userId}/prioritaet")
    @Transactional
    public Response forceUpdatePrioritaet(@PathParam("userId") Long userId, Prioritaet newPrio) {
        boolean updated = adminService.forceUpdatePrioritaet(userId, newPrio);
        if (!updated) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.ok().build();
    }

    @GET
    @Path("/stats")
    public List<VortragStatDto> getStats() {
        return adminService.getStats();
    }

    @GET
    @Path("/export/csv")
    @Produces(MediaType.TEXT_PLAIN)
    public Response exportCsv() {
        return adminService.exportCsv();
    }
}
