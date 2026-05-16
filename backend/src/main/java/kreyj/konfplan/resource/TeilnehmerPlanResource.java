package kreyj.konfplan.resource;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import kreyj.konfplan.dto.PrioritaetRequest;
import kreyj.konfplan.dto.VeranstaltungDto;
import kreyj.konfplan.dto.VerfuegbarkeitDto;
import kreyj.konfplan.dto.ZuweisungDto;
import kreyj.konfplan.persistence.*;
import kreyj.konfplan.service.PlanService;
import kreyj.konfplan.service.PrioritaetService;
import kreyj.konfplan.util.JwtHelper;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import static jakarta.ws.rs.core.Response.Status.FORBIDDEN;

@Path("/api/teilnehmer")
@RolesAllowed({"TEILNEHMER", "ADMIN"})
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Teilnehmer-Planung", description = "Endpunkte für Teilnehmer zur Ansicht ihres Plans und zur Verwaltung von Prioritäten")
public class TeilnehmerPlanResource {

    @Inject
    JsonWebToken jwt;

    @Inject
    PlanService planService;

    @Inject
    PrioritaetService prioritaetService;

    @GET
    @Path("/veranstaltungen")
    @Operation(summary = "Meine Veranstaltungen abrufen", description = "Ruft die Veranstaltungen ab, für die der Teilnehmer registriert ist.")
    public List<VeranstaltungDto> getMeineVeranstaltungen() {
        String email = JwtHelper.getUserPrincipalName(jwt);
        Teilnehmer t = Teilnehmer.find("email", email).firstResult();
        if (t == null) return List.of();
        return t.veranstaltungen.stream()
                .map(VeranstaltungResource::mapVeranstaltungToDto)
                .collect(Collectors.toList());
    }

    @GET
    @Path("/zuweisungen")
    @Operation(summary = "Persönlichen Plan abrufen", description = "Ruft den persönlichen Vortragsplan (Zuweisungen) für eine Veranstaltung ab.")
    public List<ZuweisungDto> getPlan(@QueryParam("vid") Long vid) {
        // Hinweis: Aktuell ignoriert PlanService vid und gibt alles zurück. 
        // Für Multi-Event-Support müsste PlanService angepasst werden.
        return planService.getPlanFuerTeilnehmer(JwtHelper.getUserPrincipalName(jwt), vid);
    }

    @GET
    @Path("/prios")
    @Operation(summary = "Meine Prioritäten abrufen", description = "Ruft die vom Teilnehmer gesetzten Prioritäten für Wahlvorträge ab.")
    public List<Prioritaet> getPrios(@QueryParam("vid") Long vid) {
        return prioritaetService.getPrioritaetenForUser(JwtHelper.getUserPrincipalName(jwt));
    }

    @POST
    @Path("/prios")
    @Operation(summary = "Prioritäten speichern", description = "Speichert eine Liste von Prioritäten für den Teilnehmer.")
    public Response savePriorities(@RequestBody(description = "Liste der Prioritäts-Anfragen", required = true) List<PrioritaetRequest> requests) {
        prioritaetService.savePrioritaeten(JwtHelper.getUserPrincipalName(jwt), requests);
        return Response.ok().build();
    }

    @GET
    @Path("/veranstaltungen/{vid}/verfuegbarkeiten")
    @Operation(summary = "Meine Verfügbarkeiten abrufen", description = "Ruft die persönlichen Verfügbarkeiten des Teilnehmers für eine Veranstaltung ab.")
    public List<VerfuegbarkeitDto> getVerfuegbarkeiten(@PathParam("vid") Long vid) {
        Nutzer nutzer = Nutzer.findByEmail(JwtHelper.getUserPrincipalName(jwt));
        if (!(nutzer instanceof Teilnehmer)) throw new WebApplicationException("Nutzer ist kein Teilnehmer", FORBIDDEN.getStatusCode());

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
    @Operation(summary = "Verfügbarkeit aktualisieren", description = "Aktualisiert die persönliche Verfügbarkeit des Teilnehmers für einen bestimmten Slot.")
    public Response updateVerfuegbarkeit(@PathParam("vid") Long vid, @RequestBody(description = "Die Verfügbarkeitsdaten", required = true) VerfuegbarkeitDto dto) {
        Nutzer nutzer = Nutzer.findByEmail(JwtHelper.getUserPrincipalName(jwt));
        if (!(nutzer instanceof Teilnehmer)) return Response.status(FORBIDDEN).build();

        Veranstaltung veranstaltung = Veranstaltung.findById(vid);
        if (veranstaltung == null) return Response.status(Response.Status.NOT_FOUND).build();

        // Deadline Check
        if (veranstaltung.deadlineTeilnehmer != null && veranstaltung.deadlineTeilnehmer.isBefore(LocalDateTime.now())) {
            return Response.status(FORBIDDEN)
                    .entity("Die Deadline für Teilnehmer ist bereits abgelaufen.").build();
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