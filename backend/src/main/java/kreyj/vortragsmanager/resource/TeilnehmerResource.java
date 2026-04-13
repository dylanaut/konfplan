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
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class TeilnehmerResource {

    @Inject
    TeilnehmerService teilnehmerService;

    @GET
    public Response getAlleVeranstaltungsteilnehmer(@QueryParam("vid") Long vid) {
        return Response.ok(teilnehmerService.findAll(vid)).build();
    }

    @GET
    @Path("/{id}")
    public Response getTeilnehmer(@PathParam("id") Long id) {
        return Response.ok(teilnehmerService.findById(id)).build();
    }

    @POST
    @Transactional
    public Response createTeilnehmer(Teilnehmer user, @QueryParam("vid") Long vid) {
        Teilnehmer created = teilnehmerService.createTeilnehmer(user, vid);
        return Response.status(Response.Status.CREATED).entity(created).build();
    }

    @PUT
    @Path("/{id}")
    @Transactional
    public Response updateTeilnehmer(@PathParam("id") Long id, Teilnehmer user, @QueryParam("vid") Long vid) {
        Teilnehmer updated = teilnehmerService.updateTeilnehmer(id, user, vid);
        if (updated == null) return Response.status(Response.Status.NOT_FOUND).build();
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
    public Response uploadCsv(FileUploadDto data, @QueryParam("vid") Long vid) {
        try {
            int count = teilnehmerService.importFromCsv(data.file.uploadedFile().toFile().toPath(), vid);
            return Response.ok("Import erfolgreich: " + count + " Teilnehmer angelegt.").build();
        } catch (Exception e) {
            return Response.status(Response.Status.BAD_REQUEST).entity("Fehler beim Import: " + e.getMessage()).build();
        }
    }
}
