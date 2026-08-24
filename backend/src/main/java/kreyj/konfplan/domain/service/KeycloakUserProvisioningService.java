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
            if (response.getStatus() == 409) {
                nutzer.setKeycloakId(findExistingKeycloakId(nutzer));
            } else if (response.getStatus() != 201) {
                throw new KeycloakProvisioningException(
                    "Keycloak-User für '" + nutzer.getLoginName() + "' konnte nicht angelegt werden (Status "
                        + response.getStatus() + "): " + response.readEntity(String.class));
            } else {
                String keycloakId = response.getLocation().getPath().replaceAll(".*/([^/]+)$", "$1");
                nutzer.setKeycloakId(keycloakId);
            }
        }

        assignRealmRole(nutzer.getKeycloakId(), nutzer.getRole());
        LOG.info("Keycloak-User angelegt: " + nutzer.getLoginName() + " [" + nutzer.getRole() + "]");
    }


    /**
     * Behandelt Status 409 beim Anlegen: kann z.B. auftreten, wenn ein fruehrerer Importversuch
     * den Keycloak-User bereits erfolgreich angelegt hat, ein spaeterer Schritt derselben
     * Transaktion aber fehlschlug und die DB-Aenderungen zurueckgerollt wurden - Keycloak kennt
     * kein Rollback, der User bleibt dort bestehen ("Split-Brain" zwischen DB und Keycloak).
     * Statt den Import dauerhaft blockiert zu lassen, wird der bestehende Keycloak-User anhand
     * von Username oder E-Mail nachgeschlagen und wiederverwendet.
     */
    private String findExistingKeycloakId(Nutzer nutzer) {
        List<UserRepresentation> byUsername = keycloak.realm(realm).users()
            .searchByUsername(nutzer.getLoginName(), true);
        if (!byUsername.isEmpty()) {
            LOG.warn("Keycloak-User '" + nutzer.getLoginName() + "' existierte bereits (Status 409 beim Anlegen) "
                + "- verknüpfe mit bestehendem Keycloak-Account statt neu anzulegen.");
            return byUsername.get(0).getId();
        }

        List<UserRepresentation> byEmail = keycloak.realm(realm).users()
            .searchByEmail(nutzer.getEmail(), true);
        if (!byEmail.isEmpty()) {
            LOG.warn("Keycloak-User mit E-Mail '" + nutzer.getEmail() + "' existierte bereits (Status 409 beim Anlegen "
                + "von '" + nutzer.getLoginName() + "') - verknüpfe mit bestehendem Keycloak-Account statt neu anzulegen.");
            return byEmail.get(0).getId();
        }

        throw new KeycloakProvisioningException(
            "Keycloak-User für '" + nutzer.getLoginName() + "' konnte nicht angelegt werden (Status 409), "
                + "aber es wurde auch kein bestehender Keycloak-User per Username oder E-Mail gefunden.");
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


    /**
     * Setzt das Passwort eines Nutzers durch einen Admin zurueck. Wie bei {@link #createUser}
     * ist das neue Passwort in Prod nur temporaer gueltig - da der Admin es kennt, muss der
     * Nutzer es beim naechsten Login zwingend selbst aendern (in Dev/Test bleibt es dauerhaft
     * gueltig, Komfort fuer wiederholte Testlaeufe).
     */
    public void resetPassword(Nutzer nutzer, String newPassword) {
        if (null == nutzer.getKeycloakId()) {
            throw new KeycloakProvisioningException("Nutzer '" + nutzer.getLoginName() + "' hat keinen Keycloak-Account.");
        }
        boolean temporary = !launchMode.isDevOrTest();
        CredentialRepresentation cred = new CredentialRepresentation();
        cred.setType(CredentialRepresentation.PASSWORD);
        cred.setValue(newPassword);
        cred.setTemporary(temporary);

        var userResource = keycloak.realm(realm).users().get(nutzer.getKeycloakId());
        userResource.resetPassword(cred);

        if (temporary) {
            UserRepresentation kcUser = userResource.toRepresentation();
            kcUser.setRequiredActions(List.of("UPDATE_PASSWORD"));
            userResource.update(kcUser);
        }
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
