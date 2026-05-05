package kreyj.vortragsmanager.service;

import io.quarkus.mailer.Mail;
import io.quarkus.mailer.Mailer;
import io.quarkus.qute.Location;
import io.quarkus.mailer.MailTemplate; // Import hinzufügen
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import kreyj.vortragsmanager.entity.*;

@ApplicationScoped
public class MailService {

    @Inject
    Mailer mailer;

    @Inject
    @Location("registrationConfirmation")
    MailTemplate registrationConfirmationTemplate;

    @Inject
    @Location("deregistrationNotification")
    MailTemplate deregistrationNotificationTemplate;

    @Inject
    @Location("emailChangeNotificationOldAddress")
    MailTemplate emailChangeNotificationOldAddressTemplate;

    @Inject
    @Location("emailChangeConfirmationNewAddress")
    MailTemplate emailChangeConfirmationNewAddressTemplate;

    public void sendTalkRegistrationNotification(Veranstaltung v, Referent referent, Vortrag talk, boolean isAdded) {
        v.organisatoren().forEach(admin -> sendTalkRegistrationNotification(admin, v, referent, talk, isAdded));
    }

    public void sendTalkRegistrationNotification(Admin organisator, Veranstaltung v, Referent referent, Vortrag talk, boolean isAdded) {
        String action = isAdded ? "angemeldet" : "abgemeldet / zurückgezogen";
        String subject = String.format("Vortrags-Update: %s für %s", action, v.name);
        String body = String.format(
                "Hallo %s,\n\nder Referent %s %s hat den Vortrag '%s' für die Veranstaltung '%s' %s.\n\nDies ist eine automatische Benachrichtigung.",
                organisator.lastName,
                referent.firstName, referent.lastName,
                talk.titel,
                v.name,
                action
        );

        mailer.send(Mail.withText(organisator.email, subject, body));
    }

    public void sendEventInvitation(Nutzer nutzer, Veranstaltung event) {
        if (nutzer.email == null) return;

        String subject = "Einladung zur Veranstaltung: " + event.name;
        String body = String.format(
                "Hallo %s %s,\n\nDu wurdest zur Veranstaltung '%s' eingeladen.\n" +
                        "Datum: %s\n\nWir freuen uns auf Deine Teilnahme!",
                nutzer.firstName, nutzer.lastName,
                event.name,
                event.beginntAm.toString()
        );

        mailer.send(Mail.withText(nutzer.email, subject, body));
    }

    /**
     * Sendet eine Bestätigungs-E-Mail nach der Registrierung.
     * @param nutzer Der neu registrierte Nutzer.
     */
    public void sendRegistrationConfirmation(Nutzer nutzer) {
        if (nutzer.email == null) return;
        registrationConfirmationTemplate.to(nutzer.email)
                .subject("Willkommen beim Vortragsmanager!")
                .data("firstName", nutzer.firstName)
                .data("lastName", nutzer.lastName)
                .data("email", nutzer.email)
                .send()
                .subscribe().with(
                        success -> System.out.println("Registration confirmation mail sent to " + nutzer.email),
                        failure -> System.err.println("Failed to send registration confirmation mail: " + failure.getMessage())
                );
    }

    /**
     * Sendet eine Benachrichtigung, wenn ein Nutzer-Account gelöscht oder abgemeldet wurde.
     * @param nutzer Der gelöschte oder abgemeldete Nutzer.
     */
    public void sendUserDeletionNotification(Nutzer nutzer) {
        if (nutzer.email == null) return;
        deregistrationNotificationTemplate.to(nutzer.email)
                .subject("Dein Account beim Vortragsmanager wurde gelöscht/abgemeldet")
                .data("firstName", nutzer.firstName)
                .data("lastName", nutzer.lastName)
                .data("email", nutzer.email)
                .send()
                .subscribe().with(
                        success -> System.out.println("Deregistration notification mail sent to " + nutzer.email),
                        failure -> System.err.println("Failed to send deregistration notification mail: " + failure.getMessage())
                );
    }

    /**
     * Sendet eine Benachrichtigung an die alte E-Mail-Adresse, dass diese geändert wurde.
     * @param nutzer Der Nutzer, dessen E-Mail geändert wurde.
     * @param oldEmail Die alte E-Mail-Adresse.
     * @param newEmail Die neue E-Mail-Adresse.
     */
    public void sendEmailChangeNotificationOldAddress(Nutzer nutzer, String oldEmail, String newEmail) {
        if (oldEmail == null) return;
        emailChangeNotificationOldAddressTemplate.to(oldEmail)
                .subject("Wichtige Information: Deine E-Mail-Adresse wurde geändert")
                .data("firstName", nutzer.firstName)
                .data("lastName", nutzer.lastName)
                .data("oldEmail", oldEmail)
                .data("newEmail", newEmail)
                .send()
                .subscribe().with(
                        success -> System.out.println("Email change notification (old address) mail sent to " + oldEmail),
                        failure -> System.err.println("Failed to send email change notification (old address) mail: " + failure.getMessage())
                );
    }

    /**
     * Sendet eine Bestätigungs-E-Mail an die neue E-Mail-Adresse.
     * @param nutzer Der Nutzer, dessen E-Mail geändert wird.
     * @param newEmail Die neue E-Mail-Adresse.
     * @param confirmationLink Der Link zur Bestätigung der neuen E-Mail-Adresse.
     */
    public void sendEmailChangeConfirmationNewAddress(Nutzer nutzer, String newEmail, String confirmationLink) {
        if (newEmail == null) return;
        emailChangeConfirmationNewAddressTemplate.to(newEmail)
                .subject("Bitte bestätige deine neue E-Mail-Adresse für den Vortragsmanager")
                .data("firstName", nutzer.firstName)
                .data("lastName", nutzer.lastName)
                .data("newEmail", newEmail)
                .data("confirmationLink", confirmationLink)
                .send()
                .subscribe().with(
                        success -> System.out.println("Email change confirmation (new address) mail sent to " + newEmail),
                        failure -> System.err.println("Failed to send email change confirmation (new address) mail: " + failure.getMessage())
                );
    }
}