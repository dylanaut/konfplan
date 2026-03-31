package kreyj.vortragsmanager.resource;

import io.quarkus.security.Authenticated;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
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
public class AdminResource {

    @Inject
    AdminService adminService;

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
    @Consumes(MediaType.APPLICATION_JSON)
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
    @Consumes(MediaType.APPLICATION_JSON)
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