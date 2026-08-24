package kreyj.konfplan.application.devsupport;

import io.quarkus.arc.profile.IfBuildProfile;
import io.quarkus.logging.Log;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.keycloak.admin.client.Keycloak;

import java.util.Map;

/**
 * Ueberschreibt den Text des nativen "Passwort vergessen"-Links im Keycloak-Login-Dialog, damit
 * er auch die allererste Anmeldung (kein Start-Passwort mehr, siehe #237) abdeckt. Realm-weite
 * Text-Overrides ueber die Keycloak-Localization-API statt eines eigenen Themes, siehe
 * {@link kreyj.konfplan.application.prodsupport.ProdKeycloakRealmSyncService} fuer die
 * Prod-Variante (dort zusaetzlich idempotent bei jedem Start, da Realm-Import bestehende Realms
 * nicht anfasst).
 */
@ApplicationScoped
@IfBuildProfile("dev")
public class DevKeycloakLoginTextsService {

    @Inject
    Keycloak keycloak;

    @ConfigProperty(name = "quarkus.keycloak.admin-client.realm")
    String realm;


    void onStart(@Observes StartupEvent event) {
        try {
            keycloak.realm(realm).localization()
                .createOrUpdateRealmLocalizationTexts("de", Map.of("doForgotPassword", "Erstanmeldung / Passwort vergessen"));
            Log.info("Keycloak-Login-Text 'doForgotPassword' für Dev-Modus überschrieben.");
        } catch (Exception e) {
            Log.warn("Konnte Keycloak-Login-Texte nicht für den Dev-Modus überschreiben.", e);
        }
    }
}
