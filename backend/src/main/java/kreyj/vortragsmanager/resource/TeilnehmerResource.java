package kreyj.vortragsmanager.resource;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.persistence.OptimisticLockException;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import kreyj.vortragsmanager.dto.FileUploadDto;
import kreyj.vortragsmanager.dto.NutzerDto;
import kreyj.vortragsmanager.entity.Nutzer;
import kreyj.vortragsmanager.entity.Teilnehmer;
import kreyj.vortragsmanager.service.TeilnehmerService;
import kreyj.vortragsmanager.util.JwtHelper;
import org.eclipse.microprofile.jwt.JsonWebToken;

@Path("/api/teilnehmer")
@RolesAllowed({"ADMIN"})
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class TeilnehmerResource {
    @Inject
    JsonWebToken jwt;

    @Inject
    TeilnehmerService teilnehmerService;

    @GET
    public Response getAlleVeranstaltungsteilnehmer(@QueryParam("vid") Long vid) {
        return Response.ok(teilnehmerService.findAll(vid)).build();
    }

    @GET
    @Path("/{id}")
    @RolesAllowed("ADMIN")
    public Response getTeilnehmer(@PathParam("id") Long id) {
        if (id == null) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }
        Nutzer nutzer = teilnehmerService.findById(id);
        if (null == nutzer) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        if (!nutzer.email.equals(JwtHelper.getUserPrincipalName(jwt)) && !jwt.getGroups().contains("ADMIN")) {
            return Response.status(Response.Status.FORBIDDEN).build();
        }
        return Response.ok(AdminResource.mapNutzerToDto(nutzer)).build();
    }

    @GET
    @Path("/profile")
    @RolesAllowed("TEILNEHMER")
    public Response getTeilnehmerProfile() {
        Teilnehmer teilnehmer = teilnehmerService.findByEmail(JwtHelper.getUserPrincipalName(jwt));

        if (null == teilnehmer) {
            throw new WebApplicationException("Teilnehmer not found", Response.Status.NOT_FOUND);
        }

        return Response.ok(AdminResource.mapNutzerToDto(teilnehmer)).build();
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
    public Response updateTeilnehmer(@PathParam("id") Long id, NutzerDto user, @QueryParam("vid") Long vid) {
        try {
            Teilnehmer updated = teilnehmerService.updateTeilnehmer(id, user, vid);
            if (updated == null) {
                return Response.status(Response.Status.NOT_FOUND).build();
            }
            return Response.ok(updated).build();
        } catch (OptimisticLockException e) {
            return Response.status(Response.Status.CONFLICT).entity(e.getMessage()).build();
        }
    }


    @PUT
    @Path("/profile")
    @RolesAllowed("TEILNEHMER")
    @Transactional
    public Response updateTeilnehmerProfile(NutzerDto teilnehmerDto) {
        if (teilnehmerDto == null) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }

        String email = JwtHelper.getUserPrincipalName(jwt);
        Teilnehmer teilnehmer = teilnehmerService.findByEmail(email);

        try {
            Teilnehmer updated = teilnehmerService.updateTeilnehmerProfile(teilnehmer, teilnehmerDto);
            if (updated == null) {
                return Response.status(Response.Status.NOT_FOUND).build();
            }
            return Response.ok(AdminResource.mapNutzerToDto(updated)).build();
        } catch (OptimisticLockException e) {
            return Response.status(Response.Status.CONFLICT).entity(e.getMessage()).build();
        }
    }

    @DELETE
    @Path("/{id}")
    public Response deleteTeilnehmer(@PathParam("id") Long id) {
        Nutzer nutzer = teilnehmerService.findById(id);
        if (nutzer == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        teilnehmerService.deleteUser(nutzer);
        return Response.noContent().build();
    }

    @PATCH
    @Path("/{id}/toggle")
    public Response toggleActive(@PathParam("id") Long id) {
        Nutzer byId = teilnehmerService.findById(id);
        if (byId == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
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
