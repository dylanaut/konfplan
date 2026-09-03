package kreyj.konfplan.adapter.in.web;

import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import kreyj.konfplan.adapter.in.web.dto.OnboardingStatusDto;
import kreyj.konfplan.domain.service.OnboardingStatusService;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.List;

@Path("/api/admin/onboarding-status")
@RolesAllowed("ADMIN")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Onboarding-Status", description = "Übersicht, welche Nutzer noch kein echtes eigenes Passwort vergeben haben")
public class OnboardingStatusResource {
    private final OnboardingStatusService onboardingStatusService;

    public OnboardingStatusResource(OnboardingStatusService onboardingStatusService) {
        this.onboardingStatusService = onboardingStatusService;
    }

    @GET
    @Operation(summary = "Onboarding-Status aller Nutzer abrufen", description = "Liefert je Nutzer LoginName, Rolle, E-Mail und ob bereits ein echtes (nicht temporäres/ausstehendes) Passwort gesetzt wurde.")
    public List<OnboardingStatusDto> get() {
        return onboardingStatusService.getOnboardingStatus();
    }
}
