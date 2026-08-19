package kreyj.konfplan.application.prodsupport;

import io.quarkus.arc.profile.IfBuildProfile;
import io.quarkus.logging.Log;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.AuthenticationManagementResource;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.representations.idm.RealmRepresentation;
import org.keycloak.representations.idm.RequiredActionProviderRepresentation;

import java.util.Map;

/**
 * Keycloaks "--import-realm" wendet {@code deploy/keycloak-realm.template.json} nur beim
 * erstmaligen Anlegen der Realm an - existiert die Realm schon (wie bei jedem bereits laufenden
 * Prod-Deployment), werden spaetere Aenderungen an der Template-JSON beim Container-Neustart
 * stillschweigend ignoriert (siehe #176: dadurch fehlte auf einem bestehenden Deployment der
 * smtpServer.from, Keycloaks natives "Passwort vergessen" schlug mit "Invalid sender address
 * 'null'" fehl). Bringt daher bei jedem Start die SMTP-Konfiguration und die
 * UPDATE_PASSWORD-RequiredAction idempotent auf den Zielzustand, unabhaengig davon, ob die Realm
 * neu angelegt oder schon vorhanden ist. Nutzt dieselben, bereits fuer den App-eigenen Mailer
 * aufgeloesten quarkus.mailer.*-Properties als einzige Quelle der Wahrheit fuer die SMTP-Werte.
 */
@ApplicationScoped
@IfBuildProfile("prod")
public class ProdKeycloakRealmSyncService {

    private static final String UPDATE_PASSWORD_ALIAS = "UPDATE_PASSWORD";

    @Inject
    Keycloak keycloak;

    @ConfigProperty(name = "quarkus.keycloak.admin-client.realm")
    String realm;

    @ConfigProperty(name = "quarkus.mailer.host")
    String mailerHost;

    @ConfigProperty(name = "quarkus.mailer.port")
    int mailerPort;

    @ConfigProperty(name = "quarkus.mailer.start-tls")
    String mailerStartTls;

    @ConfigProperty(name = "quarkus.mailer.username")
    String mailerUsername;

    @ConfigProperty(name = "quarkus.mailer.password")
    String mailerPassword;

    @ConfigProperty(name = "quarkus.mailer.from")
    String mailerFrom;


    void onStart(@Observes StartupEvent event) {
        RealmResource realmResource = keycloak.realm(realm);

        try {
            syncSmtpServer(realmResource);
        } catch (Exception e) {
            Log.warn("Konnte Keycloak-Realm-SMTP nicht synchronisieren.", e);
        }

        try {
            syncUpdatePasswordRequiredAction(realmResource);
        } catch (Exception e) {
            Log.warn("Konnte die UPDATE_PASSWORD-RequiredAction nicht synchronisieren.", e);
        }
    }


    private void syncSmtpServer(RealmResource realmResource) {
        RealmRepresentation realmRepresentation = realmResource.toRepresentation();
        realmRepresentation.setSmtpServer(Map.of(
            "host", mailerHost,
            "port", String.valueOf(mailerPort),
            "from", mailerFrom,
            "auth", String.valueOf(!mailerUsername.isBlank()),
            "starttls", String.valueOf(!"DISABLED".equalsIgnoreCase(mailerStartTls)),
            "user", mailerUsername,
            "password", mailerPassword
        ));
        realmResource.update(realmRepresentation);
        Log.infof("Keycloak-Realm-SMTP synchronisiert (%s:%d).", mailerHost, mailerPort);
    }


    private void syncUpdatePasswordRequiredAction(RealmResource realmResource) {
        AuthenticationManagementResource flows = realmResource.flows();

        RequiredActionProviderRepresentation requiredAction = findRequiredAction(flows);
        if (null == requiredAction) {
            flows.getUnregisteredRequiredActions().stream()
                .filter(a -> UPDATE_PASSWORD_ALIAS.equals(a.getProviderId()))
                .findFirst()
                .ifPresent(flows::registerRequiredAction);
            requiredAction = findRequiredAction(flows);
        }

        if (null == requiredAction) {
            Log.warn("UPDATE_PASSWORD-RequiredAction ist auf diesem Keycloak-Server nicht verfuegbar - konnte nicht registriert werden.");
            return;
        }

        requiredAction.setEnabled(true);
        requiredAction.setDefaultAction(true);
        flows.updateRequiredAction(UPDATE_PASSWORD_ALIAS, requiredAction);
        Log.info("Keycloak-RequiredAction UPDATE_PASSWORD als Default-Action synchronisiert.");
    }


    private RequiredActionProviderRepresentation findRequiredAction(AuthenticationManagementResource flows) {
        return flows.getRequiredActions().stream()
            .filter(a -> UPDATE_PASSWORD_ALIAS.equals(a.getAlias()))
            .findFirst()
            .orElse(null);
    }
}
