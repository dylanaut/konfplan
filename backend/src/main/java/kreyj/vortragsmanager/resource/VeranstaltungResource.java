package kreyj.vortragsmanager.resource;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import kreyj.vortragsmanager.entity.Veranstaltung;
import kreyj.vortragsmanager.service.VeranstaltungService;
import java.util.List;

@Path("/api/veranstaltung")
@RolesAllowed("ADMIN") // Nur Admins dürfen Veranstaltungen verwalten
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class VeranstaltungResource {

    @Inject
    VeranstaltungService veranstaltungService;

    @GET
    public List<Veranstaltung> getAll() {
        return veranstaltungService.listAll();
    }

    @GET
    @Path("/{id}")
    public Response getOne(@PathParam("id") Long id) {
        Veranstaltung v = veranstaltungService.findById(id);
        if (v == null) return Response.status(Response.Status.NOT_FOUND).build();
        return Response.ok(v).build();
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

    @PUT
    @Path("/{id}")
    public Response update(@PathParam("id") Long id, Veranstaltung v) {
        v.id = id;
        try {
            Veranstaltung updated = veranstaltungService.save(v);
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
}
