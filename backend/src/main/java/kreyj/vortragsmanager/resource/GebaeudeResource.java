package kreyj.vortragsmanager.resource;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import kreyj.vortragsmanager.dto.FileUploadDto;
import kreyj.vortragsmanager.entity.Gebaeude;
import kreyj.vortragsmanager.entity.Raum;
import kreyj.vortragsmanager.service.GebaeudeService;
import kreyj.vortragsmanager.service.RaumService;
import java.util.List;

@Path("/api/gebaeude")
@RolesAllowed("ADMIN")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class GebaeudeResource {

    @Inject
    GebaeudeService gebaeudeService;

    @Inject
    RaumService raumService;

    // --- GEBÄUDE ---

    @GET
    public List<Gebaeude> getAll() {
        return gebaeudeService.listAll();
    }

    @POST
    @Path("/import")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public Response importGebaeude(FileUploadDto data) {
        try {
            int count = gebaeudeService.importGebaeudeWithRaeumeFromCsv(data.file.uploadedFile().toFile().toPath());
            return Response.ok("Import erfolgreich: " + count + " Gebäude (inkl. Räumen) angelegt.").build();
        } catch (Exception e) {
            return Response.status(Response.Status.BAD_REQUEST).entity("Fehler: " + e.getMessage()).build();
        }
    }

    @GET
    @Path("/{id}")
    public Response getOne(@PathParam("id") Long id) {
        Gebaeude g = gebaeudeService.findById(id);
        if (g == null) return Response.status(Response.Status.NOT_FOUND).build();
        return Response.ok(g).build();
    }

    @POST
    public Response create(Gebaeude g) {
        Gebaeude saved = gebaeudeService.save(g);
        return Response.status(Response.Status.CREATED).entity(saved).build();
    }

    @PUT
    @Path("/{id}")
    public Response update(@PathParam("id") Long id, Gebaeude g) {
        g.id = id;
        Gebaeude updated = gebaeudeService.save(g);
        if (updated == null) return Response.status(Response.Status.NOT_FOUND).build();
        return Response.ok(updated).build();
    }

    @DELETE
    @Path("/{id}")
    public Response delete(@PathParam("id") Long id) {
        boolean deleted = gebaeudeService.delete(id);
        if (!deleted) return Response.status(Response.Status.NOT_FOUND).build();
        return Response.noContent().build();
    }

    // --- RÄUME ALS SUB-RESSOURCE VON GEBÄUDEN ---

    @GET
    @Path("/{gid}/raeume")
    public List<Raum> getRaeumeByGebaeude(@PathParam("gid") Long gid) {
        return raumService.listByGebaeude(gid);
    }

    @POST
    @Path("/{gid}/raeume")
    public Response createRaum(@PathParam("gid") Long gid, Raum r) {
        try {
            Raum saved = raumService.save(r, gid);
            return Response.status(Response.Status.CREATED).entity(saved).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
        }
    }

    @PUT
    @Path("/{gid}/raeume/{rid}")
    public Response updateRaum(@PathParam("gid") Long gid, @PathParam("rid") Long rid, Raum r) {
        r.id = rid;
        try {
            Raum saved = raumService.save(r, gid);
            if (saved == null) return Response.status(Response.Status.NOT_FOUND).build();
            return Response.ok(saved).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
        }
    }

    @DELETE
    @Path("/{gid}/raeume/{rid}")
    public Response deleteRaum(@PathParam("gid") Long gid, @PathParam("rid") Long rid) {
        boolean deleted = raumService.delete(rid);
        if (!deleted) return Response.status(Response.Status.NOT_FOUND).build();
        return Response.noContent().build();
    }

    @POST
    @Path("/{gid}/raeume/import")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public Response importRaeume(@PathParam("gid") Long gid, FileUploadDto data) {
        try {
            int count = raumService.importFromCsv(data.file.uploadedFile().toFile().toPath(), gid);
            return Response.ok("Import erfolgreich: " + count + " Räume angelegt.").build();
        } catch (Exception e) {
            return Response.status(Response.Status.BAD_REQUEST).entity("Fehler: " + e.getMessage()).build();
        }
    }
}
