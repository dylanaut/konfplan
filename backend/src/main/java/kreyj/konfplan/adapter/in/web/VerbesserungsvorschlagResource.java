package kreyj.konfplan.adapter.in.web;

import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import kreyj.konfplan.adapter.in.web.dto.VerbesserungsvorschlagDto;
import kreyj.konfplan.domain.service.VerbesserungsvorschlagService;
import kreyj.konfplan.persistence.VorschlagStatus;
import kreyj.konfplan.util.JwtHelper;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.List;

@Path("/api/verbesserungsvorschlaege")
@RolesAllowed({"ADMIN", "REFERENT", "TEILNEHMER"})
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Verbesserungsvorschlaege", description = "Endpunkte für Verbesserungsvorschläge von Nutzern an die Anwendung")
public class VerbesserungsvorschlagResource {
    private final JsonWebToken jwt;
    private final VerbesserungsvorschlagService vorschlagService;

    public VerbesserungsvorschlagResource(JsonWebToken jwt, VerbesserungsvorschlagService vorschlagService) {
        this.jwt = jwt;
        this.vorschlagService = vorschlagService;
    }

    @POST
    @Operation(summary = "Verbesserungsvorschlag einreichen", description = "Legt einen neuen Verbesserungsvorschlag des angemeldeten Nutzers an.")
    public Response create(@RequestBody(description = "Titel und Beschreibung des Vorschlags") VerbesserungsvorschlagDto dto) {
        var vorschlag = vorschlagService.create(dto.titel, dto.beschreibung, JwtHelper.getUserPrincipalName(jwt));
        return Response.status(Response.Status.CREATED).entity(VerbesserungsvorschlagDto.from(vorschlag)).build();
    }

    @GET
    @RolesAllowed("ADMIN")
    @Operation(summary = "Alle Verbesserungsvorschläge abrufen", description = "Listet alle eingereichten Verbesserungsvorschläge, neueste zuerst.")
    public List<VerbesserungsvorschlagDto> getAll() {
        return vorschlagService.listAll().stream().map(VerbesserungsvorschlagDto::from).toList();
    }

    @PUT
    @Path("/{id}/status")
    @RolesAllowed("ADMIN")
    @Operation(summary = "Status eines Verbesserungsvorschlags ändern", description = "Markiert einen Vorschlag als offen oder erledigt.")
    public VerbesserungsvorschlagDto updateStatus(@PathParam("id") Long id, VorschlagStatus status) {
        return VerbesserungsvorschlagDto.from(vorschlagService.updateStatus(id, status));
    }

    @DELETE
    @Path("/{id}")
    @RolesAllowed("ADMIN")
    @Operation(summary = "Verbesserungsvorschlag löschen", description = "Löscht einen Verbesserungsvorschlag endgültig.")
    public Response delete(@PathParam("id") Long id) {
        vorschlagService.delete(id);
        return Response.noContent().build();
    }
}
