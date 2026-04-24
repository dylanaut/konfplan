package kreyj.vortragsmanager.resource;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import kreyj.vortragsmanager.dto.PrioritaetRequest;
import kreyj.vortragsmanager.dto.VeranstaltungDto;
import kreyj.vortragsmanager.dto.ZuweisungDto;
import kreyj.vortragsmanager.entity.Prioritaet;
import kreyj.vortragsmanager.entity.Teilnehmer;
import kreyj.vortragsmanager.entity.Vortrag;
import kreyj.vortragsmanager.service.PlanService;
import kreyj.vortragsmanager.service.PrioritaetService;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.util.List;
import java.util.stream.Collectors;

@Path("/api/teilnehmer")
@RolesAllowed({"TEILNEHMER", "ADMIN"})
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class TeilnehmerPlanResource {

    @Inject
    JsonWebToken jwt;

    @Inject
    PlanService planService;

    @Inject
    PrioritaetService prioritaetService;

    @GET
    @Path("/veranstaltungen")
    public List<VeranstaltungDto> getMeineVeranstaltungen() {
        String email = jwt.getSubject();
        Teilnehmer t = Teilnehmer.find("email", email).firstResult();
        if (t == null) return List.of();
        return t.veranstaltungen.stream()
                .map(VeranstaltungResource::mapToDto)
                .collect(Collectors.toList());
    }

    @GET
    @Path("/zuweisungen")
    public List<ZuweisungDto> getPlan(@QueryParam("vid") Long vid) {
        // Hinweis: Aktuell ignoriert PlanService vid und gibt alles zurück. 
        // Für Multi-Event-Support müsste PlanService angepasst werden.
        return planService.getPlanFuerTeilnehmer(jwt.getSubject());
    }

    @GET
    @Path("/prios")
    public List<Prioritaet> getPrios(@QueryParam("vid") Long vid) {
        return prioritaetService.getPrioritaetenForUser(jwt.getSubject());
    }

    @POST
    @Path("/prios")
    public Response savePriorities(List<PrioritaetRequest> requests) {
        prioritaetService.savePrioritaeten(jwt.getSubject(), requests);
        return Response.ok().build();
    }

    @GET
    @Path("/vortraege")
    public List<Vortrag> getVortraege(@QueryParam("vid") Long vid) {
        if (vid == null) return List.of();
        return Vortrag.find("veranstaltung.id", vid).list();
    }
}
