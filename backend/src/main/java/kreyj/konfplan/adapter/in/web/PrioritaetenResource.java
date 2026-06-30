package kreyj.konfplan.adapter.in.web;

import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import kreyj.konfplan.adapter.in.web.dto.VortragPrioDto;
import kreyj.konfplan.domain.service.PrioritaetService;
import kreyj.konfplan.persistence.Nutzer;
import kreyj.konfplan.persistence.Veranstaltung;
import kreyj.konfplan.util.JwtHelper;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.List;
import java.util.Map;

@Path("/api/prios")
@RolesAllowed({"TEILNEHMER", "ADMIN"})
@Tag(name = "Teilnehmer-Prioritäten", description = "Endpunkte für Teilnehmer zur Verwaltung ihrer Vortragsprioritäten")
public class PrioritaetenResource {

    private final JsonWebToken jwt;

    private final PrioritaetService prioService;


    public PrioritaetenResource(JsonWebToken jwt, PrioritaetService prioService) {
        this.jwt = jwt;
        this.prioService = prioService;
    }


    @GET
    @Path("/{vid}")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Meine Prioritäten abrufen", description = "Ruft die vom Teilnehmer gesetzten Prioritäten für Wahlvorträge ab.")
    public Response getPrioritaeten(@PathParam("vid") Long vid) {
        Veranstaltung veranstaltung = Veranstaltung.findById(vid);
        if (null == veranstaltung) {
            return Response.status(Response.Status.NOT_FOUND).entity("Veranstaltung nicht gefunden").build();
        }
        String email = JwtHelper.getUserPrincipalName(jwt);
        Nutzer nutzer = Nutzer.findByEmail(email);
        if (null == nutzer) {
            return Response.status(Response.Status.NOT_FOUND).entity("Nutzer nicht gefunden").build();
        }

        Map<Long, Integer> nutzerPrioritaeten = prioService.getVortragPrioritaeten(nutzer.getId(), vid);
        return Response.ok(nutzerPrioritaeten).build();
    }


    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = "Prioritäten aktualisieren", description = "Aktualisiert die Prioritäten des Teilnehmers für die Wahlvorträge.")
    public Response updatePrioritaeten(@RequestBody(description = "Liste der Prioritäts-Anfragen") List<VortragPrioDto> requests) {
        String email = JwtHelper.getUserPrincipalName(jwt);

        prioService.savePrioritaeten(email, requests);

        return Response.ok().build();
    }
}
