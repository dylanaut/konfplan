package kreyj.vortragsmanager.resource;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Response;
import kreyj.vortragsmanager.dto.SpeakerProfileDto;
import kreyj.vortragsmanager.dto.SpeakerTalkDto;
import kreyj.vortragsmanager.entity.Talk;
import kreyj.vortragsmanager.entity.User;
import kreyj.vortragsmanager.service.SpeakerService;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.time.LocalDate;

@Path("/api/speaker")
@RolesAllowed("SPEAKER")
public class SpeakerResource {

    @Inject
    JsonWebToken jwt;

    @Inject
    SpeakerService speakerService;

    @GET
    @Path("/profile")
    public User getProfile() {
        return User.findByEmail(jwt.getSubject());
    }

    @PUT
    @Path("/profile")
    public Response updateProfile(SpeakerProfileDto dto) {
        speakerService.updateProfile(jwt.getSubject(), dto);
        return Response.ok().build();
    }

    @GET
    @Path("/talk")
    public Talk getTalk() {
        User speaker = User.findByEmail(jwt.getSubject());
        return Talk.find("speaker", speaker).firstResult();
    }

    @PUT
    @Path("/talk")
    public Response updateTalk(SpeakerTalkDto dto) {
        speakerService.updateTalk(jwt.getSubject(), dto);
        return Response.ok().build();
    }

    @POST
    @Path("/availability/day")
    public Response setDayAvailability(@QueryParam("date") String dateStr, @QueryParam("available") boolean available) {
        LocalDate date = LocalDate.parse(dateStr);
        speakerService.toggleEntireDay(jwt.getSubject(), date, available);
        return Response.ok().build();
    }
}