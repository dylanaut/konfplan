package kreyj.vortragsmanager.resource;

import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;
import kreyj.vortragsmanager.dto.PriorityRequest;
import kreyj.vortragsmanager.entity.Priority;
import kreyj.vortragsmanager.service.PriorityService;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.util.List;

@Path("/api/participant/priorities")
@Authenticated
public class ParticipantPriorityResource {

    @Inject
    JsonWebToken jwt;

    @Inject
    PriorityService priorityService;

    @GET
    public List<Priority> getMyPriorities() {
        String email = jwt.getSubject(); // Die Email aus dem JWT Token
        return priorityService.getPrioritiesForUser(email);
    }

    @POST
    public Response updateMyPriorities(List<PriorityRequest> requests) {
        String email = jwt.getSubject();
        priorityService.savePriorities(email, requests);
        return Response.ok().build();
    }
}