package kreyj.vortragsmanager.resource;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import kreyj.vortragsmanager.dto.RefProfilDto;
import kreyj.vortragsmanager.dto.RefVortragDto;
import kreyj.vortragsmanager.dto.ZuweisungDto;
import kreyj.vortragsmanager.entity.Vortrag;
import kreyj.vortragsmanager.entity.User;
import kreyj.vortragsmanager.service.PlanService;
import kreyj.vortragsmanager.service.ReferentService;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.time.LocalDate;
import java.util.List;

@Path("/api/referenten")
@RolesAllowed({"ADMIN","REFERENT"})
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ReferentResource {

    @Inject
    JsonWebToken jwt;

    @Inject
    ReferentService referentService;

    @Inject
    PlanService planService;

    @GET
    @Path("/profile")
    public User getProfile() {
        return referentService.getProfile(jwt.getSubject());
    }

    @PUT
    @Path("/profile")
    public Response updateProfile(RefProfilDto dto) {
        referentService.updateProfile(jwt.getSubject(), dto);
        return Response.ok().build();
    }

    @GET
    @Path("/vortraege")
    public List<RefVortragDto> getMeineVortraege() {
        return referentService.getMeineVortraege(jwt.getSubject());
    }

    @POST
    @Path("/vortraege")
    public Response createVortrag(RefVortragDto dto) {
        try {
            RefVortragDto saved = referentService.createVortrag(jwt.getSubject(), dto);
            return Response.ok(saved).build();
        } catch (Exception e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(e.getMessage()).build();
        }
    }

    @PUT
    @Path("/vortraege/{vortragId}")
    public Response updateVortrag(@PathParam("vortragId") Long vortragId, RefVortragDto dto) {
        try {
            RefVortragDto updated = referentService.updateVortrag(jwt.getSubject(), vortragId, dto);
            if (updated == null) return Response.status(Response.Status.NOT_FOUND).build();
            return Response.ok(updated).build();
        } catch (Exception e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(e.getMessage()).build();
        }
    }

    @DELETE
    @Path("/vortraege/{vortragId}")
    public Response deleteVortrag(@PathParam("vortragId") Long vortragId) {
        boolean deleted = referentService.deleteVortrag(jwt.getSubject(), vortragId);
        if (!deleted) return Response.status(Response.Status.NOT_FOUND).build();
        return Response.noContent().build();
    }

    @GET
    @Path("/my-plan")
    public List<ZuweisungDto> getMyPlan() {
        return planService.getPlanFuerReferent(jwt.getSubject());
    }

    @POST
    @Path("/verfuegbarkeit/tag")
    public Response setDayAvailability(@QueryParam("date") String dateStr, @QueryParam("available") boolean available) {
        LocalDate date = LocalDate.parse(dateStr);
        referentService.toggleEntireDay(jwt.getSubject(), date, available);
        return Response.ok().build();
    }
}
