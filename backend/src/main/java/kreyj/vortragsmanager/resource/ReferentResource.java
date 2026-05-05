package kreyj.vortragsmanager.resource;

import io.quarkus.elytron.security.common.BcryptUtil;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import kreyj.vortragsmanager.dto.*;
import kreyj.vortragsmanager.entity.*;
import kreyj.vortragsmanager.service.MailService;
import kreyj.vortragsmanager.service.PlanService;
import kreyj.vortragsmanager.service.ReferentService;
import kreyj.vortragsmanager.util.JwtHelper;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Path("/api/referenten")
@RolesAllowed({"ADMIN", "REFERENT"})
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ReferentResource {
    @Context
    UriInfo uriInfo;

    @Inject
    JsonWebToken jwt;

    @Inject
    ReferentService referentService;

    @Inject
    PlanService planService;

    @Inject // MailService injizieren
    MailService mailService;

    @GET
    @Path("/profile")
    public ReferentProfileResponseDto getProfile() { // Changed return type
        Referent referent = referentService.getProfile(JwtHelper.getUserPrincipalName(jwt));
        if (referent == null) {
            throw new WebApplicationException("Referent not found", Response.Status.NOT_FOUND);
        }
        return mapReferentToProfileResponseDto(referent); // Use mapper
    }

    @PUT
    @Path("/profile")
    public Response updateProfile(RefProfilDto dto) {
        referentService.updateProfile(JwtHelper.getUserPrincipalName(jwt), dto);
        return Response.ok().build();
    }

    @POST
    @Path("/email-change-request")
    @Transactional
    public Response requestEmailChange(EmailChangeRequestDto requestDto) {
        Nutzer nutzer = Nutzer.findByEmail(JwtHelper.getUserPrincipalName(jwt));
        if (nutzer == null) {
            return Response.status(Response.Status.NOT_FOUND).entity("Nutzer nicht gefunden.").build();
        }

        // Passwort validieren
        if (!BcryptUtil.matches(requestDto.currentPassword, nutzer.passwordHash)) {
            return Response.status(Response.Status.UNAUTHORIZED).entity("Aktuelles Passwort ist falsch.").build();
        }

        // Prüfen, ob die neue E-Mail bereits existiert
        if (Nutzer.findByEmail(requestDto.newEmail) != null) {
            return Response.status(Response.Status.CONFLICT).entity("Die neue E-Mail-Adresse wird bereits verwendet.").build();
        }

        String oldEmail = nutzer.email;
        String token = UUID.randomUUID().toString();
        nutzer.newEmail = requestDto.newEmail;
        nutzer.emailChangeToken = token;
        nutzer.emailChangeTokenExpiry = LocalDateTime.now().plusHours(2); // Token 2 Stunden gültig
        nutzer.persist();

        // E-Mails senden
        mailService.sendEmailChangeNotificationOldAddress(nutzer, oldEmail, requestDto.newEmail);

        URI baseUri = uriInfo.getBaseUri();
        String confirmationLink = baseUri.toString() + "/api/referenten/email-change-confirm?token=" + token;
        mailService.sendEmailChangeConfirmationNewAddress(nutzer, requestDto.newEmail, confirmationLink);

        return Response.ok("Bestätigungs-E-Mail an neue Adresse gesendet. Bitte überprüfen Sie Ihr Postfach.").build();
    }

    @GET
    @Path("/email-change-confirm")
    @PermitAll // Dieser Endpunkt muss ohne Authentifizierung erreichbar sein
    @Transactional
    public Response confirmEmailChange(@QueryParam("token") String token) {
        Nutzer nutzer = Nutzer.find("emailChangeToken", token).firstResult();

        if (nutzer == null || nutzer.emailChangeTokenExpiry == null || nutzer.emailChangeTokenExpiry.isBefore(LocalDateTime.now())) {
            return Response.status(Response.Status.BAD_REQUEST).entity("Ungültiger oder abgelaufener Bestätigungslink.").build();
        }

        // E-Mail aktualisieren
        nutzer.email = nutzer.newEmail;
        // Temporäre Felder löschen
        nutzer.newEmail = null;
        nutzer.emailChangeToken = null;
        nutzer.emailChangeTokenExpiry = null;
        nutzer.persist();

        return Response.ok("Ihre E-Mail-Adresse wurde erfolgreich geändert.").build();
    }

    @GET
    @Path("/vortraege")
    public List<VortragDto> getReferentenVortraege() {
        return referentService.getReferentVortraege(JwtHelper.getUserPrincipalName(jwt));
    }

    @POST
    @Path("/vortraege")
    public Response createVortrag(VortragDto dto) {
        try {
            VortragDto saved = referentService.createVortrag(JwtHelper.getUserPrincipalName(jwt), dto);
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
            VortragDto updated = referentService.updateVortrag(JwtHelper.getUserPrincipalName(jwt), vortragId, dto);
            if (updated == null) {
                return Response.status(Response.Status.NOT_FOUND).build();
            }
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
        boolean deleted = referentService.deleteVortrag(JwtHelper.getUserPrincipalName(jwt), vortragId);
        if (!deleted) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.noContent().build();
    }

    @POST
    @Path("/veranstaltungen/{targetEventId}/vortraege/{sourceTalkId}/clone")
    public Response cloneTalkForEvent(@PathParam("targetEventId") Long targetEventId, @PathParam("sourceTalkId") Long sourceTalkId) {
        try {
            VortragDto clonedTalk = referentService.cloneTalkForEvent(JwtHelper.getUserPrincipalName(jwt), sourceTalkId, targetEventId);
            return Response.status(Response.Status.CREATED).entity(clonedTalk).build();
        } catch (WebApplicationException e) {
            return e.getResponse();
        } catch (Exception e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(e.getMessage()).build();
        }
    }

    @GET
    @Path("/plaene")
    public List<ZuweisungDto> getMyPlan() {
        return planService.getPlanFuerReferent(JwtHelper.getUserPrincipalName(jwt));
    }

    @GET
    @Path("/veranstaltungen")
    public List<ReferentVeranstaltungDto> getReferentVeranstaltungen() {
        return referentService.getReferentVeranstaltungen(JwtHelper.getUserPrincipalName(jwt));
    }

    @POST
    @Path("/veranstaltungen/{eventId}/vortraege/{talkId}/register")
    public Response registerTalkForEvent(@PathParam("eventId") Long eventId, @PathParam("talkId") Long talkId) {
        referentService.registerTalkForEvent(JwtHelper.getUserPrincipalName(jwt), talkId, eventId);
        return Response.ok().build();
    }

    @DELETE
    @Path("/veranstaltungen/{eventId}/vortraege/{talkId}/deregister")
    public Response deregisterTalkFromEvent(@PathParam("eventId") Long eventId, @PathParam("talkId") Long talkId) {
        referentService.deregisterTalkFromEvent(JwtHelper.getUserPrincipalName(jwt), talkId, eventId);
        return Response.ok().build();
    }

    @GET
    @Path("/veranstaltungen/{vid}/verfuegbarkeiten")
    public List<VerfuegbarkeitDto> getVerfuegbarkeiten(@PathParam("vid") Long vid) {
        Nutzer nutzer = Nutzer.findByEmail(JwtHelper.getUserPrincipalName(jwt));
        if (!(nutzer instanceof Referent)) {
            throw new WebApplicationException("Kein Referent", 403);
        }

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
        Nutzer nutzer = Nutzer.findByEmail(JwtHelper.getUserPrincipalName(jwt));
        if (!(nutzer instanceof Referent)) {
            return Response.status(Response.Status.FORBIDDEN).build();
        }

        Veranstaltung veranstaltung = Veranstaltung.findById(vid);
        if (veranstaltung == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        // Deadline Check
        if (veranstaltung.deadlineReferenten != null && veranstaltung.deadlineReferenten.isBefore(LocalDateTime.now())) {
            return Response.status(Response.Status.FORBIDDEN)
                    .entity("Die Deadline für Referenten ist bereits abgelaufen.").build();
        }

        EventSlot slot = EventSlot.findById(dto.slotId);
        if (slot == null || !slot.veranstaltung.id.equals(vid)) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }

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

    // -------------------------------------------------------------------
    // Helper methods
    // -------------------------------------------------------------------


    // New mapper method
    public static ReferentProfileResponseDto mapReferentToProfileResponseDto(Referent referent) {
        ReferentProfileResponseDto dto = new ReferentProfileResponseDto();
        dto.id = referent.id;
        dto.email = referent.email;
        dto.firstName = referent.firstName;
        dto.lastName = referent.lastName;
        dto.jobRole = referent.jobRole;
        dto.organisation = referent.organisation;
        dto.slogan = referent.slogan;
        dto.biography = referent.biography;
        dto.role = referent.role;
        return dto;
    }

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
}