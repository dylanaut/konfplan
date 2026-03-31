package kreyj.vortragsmanager.resource;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import kreyj.vortragsmanager.dto.TalkStatDto;
import kreyj.vortragsmanager.entity.Priority;
import kreyj.vortragsmanager.entity.Talk;
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
    @Path("/talks")
    public List<Talk> getAllTalks() {
        return adminService.getAllTalks();
    }

    @GET
    @Path("/speakers")
    public List<User> getAllSpeakers() {
        return adminService.getAllSpeakers();
    }

    @PUT
    @Path("/talks/{id}")
    @Transactional
    public Response updateTalk(@PathParam("id") Long id, Talk updatedTalk) {
        Talk saved = adminService.updateTalk(id, updatedTalk);
        if (saved == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.ok(saved).build();
    }

    @PUT
    @Path("/participants/{userId}/priority")
    @Transactional
    public Response forceUpdatePriority(@PathParam("userId") Long userId, Priority newPrio) {
        boolean updated = adminService.forceUpdatePriority(userId, newPrio);
        if (!updated) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.ok().build();
    }

    @GET
    @Path("/stats")
    public List<TalkStatDto> getStats() {
        return adminService.getStats();
    }

    @GET
    @Path("/export/csv")
    @Produces(MediaType.TEXT_PLAIN)
    public Response exportCsv() {
        return adminService.exportCsv();
    }
}
