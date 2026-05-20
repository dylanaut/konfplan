package kreyj.konfplan.resource;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.persistence.OptimisticLockException;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import kreyj.konfplan.dto.FileUploadDto;
import kreyj.konfplan.dto.NutzerDto;
import kreyj.konfplan.persistence.Nutzer;
import kreyj.konfplan.persistence.Teilnehmer;
import kreyj.konfplan.service.TeilnehmerService;
import kreyj.konfplan.util.JwtHelper;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Path("/api/teilnehmer")
@RolesAllowed({"ADMIN"})
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Teilnehmer", description = "Endpunkte zur Verwaltung von Teilnehmern")
public class TeilnehmerResource {
    @Inject
    JsonWebToken jwt;

    @Inject
    TeilnehmerService teilnehmerService;

    @GET
    @Operation(summary = "Alle Teilnehmer einer Veranstaltung abrufen", description = "Gibt eine Liste aller Teilnehmer für eine bestimmte Veranstaltung zurück.")
    public Response getAlleVeranstaltungsteilnehmer(@QueryParam("vid") Long vid) {
        return Response.ok(teilnehmerService.findAll(vid)).build();
    }

    @GET
    @Path("/{id}")
    @RolesAllowed("ADMIN")
    @Operation(summary = "Einen Teilnehmer abrufen", description = "Ruft einen einzelnen Teilnehmer anhand seiner ID ab.")
    public Response getTeilnehmer(@PathParam("id") Long id) {
        if (id == null) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }
        Nutzer nutzer = teilnehmerService.findById(id);
        if (null == nutzer) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        if (!nutzer.getEmail().equals(JwtHelper.getUserPrincipalName(jwt)) && !jwt.getGroups().contains("ADMIN")) {
            return Response.status(Response.Status.FORBIDDEN).build();
        }
        return Response.ok(AdminResource.mapNutzerToDto(nutzer)).build();
    }

    @GET
    @Path("/profile")
    @RolesAllowed("TEILNEHMER")
    @Operation(summary = "Eigenes Teilnehmerprofil abrufen", description = "Ruft das Profil des aktuell angemeldeten Teilnehmers ab.")
    public Response getTeilnehmerProfile() {
        Teilnehmer teilnehmer = teilnehmerService.findByEmail(JwtHelper.getUserPrincipalName(jwt));

        if (null == teilnehmer) {
            throw new WebApplicationException("Teilnehmer not found", Response.Status.NOT_FOUND);
        }

        return Response.ok(AdminResource.mapNutzerToDto(teilnehmer)).build();
    }

    @POST
    @Transactional
    @Operation(summary = "Neuen Teilnehmer erstellen", description = "Erstellt einen neuen Teilnehmer für eine Veranstaltung.")
    public Response createTeilnehmer(@RequestBody(description = "Der zu erstellende Teilnehmer", required = true) Teilnehmer user, @QueryParam("vid") Long vid) {
        Teilnehmer created = teilnehmerService.createTeilnehmer(user, vid);
        return Response.status(Response.Status.CREATED).entity(created).build();
    }


    @PUT
    @Path("/{id}")
    @Transactional
    @Operation(summary = "Teilnehmer aktualisieren", description = "Aktualisiert die Daten eines Teilnehmers.")
    public Response updateTeilnehmer(@PathParam("id") Long id, @RequestBody(description = "Die aktualisierten Teilnehmerdaten", required = true) NutzerDto user, @QueryParam("vid") Long vid) {
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
    @Operation(summary = "Eigenes Teilnehmerprofil aktualisieren", description = "Aktualisiert das Profil des aktuell angemeldeten Teilnehmers.")
    public Response updateTeilnehmerProfile(@RequestBody(description = "Die aktualisierten Profildaten", required = true) NutzerDto teilnehmerDto) {
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
    @Operation(summary = "Teilnehmer löschen", description = "Löscht einen Teilnehmer.")
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
    @Operation(summary = "Aktivierungsstatus umschalten", description = "Schaltet den 'isActive'-Status eines Teilnehmers um.")
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
    @Operation(summary = "Teilnehmer importieren", description = "Importiert Teilnehmer für eine Veranstaltung aus einer CSV-Datei.")
    public Response uploadCsv(FileUploadDto data, @QueryParam("vid") Long vid) {
        try {
            int count = teilnehmerService.importFromCsv(data.file.uploadedFile().toFile().toPath(), vid);
            return Response.ok("Import erfolgreich: " + count + " Teilnehmer angelegt.").build();
        } catch (Exception e) {
            return Response.status(Response.Status.BAD_REQUEST).entity("Fehler beim Import: " + e.getMessage()).build();
        }
    }

    @POST
    @Path("/veranstaltungen/{vid}/verfuegbarkeiten/init")
    @RolesAllowed("TEILNEHMER")
    @Transactional
    @Operation(summary = "Initiale Verfügbarkeiten erstellen", description = "Erstellt die initialen Verfügbarkeitseinträge für einen Teilnehmer in einer Veranstaltung.")
    public Response createInitialAvailabilities(@PathParam("vid") Long vid) {
        String email = JwtHelper.getUserPrincipalName(jwt);
        Teilnehmer teilnehmer = teilnehmerService.findByEmail(email);
        if (teilnehmer == null) {
            return Response.status(Response.Status.NOT_FOUND).entity("Teilnehmer nicht gefunden.").build();
        }
        try {
            teilnehmerService.createInitialAvailabilities(teilnehmer.getId(), vid);
            return Response.ok().build();
        } catch (NotFoundException e) {
            return Response.status(Response.Status.NOT_FOUND).entity(e.getMessage()).build();
        }
    }
}