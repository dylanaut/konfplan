package kreyj.konfplan.resource;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import kreyj.konfplan.dto.FileUploadDto;
import kreyj.konfplan.dto.GebaeudeSimpleDto;
import kreyj.konfplan.dto.RaumDto;
import kreyj.konfplan.persistence.Gebaeude;
import kreyj.konfplan.persistence.Raum;
import kreyj.konfplan.service.GebaeudeService;
import kreyj.konfplan.service.RaumService;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.List;

@Path("/api/gebaeude")
@RolesAllowed("ADMIN")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Gebäude & Räume", description = "Verwaltung von Gebäuden und den zugehörigen Räumen")
public class GebaeudeResource {

    @Inject
    GebaeudeService gebaeudeService;

    @Inject
    RaumService raumService;

    // --- GEBÄUDE ---

    @GET
    @Operation(summary = "Alle Gebäude abrufen", description = "Gibt eine Liste aller Gebäude zurück.")
    public List<GebaeudeSimpleDto> getAll(@QueryParam("sortByRooms") String sortByRooms,
                                          @QueryParam("sortDirectionRooms") @DefaultValue("asc") String sortDirectionRooms) {
        return gebaeudeService.listAll()
                .stream()
                .map(gebaeude -> mapToDto(gebaeude))
                .toList();
    }

    @POST
    @Path("/import")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Operation(summary = "Gebäude importieren", description = "Importiert Gebäude und Räume aus einer CSV-Datei.")
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
    @Operation(summary = "Ein Gebäude abrufen", description = "Ruft ein einzelnes Gebäude anhand seiner ID ab.")
    public Response getOne(@PathParam("id") Long id) {
        Gebaeude g = gebaeudeService.findById(id);
        if (g == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.ok(g).build();
    }

    @POST
    @Operation(summary = "Neues Gebäude erstellen", description = "Erstellt ein neues Gebäude.")
    public Response create(@RequestBody(description = "Das zu erstellende Gebäude", required = true) Gebaeude g) {
        Gebaeude saved = gebaeudeService.save(g);
        return Response.status(Response.Status.CREATED).entity(saved).build();
    }

    @PUT
    @Path("/{id}")
    @Operation(summary = "Gebäude aktualisieren", description = "Aktualisiert ein bestehendes Gebäude.")
    public Response update(@PathParam("id") Long id, @RequestBody(description = "Die aktualisierten Gebäudedaten", required = true) Gebaeude g) {
        g.id = id;
        Gebaeude updated = gebaeudeService.save(g);
        if (updated == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.ok(updated).build();
    }

    @DELETE
    @Path("/{id}")
    @Operation(summary = "Gebäude löschen", description = "Löscht ein Gebäude.")
    public Response delete(@PathParam("id") Long id) {
        boolean deleted = gebaeudeService.delete(id);
        if (!deleted) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.noContent().build();
    }

    // --- RÄUME ALS SUB-RESSOURCE VON GEBÄUDEN ---

    @GET
    @Path("/{gid}/raeume")
    @Operation(summary = "Räume eines Gebäudes abrufen", description = "Ruft alle Räume ab, die zu einem bestimmten Gebäude gehören.")
    public List<RaumDto> getRaeumeByGebaeude(@PathParam("gid") Long gid) {
        return raumService.listByGebaeude(gid).stream().map(GebaeudeResource::mapRaumToDto).toList();
    }

    @POST
    @Path("/{gid}/raeume")
    @Operation(summary = "Neuen Raum in einem Gebäude erstellen", description = "Erstellt einen neuen Raum innerhalb eines Gebäudes.")
    public Response createRaum(@PathParam("gid") Long gid, @RequestBody(description = "Der zu erstellende Raum", required = true) Raum r) {
        try {
            Raum saved = raumService.save(r, gid);
            return Response.status(Response.Status.CREATED).entity(saved).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
        }
    }

    @PUT
    @Path("/{gid}/raeume/{rid}")
    @Operation(summary = "Raum aktualisieren", description = "Aktualisiert einen bestehenden Raum.")
    public Response updateRaum(@PathParam("gid") Long gid, @PathParam("rid") Long rid, @RequestBody(description = "Die aktualisierten Raumdaten", required = true) Raum r) {
        r.id = rid;
        try {
            Raum saved = raumService.save(r, gid);
            if (saved == null) {
                return Response.status(Response.Status.NOT_FOUND).build();
            }
            return Response.ok(saved).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
        }
    }

    @DELETE
    @Path("/{gid}/raeume/{rid}")
    @Operation(summary = "Raum löschen", description = "Löscht einen Raum.")
    public Response deleteRaum(@PathParam("gid") Long gid, @PathParam("rid") Long rid) {
        boolean deleted = raumService.delete(rid);
        if (!deleted) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.noContent().build();
    }

    @POST
    @Path("/{gid}/raeume/import")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Operation(summary = "Räume importieren", description = "Importiert Räume für ein Gebäude aus einer CSV-Datei.")
    public Response importRaeume(@PathParam("gid") Long gid, FileUploadDto data) {
        try {
            int count = raumService.importFromCsv(data.file.uploadedFile().toFile().toPath(), gid);
            return Response.ok("Import erfolgreich: " + count + " Räume angelegt.").build();
        } catch (Exception e) {
            return Response.status(Response.Status.BAD_REQUEST).entity("Fehler: " + e.getMessage()).build();
        }
    }

    // -------------------------------------------------------------------
    // Helper methods
    // -------------------------------------------------------------------

    public static GebaeudeSimpleDto mapToDto(Gebaeude gebaeude) {
        GebaeudeSimpleDto dto = new GebaeudeSimpleDto();
        dto.id = gebaeude.id;
        dto.version = gebaeude.version;

        dto.name = gebaeude.name;
        dto.strasse = gebaeude.strasse;
        dto.hausnummer = gebaeude.hausnummer;
        dto.ort = gebaeude.ort;
        dto.postleitzahl = gebaeude.postleitzahl;
        dto.typ = gebaeude.typ;

        dto.raeume = gebaeude.getRaeume().stream()
                .map(GebaeudeResource::mapRaumToDto)
                .toList();

        return dto;
    }

    public static RaumDto mapRaumToDto(Raum raum) {
        RaumDto dto = new RaumDto();

        dto.id = raum.id;
        dto.version = raum.version;
        dto.name = raum.name;
        dto.kapazitaet = raum.kapazitaet;
        dto.etage = raum.etage;

        dto.gebaeudeId = raum.gebaeude.id;

        return dto;
    }
}