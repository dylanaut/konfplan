package kreyj.konfplan.adapter.in.web;

import io.quarkus.elytron.security.common.BcryptUtil;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.persistence.OptimisticLockException;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import kreyj.konfplan.adapter.in.web.dto.EmailChangeRequestDto;
import kreyj.konfplan.adapter.in.web.dto.NutzerDto;
import kreyj.konfplan.adapter.in.web.dto.NutzerVerfuegbarkeitDto;
import kreyj.konfplan.adapter.in.web.dto.ReferentVeranstaltungDto;
import kreyj.konfplan.adapter.in.web.dto.ReferentVortragDto;
import kreyj.konfplan.adapter.in.web.dto.VortragDto;
import kreyj.konfplan.application.port.in.AdminServiceInterface;
import kreyj.konfplan.application.port.in.ReferentServiceInterface;
import kreyj.konfplan.application.service.MailService;
import kreyj.konfplan.application.service.PlanService;
import kreyj.konfplan.application.service.VeranstaltungService;
import kreyj.konfplan.persistence.Nutzer;
import kreyj.konfplan.persistence.NutzerVerfuegbarkeit;
import kreyj.konfplan.persistence.Referent;
import kreyj.konfplan.persistence.Veranstaltung;
import kreyj.konfplan.util.JwtHelper;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static jakarta.ws.rs.core.Response.Status.FORBIDDEN;
import static kreyj.konfplan.persistence.NutzerVerfuegbarkeitId.nvIdL;

@Path("/api/referenten")
@RolesAllowed({"ADMIN", "REFERENT"})
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Referenten", description = "Endpunkte für Referenten zur Verwaltung ihres Profils und ihrer Vorträge")
public class ReferentResource {
    private final VeranstaltungService veranstaltungService;

    @Context
    UriInfo uriInfo;

    private final JsonWebToken jwt;

    private final ReferentServiceInterface referentService;
    private final PlanService planService;
    private final MailService mailService;
    private final AdminServiceInterface adminService;


    public ReferentResource(JsonWebToken jwt, ReferentServiceInterface referentService, PlanService planService, MailService mailService, VeranstaltungService veranstaltungService, AdminServiceInterface adminService) {
        this.jwt = jwt;
        this.referentService = referentService;
        this.planService = planService;
        this.mailService = mailService;
        this.veranstaltungService = veranstaltungService;
        this.adminService = adminService;
    }


    @GET
    @Path("/profile")
    @Operation(summary = "Referentenprofil abrufen", description = "Ruft das Profil des aktuell angemeldeten Referenten ab.")
    public Response getReferent() { // Changed return type
        Referent referent = referentService.findByEmail(JwtHelper.getUserPrincipalName(jwt));
        if (referent == null) {
            throw new WebApplicationException("Referent not found", Response.Status.NOT_FOUND);
        }
        return Response.ok(adminService.mapNutzerToDto(referent)).build();
    }


    @PUT
    @Path("/profile")
    @Operation(summary = "Referent aktualisieren", description = "Aktualisiert das Profil des aktuell angemeldeten Referenten.")
    public Response updateProfile(@RequestBody(description = "Die aktualisierten Profildaten") NutzerDto dto) {
        try {
            referentService.updateProfile(JwtHelper.getUserPrincipalName(jwt), dto);
        } catch (OptimisticLockException e) {
            return Response.status(Response.Status.CONFLICT).entity(e.getMessage()).build();
        }
        return Response.ok().build();
    }


