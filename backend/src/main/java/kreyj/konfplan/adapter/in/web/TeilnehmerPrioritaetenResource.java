package kreyj.konfplan.adapter.in.web;

import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import kreyj.konfplan.application.service.PrioritaetService;
import kreyj.konfplan.adapter.in.web.dto.PrioritaetRequest;
import kreyj.konfplan.persistence.Prioritaet;
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

    private final JsonWebToken jwt;

    private final PrioritaetService prioService;

    public TeilnehmerPrioritaetenResource(JsonWebToken jwt, PrioritaetService prioService) {
        this.jwt = jwt;
        this.prioService = prioService;
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Meine Prioritäten abrufen", description = "Ruft die vom Teilnehmer gesetzten Prioritäten für Wahlvorträge ab.")
    public List<Prioritaet> getPrioritaeten() {
        String email = JwtHelper.getUserPrincipalName(jwt); // Die Email aus dem JWT Token
        return prioService.getNutzerPrioritaeten(email);
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = "Prioritäten aktualisieren", description = "Aktualisiert die Prioritäten des Teilnehmers für die Wahlvorträge.")
    public Response updatePrioritaeten(@RequestBody(description = "Liste der Prioritäts-Anfragen") List<PrioritaetRequest> requests) {
        String email = JwtHelper.getUserPrincipalName(jwt);
        prioService.savePrioritaeten(email, requests);
        return Response.ok().build();
    }
}
