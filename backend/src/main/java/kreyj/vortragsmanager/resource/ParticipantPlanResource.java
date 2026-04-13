package kreyj.vortragsmanager.resource;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import kreyj.vortragsmanager.dto.PrioritaetRequest;
import kreyj.vortragsmanager.dto.ZuweisungDto;
import kreyj.vortragsmanager.entity.Prioritaet;
import kreyj.vortragsmanager.service.PlanService;
import kreyj.vortragsmanager.service.PrioritaetService;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.util.List;

@Path("/api/participant")
@RolesAllowed({"TEILNEHMER", "ADMIN"})
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ParticipantPlanResource {

    @Inject
    JsonWebToken jwt;

    @Inject
    PlanService planService;

    @Inject
    PrioritaetService prioritaetService;

    @GET
    @Path("/my-plan")
    public List<ZuweisungDto> getMyPlan() {
        return planService.getPlanFuerTeilnehmer(jwt.getSubject());
    }

    @GET
    @Path("/priorities")
    public List<Prioritaet> getMyPriorities() {
        return prioritaetService.getPrioritaetenForUser(jwt.getSubject());
    }

    @POST
    @Path("/priorities")
    public Response savePriorities(List<PrioritaetRequest> requests) {
        prioritaetService.savePrioritaeten(jwt.getSubject(), requests);
        return Response.ok().build();
    }
}
