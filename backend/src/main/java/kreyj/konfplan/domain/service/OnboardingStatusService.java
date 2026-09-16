package kreyj.konfplan.domain.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import kreyj.konfplan.adapter.in.web.dto.OnboardingStatusDto;
import kreyj.konfplan.persistence.GruppenkategorieWert;
import kreyj.konfplan.persistence.Nutzer;
import kreyj.konfplan.persistence.Teilnehmer;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Beantwortet "wer hat noch kein echtes eigenes Passwort vergeben" fuer den Organisator-Onboarding-
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
                gruppenwerteByKategorieVon(nutzer),
                keycloakUserProvisioningService.hatEchtesPasswort(nutzer)))
            .toList();
    }

    private Map<String, List<String>> gruppenwerteByKategorieVon(Nutzer nutzer) {
        if (!(nutzer instanceof Teilnehmer teilnehmer)) {
            return Map.of();
        }
        return teilnehmer.getGruppenwerte().stream()
            .collect(Collectors.groupingBy(w -> w.getGruppenkategorie().getName(),
                Collectors.mapping(GruppenkategorieWert::getWert, Collectors.toList())));
    }
}
