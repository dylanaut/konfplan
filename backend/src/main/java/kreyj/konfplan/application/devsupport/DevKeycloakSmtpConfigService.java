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
 * Wert in der Realm-JSON möglich). Übernimmt daher zur Laufzeit den von Quarkus für den
 * App-eigenen Mailer bereits korrekt aufgelösten Mailpit-Port. Der Host dagegen NICHT von
 * quarkus.mailer.host - der zeigt auf "localhost" aus Sicht des nativ laufenden App-Prozesses,
 * aber Keycloak läuft selbst in einem eigenen Docker-Container, für den "localhost" der
 * Keycloak-Container selbst wäre, nicht der Host-Rechner mit dem gemappten Mailpit-Port.
 */
@ApplicationScoped
@IfBuildProfile("dev")
public class DevKeycloakSmtpConfigService {

    private static final String KEYCLOAK_CONTAINER_HOST_ALIAS = "host.docker.internal";

    @Inject
    Keycloak keycloak;

    @ConfigProperty(name = "quarkus.keycloak.admin-client.realm")
    String realm;

    @ConfigProperty(name = "quarkus.mailer.port")
    int mailerPort;


    void onStart(@Observes StartupEvent event) {
        try {
            RealmRepresentation realmRepresentation = keycloak.realm(realm).toRepresentation();
            realmRepresentation.setSmtpServer(Map.of(
                "host", KEYCLOAK_CONTAINER_HOST_ALIAS,
                "port", String.valueOf(mailerPort),
                "from", "konfplan@local"
            ));
            keycloak.realm(realm).update(realmRepresentation);
            Log.infof("Keycloak-Realm-SMTP für Dev-Modus auf %s:%d gesetzt (Mailpit).", KEYCLOAK_CONTAINER_HOST_ALIAS, mailerPort);
        } catch (Exception e) {
            Log.warn("Konnte Keycloak-Realm-SMTP nicht für den Dev-Modus konfigurieren.", e);
        }
    }
}
