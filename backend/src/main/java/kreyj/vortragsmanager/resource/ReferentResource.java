package kreyj.vortragsmanager.resource;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import kreyj.vortragsmanager.dto.*;
import kreyj.vortragsmanager.entity.*;
import kreyj.vortragsmanager.service.PlanService;
import kreyj.vortragsmanager.service.ReferentService;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

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

    public static VortragDto mapVortragToDto(Vortrag v) {
        VortragDto dto = new VortragDto();
        dto.id = v.id;
        dto.version = v.version;
        dto.titel = v.titel;
        dto.abstractText = v.inhalt;
        dto.veranstaltungId = v.veranstaltung.id;
        dto.veranstaltungName = v.veranstaltung.name;
        dto.referentId = v.referent.id;
        dto.referentName = v.referent.lastName + ", " + v.referent.firstName;
        dto.referentOrganisation = v.referent.organisation;

        if (v instanceof Wahlvortrag wahlvortrag) {
            dto.wiederholbar = wahlvortrag.wiederholbar;
            dto.maxWiederholungen = wahlvortrag.maxWiederholungen;
            dto.verfuegIds = wahlvortrag.wahlSlots.stream()
                    .map(s -> s.id)
                    .collect(Collectors.toList());
        } else if (v instanceof Pflichtvortrag pflichtvortrag) {
            dto.istPflicht = true;
            dto.pflichtgruppe = pflichtvortrag.pflichtgruppe;
            if (pflichtvortrag.pflichtslot != null) {
                dto.verfuegIds = List.of(pflichtvortrag.pflichtslot.id);
            }
        }

        return dto;
    }

    @GET
    @Path("/profile")
    public Nutzer getProfile() {
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
    public List<VortragDto> getReferentenVortraege() {
        return referentService.getReferentVortraege(jwt.getSubject());
    }

    @POST
    @Path("/vortraege")
    public Response createVortrag(VortragDto dto) {
        try {
            VortragDto saved = referentService.createVortrag(jwt.getSubject(), dto);
            return Response.ok(saved).build();
        } catch (Exception e) {
            return Response.status(Response.Status.FORBIDDEN)
                    .entity(e.getMessage()).build();
        }
    }

    @PUT
    @Path("/vortraege/{vortragId}")
    public Response updateVortrag(@PathParam("vortragId") Long vortragId, VortragDto dto) {
        try {
            VortragDto updated = referentService.updateVortrag(jwt.getSubject(), vortragId, dto);
            if (updated == null) return Response.status(Response.Status.NOT_FOUND).build();
            return Response.ok(updated).build();
        } catch (WebApplicationException e) {
            return e.getResponse();
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
    @Path("/plaene")
    public List<ZuweisungDto> getMyPlan() {
        return planService.getPlanFuerReferent(jwt.getSubject());
    }

    @GET
    @Path("/veranstaltungen")
    public List<ReferentVeranstaltungDto> getReferentVeranstaltungen() {
        return referentService.getReferentVeranstaltungen(jwt.getSubject());
    }

    @POST
    @Path("/veranstaltungen/{eventId}/vortraege/{talkId}/register")
    public Response registerTalkForEvent(@PathParam("eventId") Long eventId, @PathParam("talkId") Long talkId) {
        referentService.registerTalkForEvent(jwt.getSubject(), talkId, eventId);
        return Response.ok().build();
    }

    @DELETE
    @Path("/veranstaltungen/{eventId}/vortraege/{talkId}/deregister")
    public Response deregisterTalkFromEvent(@PathParam("eventId") Long eventId, @PathParam("talkId") Long talkId) {
        referentService.deregisterTalkFromEvent(jwt.getSubject(), talkId, eventId);
        return Response.ok().build();
    }

    @GET
    @Path("/veranstaltungen/{vid}/verfuegbarkeiten")
    public List<VerfuegbarkeitDto> getVerfuegbarkeiten(@PathParam("vid") Long vid) {
        Nutzer nutzer = Nutzer.findByEmail(jwt.getSubject());
        if (!(nutzer instanceof Referent)) throw new WebApplicationException("Kein Referent", 403);
        
        return Verfuegbarkeit.find("nutzer = ?1 and slot.veranstaltung.id = ?2", nutzer, vid).stream()
                .map(v -> {
                    Verfuegbarkeit vf = (Verfuegbarkeit) v;
                    return new VerfuegbarkeitDto(vf.nutzer.id, vf.slot.id, vf.isAvailable);
                })
                .collect(Collectors.toList());
    }

    @POST
    @Path("/veranstaltungen/{vid}/verfuegbarkeiten")
    @Transactional
    public Response updateVerfuegbarkeit(@PathParam("vid") Long vid, VerfuegbarkeitDto dto) {
        Nutzer nutzer = Nutzer.findByEmail(jwt.getSubject());
        if (!(nutzer instanceof Referent)) return Response.status(Response.Status.FORBIDDEN).build();
        
        Veranstaltung veranstaltung = Veranstaltung.findById(vid);
        if (veranstaltung == null) return Response.status(Response.Status.NOT_FOUND).build();

        // Deadline Check
        if (veranstaltung.deadlineReferenten != null && veranstaltung.deadlineReferenten.isBefore(LocalDateTime.now())) {
            return Response.status(Response.Status.FORBIDDEN)
                    .entity("Die Deadline für Referenten ist bereits abgelaufen.").build();
        }

        EventSlot slot = EventSlot.findById(dto.slotId);
        if (slot == null || !slot.veranstaltung.id.equals(vid)) return Response.status(Response.Status.BAD_REQUEST).build();

        Verfuegbarkeit v = Verfuegbarkeit.find("nutzer = ?1 and slot = ?2", nutzer, slot).firstResult();
        if (v == null) {
            v = new Verfuegbarkeit();
            v.nutzer = nutzer;
            v.slot = slot;
        }
        v.isAvailable = dto.isAvailable;
        v.persist();
        return Response.ok().build();
    }
}
