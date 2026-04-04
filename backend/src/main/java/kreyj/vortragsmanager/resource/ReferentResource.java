package kreyj.vortragsmanager.resource;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import kreyj.vortragsmanager.dto.RefProfilDto;
import kreyj.vortragsmanager.dto.RefVortragDto;
import kreyj.vortragsmanager.entity.Vortrag;
import kreyj.vortragsmanager.entity.User;
import kreyj.vortragsmanager.service.ReferentService;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.time.LocalDate;

@Path("/api/referent")
@RolesAllowed({"ADMIN","REFERENT"})
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ReferentResource {

    @Inject
    JsonWebToken jwt;

    @Inject
    ReferentService referentService;

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
    @Path("/vortrag")
    public Vortrag getVortrag() {
        return referentService.getVortrag(jwt.getSubject());
    }

    @PUT
    @Path("/vortrag")
    public Response updateVortrag(RefVortragDto dto) {
        referentService.updateVortrag(jwt.getSubject(), dto);
        return Response.ok().build();
    }

    @POST
    @Path("/verfuegbarkeit/tag")
    public Response setDayAvailability(@QueryParam("date") String dateStr, @QueryParam("available") boolean available) {
        LocalDate date = LocalDate.parse(dateStr);
        referentService.toggleEntireDay(jwt.getSubject(), date, available);
        return Response.ok().build();
    }
}