    @POST
    @Path("/email-change-request")
    @Transactional
    @Operation(summary = "E-Mail-Änderung anfordern", description = "Fordert eine Änderung der E-Mail-Adresse an und sendet Bestätigungs-E-Mails.")
    public Response requestEmailChange(@RequestBody(description = "Anfrage zur E-Mail-Änderung") EmailChangeRequestDto requestDto) {
        Nutzer nutzer = Nutzer.findByEmail(JwtHelper.getUserPrincipalName(jwt));
        if (nutzer == null) {
            return Response.status(Response.Status.NOT_FOUND).entity("Nutzer nicht gefunden.").build();
        }

        // Passwort validieren
        if (!BcryptUtil.matches(requestDto.currentPassword, nutzer.getPasswordHash())) {
            return Response.status(Response.Status.UNAUTHORIZED).entity("Aktuelles Passwort ist falsch.").build();
        }

        // Prüfen, ob die neue E-Mail bereits existiert
        if (Nutzer.findByEmail(requestDto.newEmail) != null) {
            return Response.status(Response.Status.CONFLICT).entity("Die neue E-Mail-Adresse wird bereits verwendet.").build();
        }

        String oldEmail = nutzer.getEmail();
        String token = UUID.randomUUID().toString();
        nutzer.setNewEmail(requestDto.newEmail);
        nutzer.setEmailChangeToken(token);
        nutzer.setEmailChangeTokenExpiry(LocalDateTime.now().plusHours(2)); // Token 2 Stunden gültig
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
    @Operation(summary = "E-Mail-Änderung bestätigen", description = "Bestätigt die Änderung der E-Mail-Adresse mit einem Token.")
    public Response confirmEmailChange(@QueryParam("token") String token) {
        Nutzer nutzer = Nutzer.find("emailChangeToken", token).firstResult();

        if (nutzer == null || nutzer.getEmailChangeTokenExpiry() == null || nutzer.getEmailChangeTokenExpiry().isBefore(LocalDateTime.now())) {
            return Response.status(Response.Status.BAD_REQUEST).entity("Ungültiger oder abgelaufener Bestätigungslink.").build();
        }

        // E-Mail aktualisieren
        nutzer.setEmail(nutzer.getNewEmail());
        // Temporäre Felder löschen
        nutzer.setNewEmail(null);
        nutzer.setEmailChangeToken(null);
        nutzer.setEmailChangeTokenExpiry(null);
        nutzer.persist();

        return Response.ok("Ihre E-Mail-Adresse wurde erfolgreich geändert.").build();
    }


    @GET
    @Path("/vortraege")
    @Operation(summary = "Vorträge des Referenten abrufen", description = "Ruft alle Vorträge ab, die dem aktuell angemeldeten Referenten zugeordnet sind.")
    public List<VortragDto> getReferentenVortraege() {
        return referentService.getReferentVortraege(JwtHelper.getUserPrincipalName(jwt));
    }


    @POST
    @Path("/vortraege")
    @Operation(summary = "Neuen Vortrag erstellen", description = "Erstellt einen neuen Vortrag für den aktuell angemeldeten Referenten.")
    public Response createVortrag(@RequestBody(description = "Die Daten des neuen Vortrags") VortragDto dto) {
        try {
            VortragDto saved = referentService.createVortrag(JwtHelper.getUserPrincipalName(jwt), dto);
            return Response.ok(saved).build();
        } catch (Exception e) {
            return Response.status(FORBIDDEN)
                .entity(e.getMessage()).build();
        }
    }


    @PUT
    @Path("/vortraege/{vortragId}")
    @Operation(summary = "Vortrag aktualisieren", description = "Aktualisiert einen bestehenden Vortrag des Referenten.")
    public Response updateVortrag(@PathParam("vortragId") Long vortragId, @RequestBody(description = "Die aktualisierten Vortragsdaten") VortragDto dto) {
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
    @Operation(summary = "Vortrag löschen", description = "Löscht einen Vortrag des Referenten.")
    public Response deleteVortrag(@PathParam("vortragId") Long vortragId) {
        boolean deleted = referentService.deleteVortrag(JwtHelper.getUserPrincipalName(jwt), vortragId);
        if (!deleted) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.noContent().build();
    }


    @POST
    @Path("/veranstaltungen/{targetEventId}/vortraege/{sourceVortragId}/clone")
    @Operation(summary = "Vortrag für eine andere Veranstaltung klonen", description = "Klont einen bestehenden Vortrag für eine neue Veranstaltung.")
    public Response cloneTalkForEvent(@PathParam("targetEventId") Long targetEventId, @PathParam("sourceVortragId") Long sourceVortragId) {
        try {
            VortragDto clonedTalk = referentService.uebernimmVortragInVeranstaltung(JwtHelper.getUserPrincipalName(jwt), sourceVortragId, targetEventId);
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
    @Operation(summary = "Persönlichen Plan abrufen", description = "Ruft den persönlichen Vortragsplan des Referenten für eine Veranstaltung ab.")
    public Response getMyPlan(@QueryParam("vid") Long vid) {
        Veranstaltung veranstaltung = Veranstaltung.findById(vid);
        if (null == veranstaltung) {
            return Response.status(Response.Status.NOT_FOUND).entity("Veranstaltung nicht gefunden.").build();
        }
        Referent referent = referentService.findByEmail(JwtHelper.getUserPrincipalName(jwt));
        if (null == referent) {
            return Response.status(Response.Status.NOT_FOUND).entity("Referent nicht gefunden.").build();
        }
        List<ReferentVortragDto> planFuerReferent = planService.getPlanFuerReferent(referent, veranstaltung);
        return Response.ok(planFuerReferent).build();
    }


    @GET
    @Path("/veranstaltungen")
    @Operation(summary = "Veranstaltungen des Referenten abrufen", description = "Ruft alle Veranstaltungen ab, bei denen der Referent registriert ist.")
    public Response getReferentVeranstaltungen() {
        Referent referent =
            referentService.findByEmail(JwtHelper.getUserPrincipalName(jwt));
        if (referent == null) {
            return Response.status(Response.Status.NOT_FOUND).entity("Referent nicht gefunden.").build();
        }
        List<ReferentVeranstaltungDto> referentVeranstaltungen = referentService.getReferentVeranstaltungen(referent);
        return Response.ok().entity(referentVeranstaltungen).build();
    }


    @POST
    @Path("/veranstaltungen/{eventId}/vortraege/{vortragId}/register")
    @Operation(summary = "Vortrag für Veranstaltung registrieren", description = "Registriert einen Vortrag des Referenten für eine Veranstaltung.")
    public Response meldeVortragFuerVeranstaltungAn(@PathParam("eventId") Long eventId, @PathParam("vortragId") Long vortragId) {
        referentService.meldeVortragFuerVeranstaltungAn(JwtHelper.getUserPrincipalName(jwt), vortragId, eventId);
        return Response.ok().build();
    }


    @DELETE
    @Path("/veranstaltungen/{eventId}/vortraege/{vortragId}/deregister")
    @Operation(summary = "Vortrag von Veranstaltung deregistrieren", description = "Entfernt die Registrierung eines Vortrags von einer Veranstaltung.")
    public Response meldeVortragFuerVeranstaltungAb(@PathParam("eventId") Long eventId, @PathParam("vortragId") Long vortragId) {
        referentService.meldeVortragFuerVeranstaltungAb(JwtHelper.getUserPrincipalName(jwt), vortragId, eventId);
        return Response.ok().build();
    }


    @GET
    @Path("/veranstaltungen/{vid}/verfuegbarkeiten")
    @Operation(summary = "Verfügbarkeiten für eine Veranstaltung abrufen", description = "Ruft die persönlichen Verfügbarkeiten des Referenten für eine Veranstaltung ab.")
    public NutzerVerfuegbarkeitDto getVerfuegbarkeiten(@PathParam("vid") Long vid) {
        Nutzer nutzer = Nutzer.findByEmail(JwtHelper.getUserPrincipalName(jwt));
        if (!(nutzer instanceof Referent)) {
            throw new WebApplicationException("Nutzer ist kein Referent", FORBIDDEN.getStatusCode());
        }

        NutzerVerfuegbarkeit nv = NutzerVerfuegbarkeit.findById(nvIdL(nutzer.getId(), vid));

        if (null == nv) {
            throw new WebApplicationException("Keine Verfügbarkeit für diesen Nutzer und diese Veranstaltung gefunden.",
                Response.Status.NOT_FOUND);
        } else {
            return new NutzerVerfuegbarkeitDto(nv);
        }
    }


    @POST
    @Path("/veranstaltungen/{vid}/verfuegbarkeiten")
    @Transactional
    @Operation(summary = "Verfügbarkeit für einen Slot aktualisieren", description = "Aktualisiert die persönliche Verfügbarkeit des Referenten für einen bestimmten Slot.")
    public Response updateVerfuegbarkeit(@PathParam("vid") Long vid, @RequestBody(description = "Die Verfügbarkeitsdaten") NutzerVerfuegbarkeitDto dto) {
        Nutzer nutzer = Nutzer.findByEmail(JwtHelper.getUserPrincipalName(jwt));
        if (!(nutzer instanceof Referent) || !nutzer.getId().equals(dto.nutzerId)) {
            return Response.status(FORBIDDEN).build();
        }

        Veranstaltung veranstaltung = Veranstaltung.findById(vid);
        if (veranstaltung == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        // Deadline Check
        if (veranstaltung.getDeadlineReferenten() != null && veranstaltung.getDeadlineReferenten().isBefore(LocalDateTime.now())) {
            return Response.status(FORBIDDEN)
                .entity("Die Deadline für Referenten ist bereits abgelaufen.").build();
        }

        NutzerVerfuegbarkeit nv = NutzerVerfuegbarkeit.findById(nvIdL(nutzer.getId(), vid));
        if (nv == null) {
            return Response.status(Response.Status.NOT_FOUND).entity("NutzerVerfuegbarkeit nicht gefunden.").build();
        }

        nv.getVerfuegbareSlotIds().clear();
        nv.getVerfuegbareSlotIds().addAll(dto.verfuegbareSlotIds);
        nv.persist();

        return Response.ok().build();
    }
}
