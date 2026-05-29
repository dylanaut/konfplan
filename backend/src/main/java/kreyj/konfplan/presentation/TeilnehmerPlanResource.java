package kreyj.konfplan.presentation;

import jakarta.annotation.security.RolesAllowed;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import kreyj.konfplan.application.service.PlanService;
import kreyj.konfplan.application.service.PrioritaetService;
import kreyj.konfplan.persistence.Nutzer;
import kreyj.konfplan.persistence.NutzerVerfuegbarkeit;
import kreyj.konfplan.persistence.Prioritaet;
import kreyj.konfplan.persistence.Teilnehmer;
import kreyj.konfplan.persistence.Veranstaltung;
import kreyj.konfplan.presentation.dto.NutzerVerfuegbarkeitDto;
import kreyj.konfplan.presentation.dto.PrioritaetRequest;
import kreyj.konfplan.presentation.dto.VeranstaltungDto;
import kreyj.konfplan.presentation.dto.ZuweisungDto;
import kreyj.konfplan.util.JwtHelper;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.time.LocalDateTime;
import java.util.List;

import static jakarta.ws.rs.core.Response.Status.FORBIDDEN;
import static kreyj.konfplan.persistence.NutzerVerfuegbarkeitId.nvIdL;

@Path("/api/teilnehmer")
@RolesAllowed({"TEILNEHMER", "ADMIN"})
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Teilnehmer-Planung", description = "Endpunkte für Teilnehmer zur Ansicht ihres Plans und zur Verwaltung von Prioritäten")
public class TeilnehmerPlanResource {

    private final JsonWebToken jwt;

    private final PlanService planService;

    private final PrioritaetService prioritaetService;

    public TeilnehmerPlanResource(JsonWebToken jwt, PlanService planService, PrioritaetService prioritaetService) {
        this.jwt = jwt;
        this.planService = planService;
        this.prioritaetService = prioritaetService;
    }

    @GET
    @Path("/veranstaltungen")
    @Operation(summary = "Meine Veranstaltungen abrufen", description = "Ruft die Veranstaltungen ab, für die der Teilnehmer registriert ist.")
    public List<VeranstaltungDto> getMeineVeranstaltungen() {
        String email = JwtHelper.getUserPrincipalName(jwt);
        Teilnehmer t = Teilnehmer.find("email", email).firstResult();
        if (t == null) {
            return List.of();
        }
        return t.getVeranstaltungen().stream()
                .map(VeranstaltungResource::mapVeranstaltungToDto)
                .toList();
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
    public NutzerVerfuegbarkeitDto getVerfuegbarkeiten(@PathParam("vid") Long vid) {
        Nutzer nutzer = Nutzer.findByEmail(JwtHelper.getUserPrincipalName(jwt));
        if (!(nutzer instanceof Teilnehmer)) {
            throw new WebApplicationException("Nutzer ist kein Teilnehmer", FORBIDDEN.getStatusCode());
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
    @Operation(summary = "Verfügbarkeit aktualisieren", description = "Aktualisiert die persönliche Verfügbarkeit des Teilnehmers für einen bestimmten Slot.")
    public Response updateVerfuegbarkeit(@PathParam("vid") Long vid, @RequestBody(description = "Die Verfügbarkeitsdaten", required = true) NutzerVerfuegbarkeitDto dto) {
        Nutzer nutzer = Nutzer.findByEmail(JwtHelper.getUserPrincipalName(jwt));
        if (!(nutzer instanceof Teilnehmer) || !nutzer.getId().equals(dto.getNutzerId())) {
            return Response.status(FORBIDDEN).build();
        }

        Veranstaltung veranstaltung = Veranstaltung.findById(vid);
        if (veranstaltung == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        // Deadline Check
        if (veranstaltung.getDeadlineTeilnehmer() != null && veranstaltung.getDeadlineTeilnehmer().isBefore(LocalDateTime.now())) {
            return Response.status(FORBIDDEN)
                    .entity("Die Deadline für Teilnehmer ist bereits abgelaufen.").build();
        }

        NutzerVerfuegbarkeit v = NutzerVerfuegbarkeit.findById(nvIdL(nutzer.getId(), vid));
        if (v == null) {
            return Response.status(Response.Status.NOT_FOUND).entity("Verfügbarkeitseintrag nicht gefunden.").build();
        }

        v.getVerfuegbareSlotIds().clear();
        v.getVerfuegbareSlotIds().addAll(dto.getVerfuegbareSlotIds());
        v.persist();
        return Response.ok().build();
    }
}