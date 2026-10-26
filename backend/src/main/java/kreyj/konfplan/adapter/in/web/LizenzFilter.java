package kreyj.konfplan.adapter.in.web;

import io.smallrye.common.annotation.Blocking;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import kreyj.konfplan.domain.lizenz.LizenzStatus;
import kreyj.konfplan.domain.lizenz.LizenzZustand;
import kreyj.konfplan.domain.service.LizenzStatusService;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.Map;

/**
 * Schreibsperre ohne gueltige Lizenz: Endpunkte mit {@link LizenzPflichtig} antworten mit 402,
 * solange die Instanz nicht LIZENZIERT ist. Lesen bleibt immer moeglich. Standardmaessig
 * ausgeschaltet (konfplan.lizenz.durchsetzung), damit ein Release mit der Pruefung keine
 * Bestandskunden aussperrt, bevor deren Lizenz ausgestellt ist.
 */
@Provider
@LizenzPflichtig
@Blocking
public class LizenzFilter implements ContainerRequestFilter {

    private final LizenzStatusService lizenzStatusService;
    private final boolean durchsetzung;

    public LizenzFilter(LizenzStatusService lizenzStatusService,
                        @ConfigProperty(name = "konfplan.lizenz.durchsetzung", defaultValue = "false") boolean durchsetzung) {
        this.lizenzStatusService = lizenzStatusService;
        this.durchsetzung = durchsetzung;
    }

    @Override
    public void filter(ContainerRequestContext ctx) {
        // Nicht angemeldete Anfragen weist die Sicherheitsschicht mit 401/403 ab - den Lizenzstatus nicht preisgeben.
        if (!durchsetzung || null == ctx.getSecurityContext() || null == ctx.getSecurityContext().getUserPrincipal()) {
            return;
        }
        LizenzStatus status = lizenzStatusService.statusGecacht();
        if (LizenzZustand.LIZENZIERT == status.zustand()) {
            return;
        }
        String grund = LizenzZustand.UNLIZENZIERT == status.zustand()
            ? "Für diese KonfPlan-Instanz ist keine Lizenz installiert."
            : "Die Lizenz dieser KonfPlan-Instanz ist nicht gültig (" + status.grund() + ").";
        ctx.abortWith(Response.status(402)
            .type(MediaType.APPLICATION_JSON)
            .entity(Map.of(
                "error", grund + " Diese Aktion ist gesperrt, bis eine gültige Lizenz installiert ist.",
                "code", "LIZENZ_ERFORDERLICH",
                "zustand", status.zustand().name()))
            .build());
    }
}
