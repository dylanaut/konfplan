package kreyj.vortragsmanager.resource;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import kreyj.vortragsmanager.dto.FileUploadDto;
import kreyj.vortragsmanager.entity.Raum;
import kreyj.vortragsmanager.service.RaumService;
import java.util.List;

@Path("/api/raum")
@RolesAllowed("ADMIN")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class RaumResource {

    @Inject
    RaumService raumService;

    @GET
    public List<Raum> getAll() {
        return raumService.listAll();
    }

    @POST
    @Path("/import")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public Response importRaeume(FileUploadDto data) {
        try {
            int count = raumService.importFromCsv(data.file.uploadedFile().toFile().toPath());
            return Response.ok("Import erfolgreich: " + count + " Räume angelegt.").build();
        } catch (Exception e) {
            return Response.status(Response.Status.BAD_REQUEST).entity("Fehler: " + e.getMessage()).build();
        }
    }

    @GET
    @Path("/{id}")
    public Response getOne(@PathParam("id") Long id) {
        Raum r = raumService.findById(id);
        if (r == null) return Response.status(Response.Status.NOT_FOUND).build();
        return Response.ok(r).build();
    }

    @POST
    public Response create(Raum r) {
        Raum saved = raumService.save(r);
        return Response.status(Response.Status.CREATED).entity(saved).build();
    }

    @PUT
    @Path("/{id}")
    public Response update(@PathParam("id") Long id, Raum r) {
        r.id = id;
        Raum updated = raumService.save(r);
        if (updated == null) return Response.status(Response.Status.NOT_FOUND).build();
        return Response.ok(updated).build();
    }

    @DELETE
    @Path("/{id}")
    public Response delete(@PathParam("id") Long id) {
        boolean deleted = raumService.delete(id);
        if (!deleted) return Response.status(Response.Status.NOT_FOUND).build();
        return Response.noContent().build();
    }
}
