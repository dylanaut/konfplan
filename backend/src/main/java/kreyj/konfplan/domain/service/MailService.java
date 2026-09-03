package kreyj.konfplan.domain.service;

import io.quarkus.mailer.Mail;
import io.quarkus.mailer.MailTemplate;
import io.quarkus.mailer.Mailer;
import io.quarkus.qute.Location;
import jakarta.enterprise.context.ApplicationScoped;
import kreyj.konfplan.persistence.Organisator;
import kreyj.konfplan.persistence.Nutzer;
import kreyj.konfplan.persistence.Referent;
import kreyj.konfplan.persistence.Veranstaltung;
import kreyj.konfplan.persistence.Vortrag;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.Objects;

@ApplicationScoped
public class MailService {
    private final String adminEmail;

    private final Mailer mailer;

    private final MailTemplate registrationConfirmationTemplate;

    private final MailTemplate deregistrationNotificationTemplate;

    private final boolean outboundEnabled;

    @SuppressWarnings("CdiInjectionPointsInspection")
    public MailService(
        @ConfigProperty(name = "app.mail.admin", defaultValue = "kontakt@konfplan.de")
        String adminEmail,
        Mailer mailer,
        @Location("email/registrationConfirmation")
        MailTemplate registrationConfirmationTemplate,
        @Location("email/deregistrationNotification")
        MailTemplate deregistrationNotificationTemplate,
        @ConfigProperty(name = "app.mail.outbound.enabled", defaultValue = "false")
        boolean outboundEnabled) {
        this.adminEmail = adminEmail;
        this.mailer = mailer;
        this.registrationConfirmationTemplate = registrationConfirmationTemplate;
        this.deregistrationNotificationTemplate = deregistrationNotificationTemplate;
        this.outboundEnabled = outboundEnabled;
    }


    public void sendVortragsRegistrierung(Veranstaltung v, Referent referent, Vortrag vortrag, boolean isAdded) {
        v.organisatoren().forEach(admin -> sendVortragsRegistrierung(admin, v, referent, vortrag, isAdded));
    }


    public void sendVortragsRegistrierung(Organisator organisator, Veranstaltung v, Referent referent, Vortrag vortrag, boolean isAdded) {
        if (organisator.getEmail() == null) {
            return;
        }

        String action = isAdded ? "angemeldet" : "abgemeldet / zurückgezogen";
        String subject = String.format("Vortrags-Update: %s für %s", action, v.getName());
        String body = String.format(
            "Hallo %s,\n\nder Referent %s %s hat den Vortrag '%s' für die Veranstaltung '%s' %s.\n\nDies ist eine automatische Benachrichtigung.",
            organisator.getLastName(),
            referent.getFirstName(), referent.getLastName(),
            vortrag.getTitel(),
            v.getName(),
            action
        );

        mailer.send(Mail.withText(organisator.getEmail(), subject, body)
            .setFrom(senderEmail(v)));
    }


    public void sendEinladungZuVeranstaltung(Nutzer nutzer, Veranstaltung v) {
        if (! outboundEnabled || nutzer.getEmail() == null) {
            return;
        }

        String subject = "Einladung zur Veranstaltung: " + v.getName();
        String body = String.format(
            """
                Hallo %s %s,

                Du wurdest zur Veranstaltung '%s' eingeladen.
                Datum: %s

                Wir freuen uns auf Deine Teilnahme!""",
            nutzer.getFirstName(), nutzer.getLastName(),
            v.getName(), v.getBeginntAm().toString()
        );

        mailer.send(Mail.withText(nutzer.getEmail(), subject, body)
            .setFrom(senderEmail(v)));
    }


    /**
     * Sendet eine Bestätigungs-E-Mail nach der Registrierung.
     *
     * @param nutzer Der neu registrierte Nutzer.
     */

    public void sendRegistrationConfirmation(Nutzer nutzer) {
        if (! outboundEnabled || nutzer.getEmail() == null) {
            return;
        }
        registrationConfirmationTemplate.to(nutzer.getEmail())
            .from(adminEmail)
            .subject("Willkommen bei KonfPlan!")
            .data("firstName", nutzer.getFirstName())
            .data("lastName", nutzer.getLastName())
            .data("email", nutzer.getEmail())
            .send()
            .subscribe().with(
                success -> System.out.println("Registration confirmation mail sent to " + nutzer.getEmail()),
                failure -> System.err.println("Failed to send registration confirmation mail: " + failure.getMessage())
            );
    }


    /**
     * Sendet eine Benachrichtigung, wenn ein Nutzer-Profil gelöscht oder abgemeldet wurde.
     *
     * @param nutzer Der gelöschte oder abgemeldete Nutzer.
     */

    public void sendUserDeletionNotification(Nutzer nutzer) {
        if (! outboundEnabled || nutzer.getEmail() == null) {
            return;
        }
        deregistrationNotificationTemplate.to(nutzer.getEmail())
            .from(adminEmail)
            .subject("Dein Profil beim KonfPlan wurde gelöscht/abgemeldet")
            .data("firstName", nutzer.getFirstName())
            .data("lastName", nutzer.getLastName())
            .data("email", nutzer.getEmail())
            .send()
            .subscribe().with(
                success -> System.out.println("Deregistration notification mail sent to " + nutzer.getEmail()),
                failure -> System.err.println("Failed to send deregistration notification mail: " + failure.getMessage())
            );
    }


    public void sendVerfuegbarkeitChangedNotification(Nutzer nutzer, Veranstaltung veranstaltung) {
        String subject = "Verfügbarkeit geändert für " + veranstaltung.getName();
        String body = String.format(
            "Hallo,\n\nder Teilnehmer %s %s hat seine Verfügbarkeit für die Veranstaltung '%s' geändert.\n\nDies ist eine automatische Benachrichtigung.",
            nutzer.getFirstName(), nutzer.getLastName(),
            veranstaltung.getName()
        );

        veranstaltung.organisatoren().stream()
            .filter(organisator -> organisator.getEmail() != null)
            .forEach(organisator
                -> mailer.send(Mail.withText(organisator.getEmail(), subject, body)
                .setFrom(senderEmail(veranstaltung))));
    }

    // -------------------------------------------------------------------
    // Helper methods
    // -------------------------------------------------------------------


    private String senderEmail(Veranstaltung v) {
        return v.organisatoren().stream()
            .map(Organisator::getEmail)
            .filter(Objects::nonNull)
            .findFirst()
            .orElse(adminEmail);
    }
}
