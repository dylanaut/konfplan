package kreyj.konfplan.adapter.in.web;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import kreyj.konfplan.adapter.in.web.dto.SolverConfig;
import kreyj.konfplan.domain.service.PlanErstellungService;
import org.eclipse.microprofile.context.ManagedExecutor;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.logging.Logger;

import java.util.HashMap;
import java.util.Map;

@Path("/api/planungen")
@RolesAllowed("ADMIN")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Planung", description = "Endpunkte zur Steuerung der Planerstellung")
public class PlanungResource {
    private static final Logger LOG = Logger.getLogger(PlanungResource.class);

    @Inject
    PlanErstellungService planErstellungService;

    @SuppressWarnings("CdiInjectionPointsInspection")
    @Inject
    ManagedExecutor managedExecutor;

    @POST
    @Path("/{vid}")
    @Operation(summary = "Planerstellung starten", description = "Startet den MiniZinc-Optimierungsprozess für eine Veranstaltung.")
    public Response startPlanung(@PathParam("vid") Long vid, @RequestBody(description = "Die Konfiguration für den Solver") SolverConfig config, @Context SecurityContext securityContext) {
        String username = securityContext.getUserPrincipal().getName();

        // Synchrone Vorbedingungs-Prüfung: wirft bei Kollisionen eine CollisionsException,
        // die der BusinessExceptionMapper als HTTP 400 mit Fehlermeldung an die UI zurückgibt.
        planErstellungService.pruefeKollisionenOrThrow(vid, username);

        managedExecutor.execute(() -> {
            try {
                planErstellungService.erstellePlan(vid, config, username);
            } catch (Exception e) {
                LOG.error("Fehler bei der asynchronen Planerstellung:", e);
            }
        });
        return Response.accepted("Planerstellung wurde gestartet.").build();
    }

    @DELETE
    @Path("/")
    @Operation(summary = "Laufende Planerstellung abbrechen", description = "Bricht den aktuell laufenden MiniZinc-Prozess ab.")
    public Response cancelPlanung() {
        planErstellungService.cancel();
        return Response.ok("Abbruch-Anforderung gesendet.").build();
    }

    @GET
    @Path("/status")
    @Operation(summary = "Status der Planerstellung abrufen", description = "Gibt zurück, ob aktuell ein Planungsprozess läuft.")
    public Response getStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("isPlanning", planErstellungService.isPlanning());
        status.put("lastError", planErstellungService.getLastError());
        status.put("phase", planErstellungService.getPhase());
        return Response.ok(status).build();
    }

    @POST
    @Path("/{vid}/dzn")
    @Produces(MediaType.TEXT_PLAIN)
    @Operation(summary = "MiniZinc-Datendatei exportieren", description = "Erzeugt die .dzn-Datendatei für eine Veranstaltung zum Download, ohne den Solver zu starten.")
    public Response exportDzn(@PathParam("vid") Long vid, @RequestBody(description = "Die Konfiguration für den Solver") SolverConfig config, @Context SecurityContext securityContext) {
        String username = securityContext.getUserPrincipal().getName();
        String dznContent = planErstellungService.generiereDznVorschau(vid, config, username);
        return Response.ok(dznContent)
            .header("Content-Disposition", "attachment; filename=\"veranstaltung_" + vid + ".dzn\"")
            .build();
    }
}
