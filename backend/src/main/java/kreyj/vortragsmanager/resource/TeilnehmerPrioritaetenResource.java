package kreyj.vortragsmanager.resource;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import kreyj.vortragsmanager.dto.PrioritaetRequest;
import kreyj.vortragsmanager.entity.Prioritaet;
import kreyj.vortragsmanager.service.PrioritaetService;
import kreyj.vortragsmanager.util.JwtHelper;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.util.List;

@Path("/api/teilnehmer/prios")
@RolesAllowed({"TEILNEHMER"})
public class TeilnehmerPrioritaetenResource {

    @Inject
    JsonWebToken jwt;

    @Inject
    PrioritaetService prioService;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public List<Prioritaet> getPrioritaeten() {
        String email = JwtHelper.getUserPrincipalName(jwt); // Die Email aus dem JWT Token
        return prioService.getPrioritaetenForUser(email);
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Response updatePrioritaeten(List<PrioritaetRequest> requests) {
        String email = JwtHelper.getUserPrincipalName(jwt);
        prioService.savePrioritaeten(email, requests);
        return Response.ok().build();
    }
}