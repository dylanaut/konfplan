package kreyj.konfplan.domain.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import kreyj.konfplan.adapter.in.web.dto.OnboardingStatusDto;
import kreyj.konfplan.persistence.Nutzer;
import kreyj.konfplan.persistence.Teilnehmer;
import kreyj.konfplan.util.StringHelper;

import java.util.List;

/**
 * Beantwortet "wer hat noch kein echtes eigenes Passwort vergeben" fuer den Admin-Onboarding-
 * Status-Tab - fragt dafuer {@link KeycloakUserProvisioningService} (einzige Stelle mit Zugriff
 * auf den Keycloak Admin REST Client) einmal je Nutzer ab.
 */
@ApplicationScoped
public class OnboardingStatusService {

    @Inject
    KeycloakUserProvisioningService keycloakUserProvisioningService;

    public List<OnboardingStatusDto> getOnboardingStatus() {
        return Nutzer.<Nutzer>listAll().stream()
            .map(nutzer -> new OnboardingStatusDto(
                nutzer.getLoginName(),
                nutzer.getRole(),
                nutzer.getEmail(),
                gruppenVon(nutzer),
                keycloakUserProvisioningService.hatEchtesPasswort(nutzer)))
            .toList();
    }

    private List<String> gruppenVon(Nutzer nutzer) {
        if (!(nutzer instanceof Teilnehmer teilnehmer)) {
            return List.of();
        }
        return teilnehmer.getGruppen().stream().sorted(StringHelper.NUM_OR_ALPHA_COMPARATOR).toList();
    }
}
