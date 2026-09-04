package kreyj.konfplan.adapter.in.web;

import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import kreyj.konfplan.adapter.in.web.dto.NachrichtDto;
import kreyj.konfplan.domain.service.NachrichtService;
import kreyj.konfplan.util.JwtHelper;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.List;

@Path("/api/nachrichten")
@RolesAllowed({"ORGANISATOR", "ADMINISTRATOR", "REFERENT", "TEILNEHMER"})
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Nachrichten", description = "In-App-Postfach (MessageBox) des angemeldeten Nutzers")
public class NachrichtResource {
    private final JsonWebToken jwt;
    private final NachrichtService nachrichtService;


    public NachrichtResource(JsonWebToken jwt, NachrichtService nachrichtService) {
        this.jwt = jwt;
        this.nachrichtService = nachrichtService;
    }


    @GET
    @Operation(summary = "Eigene Nachrichten abrufen", description = "Listet alle Nachrichten des angemeldeten Nutzers, neueste zuerst.")
    public List<NachrichtDto> getEigene() {
        return nachrichtService.getNachrichtenFuerNutzer(JwtHelper.getUserPrincipalName(jwt)).stream()
            .map(NachrichtDto::from)
            .toList();
    }


    @GET
    @Path("/ungelesen-anzahl")
    @Operation(summary = "Anzahl ungelesener eigener Nachrichten", description = "Für das Badge am Postfach-Symbol.")
    public long getUngeleseneAnzahl() {
        return nachrichtService.getUngeleseneAnzahl(JwtHelper.getUserPrincipalName(jwt));
    }


    @PUT
    @Path("/{id}/gelesen")
    @Operation(summary = "Nachricht als gelesen markieren")
    public Response markiereAlsGelesen(@PathParam("id") Long id) {
        nachrichtService.markiereAlsGelesen(JwtHelper.getUserPrincipalName(jwt), id);
        return Response.noContent().build();
    }
}
