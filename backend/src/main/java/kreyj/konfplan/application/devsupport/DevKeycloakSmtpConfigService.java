package kreyj.konfplan.application.devsupport;

import io.quarkus.arc.profile.IfBuildProfile;
import io.quarkus.logging.Log;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.representations.idm.RealmRepresentation;

import java.util.Map;

/**
 * Der native "Passwort vergessen"-Link im Keycloak-Login-Dialog kann sonst keine Mail
 * verschicken, weil die dev-importierte Realm-JSON keinen smtpServer enthält und der
 * Mailpit-SMTP-Port bei jedem quarkus:dev-Start zufällig neu vergeben wird (kein statischer
 * Wert in der Realm-JSON möglich). Übernimmt daher zur Laufzeit einfach die von Quarkus für
 * den App-eigenen Mailer bereits korrekt aufgelösten quarkus.mailer.host/-port-Werte.
 */
@ApplicationScoped
@IfBuildProfile("dev")
public class DevKeycloakSmtpConfigService {

    @Inject
    Keycloak keycloak;

    @ConfigProperty(name = "quarkus.keycloak.admin-client.realm")
    String realm;

    @ConfigProperty(name = "quarkus.mailer.host")
    String mailerHost;

    @ConfigProperty(name = "quarkus.mailer.port")
    int mailerPort;


    void onStart(@Observes StartupEvent event) {
        try {
            RealmRepresentation realmRepresentation = keycloak.realm(realm).toRepresentation();
            realmRepresentation.setSmtpServer(Map.of(
                "host", mailerHost,
                "port", String.valueOf(mailerPort),
                "from", "konfplan@local"
            ));
            keycloak.realm(realm).update(realmRepresentation);
            Log.infof("Keycloak-Realm-SMTP für Dev-Modus auf %s:%d gesetzt (Mailpit).", mailerHost, mailerPort);
        } catch (Exception e) {
            Log.warn("Konnte Keycloak-Realm-SMTP nicht für den Dev-Modus konfigurieren.", e);
        }
    }
}
