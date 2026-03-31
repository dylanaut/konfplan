package kreyj.vortragsmanager.resource;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import kreyj.vortragsmanager.dto.ParticipantImportDto;
import kreyj.vortragsmanager.entity.User;
import kreyj.vortragsmanager.service.ParticipantService;

@Path("/api/admin/participants")
@RolesAllowed("ADMIN") // Nur Admins dürfen importieren
public class ParticipantResource {

    @Inject
    ParticipantService participantService;

    @GET
    public Response getParticipants() {
        return Response.ok(participantService.findAll()).build();
    }

    @GET
    @Path("/{id}")
    public Response getParticipant(Long id) {
        return Response.ok(participantService.findById(id)).build();
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Transactional
    public Response createParticipant(User user) {
        User created = participantService.createParticipant(user);
        return Response.status(Response.Status.CREATED).entity(created).build();
    }

    @PUT
    @Path("/{id}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Transactional
    public Response updateParticipant(@PathParam("id") Long id, User user) {
        User updated = participantService.updateParticipant(id, user);
        if (updated == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.ok(updated).build();
    }

    @DELETE
    @Path("/{id}")
    public Response deleteParticipant(Long id) {
        if (id == null) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }

        User byId = participantService.findById(id);

        if (byId == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        participantService.deleteUser(byId);

        return Response.noContent().build();
    }

    @PATCH
    @Path("/{id}/toggle")
    public Response toggleActive(Long id) {
        if (id == null) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }

        User byId = participantService.findById(id);

        if (byId == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        participantService.toggleActive(byId);

        return Response.noContent().build();
    }

    @POST
    @Path("/import")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public Response uploadCsv(ParticipantImportDto data) {
        try {
            participantService.importFromCsv(data.file.uploadedFile().toFile().toPath());
            return Response.ok("Import erfolgreich").build();
        } catch (Exception e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("Fehler beim Import: " + e.getMessage()).build();
        }
    }
}