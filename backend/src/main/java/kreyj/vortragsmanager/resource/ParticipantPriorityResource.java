package kreyj.vortragsmanager.resource;

import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;
import kreyj.vortragsmanager.dto.PrioritaetRequest;
import kreyj.vortragsmanager.entity.Prioritaet;
import kreyj.vortragsmanager.service.PrioritaetService;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.util.List;

@Path("/api/participant/priorities")
@Authenticated
public class ParticipantPriorityResource {

    @Inject
    JsonWebToken jwt;

    @Inject
    PrioritaetService prioService;

    @GET
    public List<Prioritaet> getMyPriorities() {
        String email = jwt.getSubject(); // Die Email aus dem JWT Token
        return prioService.getPrioritaetenForUser(email);
    }

    @POST
    public Response updateMyPriorities(List<PrioritaetRequest> requests) {
        String email = jwt.getSubject();
        prioService.savePrioritaeten(email, requests);
        return Response.ok().build();
    }
}