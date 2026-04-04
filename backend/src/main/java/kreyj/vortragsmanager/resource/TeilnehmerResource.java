package kreyj.vortragsmanager.resource;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import kreyj.vortragsmanager.dto.FileUploadDto;
import kreyj.vortragsmanager.entity.Teilnehmer;
import kreyj.vortragsmanager.entity.User;
import kreyj.vortragsmanager.service.TeilnehmerService;

@Path("/api/admin/teilnehmer")
@RolesAllowed("ADMIN")
public class TeilnehmerResource {

    @Inject
    TeilnehmerService teilnehmerService;

    @GET
    public Response getTeilnehmer() {
        return Response.ok(teilnehmerService.findAll()).build();
    }

    @GET
    @Path("/{id}")
    public Response getTeilnehmer(@PathParam("id") Long id) {
        return Response.ok(teilnehmerService.findById(id)).build();
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Transactional
    public Response createTeilnehmer(User user) {
        User created = teilnehmerService.createTeilnehmer(user);
        return Response.status(Response.Status.CREATED).entity(created).build();
    }

    @PUT
    @Path("/{id}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Transactional
    public Response updateTeilnehmer(@PathParam("id") Long id, Teilnehmer user) {
        Teilnehmer updated = teilnehmerService.updateTeilnehmer(id, user);
        if (updated == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.ok(updated).build();
    }

    @DELETE
    @Path("/{id}")
    public Response deleteTeilnehmer(@PathParam("id") Long id) {
        User byId = teilnehmerService.findById(id);
        if (byId == null) return Response.status(Response.Status.NOT_FOUND).build();
        teilnehmerService.deleteUser(byId);
        return Response.noContent().build();
    }

    @PATCH
    @Path("/{id}/toggle")
    public Response toggleActive(@PathParam("id") Long id) {
        User byId = teilnehmerService.findById(id);
        if (byId == null) return Response.status(Response.Status.NOT_FOUND).build();
        teilnehmerService.toggleActive(byId);
        return Response.noContent().build();
    }

    @POST
    @Path("/import")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public Response uploadCsv(FileUploadDto data) {
        try {
            teilnehmerService.importFromCsv(data.file.uploadedFile().toFile().toPath());
            return Response.ok("Import erfolgreich").build();
        } catch (Exception e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("Fehler beim Import: " + e.getMessage()).build();
        }
    }
}
