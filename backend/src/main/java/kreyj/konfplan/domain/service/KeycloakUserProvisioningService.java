package kreyj.konfplan.domain.service;

import io.quarkus.runtime.LaunchMode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import kreyj.konfplan.domain.exception.KeycloakProvisioningException;
import kreyj.konfplan.persistence.Nutzer;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;

import java.util.List;

/**
 * Identitaet (Login, Passwort, Rollen-Zuweisung) liegt bei Keycloak - diese Klasse ist die
 * einzige Stelle, die mit dem Keycloak Admin REST Client spricht. Die lokale {@link Nutzer}-
 * Entitaet enthaelt nur noch Fachdaten plus {@code keycloakId} als Verknuepfung.
 */
@ApplicationScoped
public class KeycloakUserProvisioningService {
    private static final Logger LOG = Logger.getLogger(KeycloakUserProvisioningService.class);

    @Inject
    Keycloak keycloak;

    @ConfigProperty(name = "quarkus.keycloak.admin-client.realm")
    String realm;

    private final LaunchMode launchMode;


    public KeycloakUserProvisioningService(LaunchMode launchMode) {
        this.launchMode = launchMode;
    }


    /**
     * Legt den Keycloak-User zu einem gerade lokal angelegten {@link Nutzer} an, weist ihm die
     * zur Rolle passende Realm-Rolle zu und setzt {@code nutzer.keycloakId}. In Dev/Test bleibt
     * das Passwort dauerhaft gueltig (Komfort fuer wiederholte Testlaeufe); in Prod ist es
     * temporaer - Keycloak erzwingt beim ersten Login eine Aenderung.
     */
    public void createUser(Nutzer nutzer, String password) {
        UserRepresentation kcUser = new UserRepresentation();
        kcUser.setUsername(nutzer.getLoginName());
        kcUser.setEmail(nutzer.getEmail());
        kcUser.setEmailVerified(true);
        kcUser.setFirstName(nutzer.getFirstName());
        kcUser.setLastName(nutzer.getLastName());
        kcUser.setEnabled(nutzer.isActive());

        boolean temporary = !launchMode.isDevOrTest();
        CredentialRepresentation cred = new CredentialRepresentation();
        cred.setType(CredentialRepresentation.PASSWORD);
        cred.setValue(password);
        cred.setTemporary(temporary);
        kcUser.setCredentials(List.of(cred));
        if (temporary) {
            kcUser.setRequiredActions(List.of("UPDATE_PASSWORD"));
        }

        try (Response response = keycloak.realm(realm).users().create(kcUser)) {
            if (response.getStatus() != 201) {
                throw new KeycloakProvisioningException(
                    "Keycloak-User für '" + nutzer.getLoginName() + "' konnte nicht angelegt werden (Status " + response.getStatus() + ").");
            }
            String keycloakId = response.getLocation().getPath().replaceAll(".*/([^/]+)$", "$1");
            nutzer.setKeycloakId(keycloakId);
        }

        assignRealmRole(nutzer.getKeycloakId(), nutzer.getRole());
        LOG.info("Keycloak-User angelegt: " + nutzer.getLoginName() + " [" + nutzer.getRole() + "]");
    }


    /**
     * Synchronisiert Stammdaten (E-Mail, Name, aktiv/inaktiv) eines bereits provisionierten
     * Nutzers nach einer Admin-getriebenen Aenderung. Anders als bei Self-Service-Aenderungen
     * (die es nicht mehr gibt, siehe Keycloak Account-Console) ist hier keine Bestaetigung nötig.
     */
    public void updateUser(Nutzer nutzer) {
        if (null == nutzer.getKeycloakId()) {
            LOG.warn("updateUser ohne keycloakId für '" + nutzer.getLoginName() + "' - überspringe Keycloak-Sync.");
            return;
        }
        UserRepresentation kcUser = keycloak.realm(realm).users().get(nutzer.getKeycloakId()).toRepresentation();
        kcUser.setEmail(nutzer.getEmail());
        kcUser.setFirstName(nutzer.getFirstName());
        kcUser.setLastName(nutzer.getLastName());
        kcUser.setEnabled(nutzer.isActive());
        keycloak.realm(realm).users().get(nutzer.getKeycloakId()).update(kcUser);
    }


    public void resetPassword(Nutzer nutzer, String newPassword) {
        if (null == nutzer.getKeycloakId()) {
            throw new KeycloakProvisioningException("Nutzer '" + nutzer.getLoginName() + "' hat keinen Keycloak-Account.");
        }
        CredentialRepresentation cred = new CredentialRepresentation();
        cred.setType(CredentialRepresentation.PASSWORD);
        cred.setValue(newPassword);
        cred.setTemporary(false);
        keycloak.realm(realm).users().get(nutzer.getKeycloakId()).resetPassword(cred);
    }


    public void deleteUser(Nutzer nutzer) {
        if (null == nutzer.getKeycloakId()) {
            return;
        }
        keycloak.realm(realm).users().get(nutzer.getKeycloakId()).remove();
    }


    private void assignRealmRole(String keycloakId, String roleName) {
        RoleRepresentation role = keycloak.realm(realm).roles().get(roleName).toRepresentation();
        keycloak.realm(realm).users().get(keycloakId).roles().realmLevel().add(List.of(role));
    }
}
