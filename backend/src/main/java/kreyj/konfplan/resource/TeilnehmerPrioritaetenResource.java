package kreyj.konfplan.resource;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import kreyj.konfplan.dto.PrioritaetRequest;
import kreyj.konfplan.persistence.Prioritaet;
import kreyj.konfplan.service.PrioritaetService;
import kreyj.konfplan.util.JwtHelper;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.List;

@Path("/api/teilnehmer/prios")
@RolesAllowed({"TEILNEHMER"})
@Tag(name = "Teilnehmer-Prioritäten", description = "Endpunkte für Teilnehmer zur Verwaltung ihrer Vortragsprioritäten")
public class TeilnehmerPrioritaetenResource {

    @Inject
    JsonWebToken jwt;

    @Inject
    PrioritaetService prioService;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Meine Prioritäten abrufen", description = "Ruft die vom Teilnehmer gesetzten Prioritäten für Wahlvorträge ab.")
    public List<Prioritaet> getPrioritaeten() {
        String email = JwtHelper.getUserPrincipalName(jwt); // Die Email aus dem JWT Token
        return prioService.getPrioritaetenForUser(email);
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = "Prioritäten aktualisieren", description = "Aktualisiert die Prioritäten des Teilnehmers für die Wahlvorträge.")
    public Response updatePrioritaeten(@RequestBody(description = "Liste der Prioritäts-Anfragen", required = true) List<PrioritaetRequest> requests) {
        String email = JwtHelper.getUserPrincipalName(jwt);
        prioService.savePrioritaeten(email, requests);
        return Response.ok().build();
    }
}