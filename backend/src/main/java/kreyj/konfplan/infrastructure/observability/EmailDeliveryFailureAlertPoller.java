package kreyj.konfplan.infrastructure.observability;

import io.quarkus.arc.profile.IfBuildProfile;
import io.quarkus.logging.Log;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.representations.idm.EventRepresentation;

import java.util.List;

/**
 * Ein fehlgeschlagener Mailversand (z.B. Brevo-IP-Sperre, siehe Deployment-Doku) passiert
 * innerhalb Keycloaks eigenem SMTP-Client beim Versenden des Passwort-Reset-/
 * Erstanmeldung-Links - taucht daher nur im Keycloak-Container-Log auf, nicht im App-Log, das
 * deploy/error-log-notifier.sh per ntfy ueberwacht. Pollt daher periodisch Keycloaks
 * SEND_RESET_PASSWORD_ERROR-Realm-Events (muessen dafuer aktiviert sein, siehe
 * {@link kreyj.konfplan.application.prodsupport.ProdKeycloakRealmSyncService} sowie die Realm-JSON
 * fuer Dev/Erstanlage) und spiegelt jeden neuen Fehler als ERROR-Log-Zeile ins App-Log, damit der
 * ntfy-Watcher ihn erfasst.
 */
@ApplicationScoped
@IfBuildProfile(anyOf = {"dev", "prod"})
public class EmailDeliveryFailureAlertPoller {

    private static final String SEND_RESET_PASSWORD_ERROR_EVENT_TYPE = "SEND_RESET_PASSWORD_ERROR";
    // Keycloaks org.keycloak.events.Errors - exakte Werte per Live-Test gegen Keycloak 26.7.1
    // verifiziert (ResetCredentialEmail-Authenticator): "invalid_email" wird gesetzt, wenn der
    // Nutzer schlicht keine (gueltige) E-Mail-Adresse hinterlegt hat - kein SMTP-/Brevo-Problem,
    // sondern ein Datenproblem, das nur ueber den Organisator-Dialog "Passwort zuruecksetzen" behebbar
    // ist. "email_send_failed" ist der tatsaechliche SMTP-Fehlerfall (z.B. Brevo-IP-Sperre).
    private static final String ERROR_INVALID_EMAIL = "invalid_email";
    private static final String ERROR_EMAIL_SEND_FAILED = "email_send_failed";

    @Inject
    Keycloak keycloak;

    @ConfigProperty(name = "quarkus.keycloak.admin-client.realm")
    String realm;

    private volatile long lastPolledAtMillis = System.currentTimeMillis();

    @Scheduled(every = "1m")
    void pollEmailDeliveryFailures() {
        long dateFrom = lastPolledAtMillis + 1;
        long dateTo = System.currentTimeMillis();

        List<EventRepresentation> events;
        try {
            // "asc" MUSS klein geschrieben sein, siehe FailedLoginMetricsPoller.
            events = keycloak.realm(realm)
                .getEvents(List.of(SEND_RESET_PASSWORD_ERROR_EVENT_TYPE), null, null, dateFrom, dateTo, null, null, null, "asc");
        } catch (Exception e) {
            Log.warn("Konnte fehlgeschlagenen Mailversand nicht von Keycloak abfragen.", e);
            return;
        }

        for (EventRepresentation event : events) {
            String username = null == event.getDetails() ? null : event.getDetails().get("username");
            Log.error(beschreibung(null == username ? "unbekannt" : username, event.getError()));
        }

        lastPolledAtMillis = dateTo;
    }

    private String beschreibung(String username, String fehlerCode) {
        if (ERROR_INVALID_EMAIL.equals(fehlerCode)) {
            return "Keycloak konnte keine Passwort-Reset-/Erstanmeldung-Mail an '" + username
                + "' senden: Der Nutzer hat keine (gültige) E-Mail-Adresse hinterlegt - kein SMTP-Problem, "
                + "der Selbst-Reset ist fuer diesen Nutzer ein Dead-End. Passwort ueber den Organisator-Dialog "
                + "'Passwort zuruecksetzen' vergeben.";
        }
        if (ERROR_EMAIL_SEND_FAILED.equals(fehlerCode)) {
            return "Keycloak konnte eine Passwort-Reset-/Erstanmeldung-Mail an '" + username
                + "' nicht versenden - vermutlich ein SMTP-Problem (z.B. Brevo-IP-Sperre).";
        }
        return "Keycloak konnte eine Passwort-Reset-/Erstanmeldung-Mail an '" + username
            + "' nicht versenden (Fehler: " + fehlerCode + ").";
    }
}
