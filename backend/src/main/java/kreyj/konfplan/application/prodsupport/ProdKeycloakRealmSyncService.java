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
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.RealmRepresentation;
import org.keycloak.representations.idm.RequiredActionProviderRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;

import java.util.List;
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
    private static final String LOGIN_TEXTS_LOCALE = "de";
    private static final int ACTION_TOKEN_LIFESPAN_SECONDS = 60 * 60 * 36;
    private static final long FAILED_LOGIN_EVENTS_EXPIRATION_SECONDS = 60L * 60 * 24 * 30;
    private static final String REALM_MANAGEMENT_CLIENT_ID = "realm-management";
    private static final String VIEW_EVENTS_ROLE = "view-events";

    @Inject
    Keycloak keycloak;

    @ConfigProperty(name = "quarkus.keycloak.admin-client.realm")
    String realm;

    @ConfigProperty(name = "quarkus.keycloak.admin-client.client-id")
    String adminCliClientId;

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

        try {
            syncLoginTexts(realmResource);
        } catch (Exception e) {
            Log.warn("Konnte die Keycloak-Login-Texte nicht synchronisieren.", e);
        }

        try {
            syncActionTokenLifespan(realmResource);
        } catch (Exception e) {
            Log.warn("Konnte die Gueltigkeitsdauer des Passwort-Reset-Links nicht synchronisieren.", e);
        }

        try {
            syncFailedLoginEventsConfig(realmResource);
        } catch (Exception e) {
            Log.warn("Konnte die Realm-Events-Konfiguration fuer fehlgeschlagene Anmeldungen nicht synchronisieren.", e);
        }
    }


    /**
     * Ueberschreibt den Text des nativen "Passwort vergessen"-Links, damit er auch die
     * allererste Anmeldung (kein Start-Passwort mehr, siehe #237) abdeckt.
     */
    private void syncLoginTexts(RealmResource realmResource) {
        realmResource.localization()
            .createOrUpdateRealmLocalizationTexts(LOGIN_TEXTS_LOCALE, Map.of("doForgotPassword", "Erstanmeldung / Passwort vergessen"));
        Log.info("Keycloak-Login-Text 'doForgotPassword' synchronisiert.");
    }


    /**
     * Begrenzt die Gueltigkeitsdauer nutzer-initiierter Action-Tokens (u.a. der
     * Passwort-Reset-Link) auf 36 Stunden statt Keycloaks Default von 5 Minuten. Keycloaks
     * Standard-Mailtext rendert die tatsaechlich konfigurierte Lebensdauer automatisch mit ein,
     * kein zusaetzlicher Theme-Eingriff noetig.
     */
    private void syncActionTokenLifespan(RealmResource realmResource) {
        RealmRepresentation realmRepresentation = realmResource.toRepresentation();
        realmRepresentation.setActionTokenGeneratedByUserLifespan(ACTION_TOKEN_LIFESPAN_SECONDS);
        realmResource.update(realmRepresentation);
        Log.infof("Gueltigkeitsdauer des Passwort-Reset-Links auf %d Sekunden synchronisiert.", ACTION_TOKEN_LIFESPAN_SECONDS);
    }


    /**
     * Aktiviert die Aufzeichnung von LOGIN_ERROR- und SEND_RESET_PASSWORD_ERROR-Realm-Events,
     * Basis fuer {@link kreyj.konfplan.infrastructure.observability.FailedLoginMetricsPoller} bzw.
     * {@link kreyj.konfplan.infrastructure.observability.EmailDeliveryFailureAlertPoller}. Bewusst
     * nur diese beiden Event-Typen statt aller Events, um Keycloaks Event-Speicher klein zu
     * halten; eventsExpiration sorgt zusaetzlich fuer automatisches Aufraeumen.
     */
    private void syncFailedLoginEventsConfig(RealmResource realmResource) {
        RealmRepresentation realmRepresentation = realmResource.toRepresentation();
        realmRepresentation.setEventsEnabled(true);
        realmRepresentation.setEnabledEventTypes(List.of("LOGIN_ERROR", "SEND_RESET_PASSWORD_ERROR"));
        realmRepresentation.setEventsExpiration(FAILED_LOGIN_EVENTS_EXPIRATION_SECONDS);
        realmResource.update(realmRepresentation);
        Log.info("Keycloak-Realm-Events fuer LOGIN_ERROR und SEND_RESET_PASSWORD_ERROR synchronisiert.");

        grantViewEventsToAdminCliServiceAccount(realmResource);
    }


    /**
     * Der Admin-CLI-Service-Account (siehe #237-Kontext: derselbe Account, mit dem auch Nutzer
     * provisioniert werden) braucht die Client-Rolle {@code view-events} auf
     * {@code realm-management}, um {@link kreyj.konfplan.infrastructure.observability.FailedLoginMetricsPoller}s
     * Events-Abfrage nutzen zu koennen - das Realm-Import-JSON gewaehrt sie nur bei Erstanlage,
     * bestehende Deployments brauchen dieselbe idempotente Nachruestung wie oben.
     */
    private void grantViewEventsToAdminCliServiceAccount(RealmResource realmResource) {
        ClientRepresentation adminCliClient = realmResource.clients().findByClientId(adminCliClientId).stream()
            .findFirst()
            .orElse(null);
        ClientRepresentation realmManagementClient = realmResource.clients().findByClientId(REALM_MANAGEMENT_CLIENT_ID).stream()
            .findFirst()
            .orElse(null);
        if (null == adminCliClient || null == realmManagementClient) {
            Log.warnf("Client '%s' oder '%s' nicht gefunden - konnte '%s' nicht zuweisen.", adminCliClientId, REALM_MANAGEMENT_CLIENT_ID, VIEW_EVENTS_ROLE);
            return;
        }

        UserRepresentation serviceAccountUser = realmResource.clients().get(adminCliClient.getId()).getServiceAccountUser();
        RoleRepresentation viewEventsRole = realmResource.clients().get(realmManagementClient.getId())
            .roles().get(VIEW_EVENTS_ROLE).toRepresentation();

        realmResource.users().get(serviceAccountUser.getId())
            .roles().clientLevel(realmManagementClient.getId())
            .add(List.of(viewEventsRole));
        Log.infof("Service-Account-Rolle '%s' (%s) zugewiesen.", VIEW_EVENTS_ROLE, REALM_MANAGEMENT_CLIENT_ID);
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
