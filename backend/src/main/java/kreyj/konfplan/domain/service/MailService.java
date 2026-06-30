package kreyj.konfplan.domain.service;

import io.quarkus.mailer.Mail;
import io.quarkus.mailer.MailTemplate;
import io.quarkus.mailer.Mailer;
import io.quarkus.qute.Location;
import jakarta.enterprise.context.ApplicationScoped;
import kreyj.konfplan.persistence.Admin;
import kreyj.konfplan.persistence.Nutzer;
import kreyj.konfplan.persistence.Referent;
import kreyj.konfplan.persistence.Veranstaltung;
import kreyj.konfplan.persistence.Vortrag;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class MailService {
    private final String adminEmail;

    private final Mailer mailer;

    private final MailTemplate registrationConfirmationTemplate;

    private final MailTemplate deregistrationNotificationTemplate;

    private final MailTemplate emailChangeNotificationOldAddressTemplate;

    private final MailTemplate emailChangeConfirmationNewAddressTemplate;


    public MailService(
        @ConfigProperty(name = "app.mail.admin", defaultValue = "konfplan@yahoo.com")
        String adminEmail,
        Mailer mailer,
        @Location("email/registrationConfirmation")
        MailTemplate registrationConfirmationTemplate,
        @Location("email/deregistrationNotification")
        MailTemplate deregistrationNotificationTemplate,
        @Location("email/emailChangeNotificationOldAddress")
        MailTemplate emailChangeNotificationOldAddressTemplate,
        @Location("email/emailChangeConfirmationNewAddress")
        MailTemplate emailChangeConfirmationNewAddressTemplate) {
        this.adminEmail = adminEmail;
        this.mailer = mailer;
        this.registrationConfirmationTemplate = registrationConfirmationTemplate;
        this.deregistrationNotificationTemplate = deregistrationNotificationTemplate;
        this.emailChangeNotificationOldAddressTemplate = emailChangeNotificationOldAddressTemplate;
        this.emailChangeConfirmationNewAddressTemplate = emailChangeConfirmationNewAddressTemplate;
    }


    public void sendVortragsRegistrierung(Veranstaltung v, Referent referent, Vortrag vortrag, boolean isAdded) {
        v.organisatoren().forEach(admin -> sendVortragsRegistrierung(admin, v, referent, vortrag, isAdded));
    }


    public void sendVortragsRegistrierung(Admin organisator, Veranstaltung v, Referent referent, Vortrag vortrag, boolean isAdded) {
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
        if (nutzer.getEmail() == null) {
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
        if (nutzer.getEmail() == null) {
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
        if (nutzer.getEmail() == null) {
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


    /**
     * Sendet eine Benachrichtigung an die alte E-Mail-Adresse, dass diese geändert wurde.
     *
     * @param nutzer   Der Nutzer, dessen E-Mail geändert wurde.
     * @param oldEmail Die alte E-Mail-Adresse.
     * @param newEmail Die neue E-Mail-Adresse.
     */

    public void sendEmailChangeNotificationOldAddress(Nutzer nutzer, String oldEmail, String newEmail) {
        if (null == oldEmail) {
            return;
        }
        emailChangeNotificationOldAddressTemplate.to(oldEmail)
            .subject("Wichtige Information: Deine E-Mail-Adresse wurde geändert")
            .from(adminEmail)
            .data("firstName", nutzer.getFirstName())
            .data("lastName", nutzer.getLastName())
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
     *
     * @param nutzer           Der Nutzer, dessen E-Mail geändert wird.
     * @param newEmail         Die neue E-Mail-Adresse.
     * @param confirmationLink Der Link zur Bestätigung der neuen E-Mail-Adresse.
     */

    public void sendEmailChangeConfirmationNewAddress(Nutzer nutzer, String newEmail, String confirmationLink) {
        if (null == newEmail) {
            return;
        }
        emailChangeConfirmationNewAddressTemplate.to(newEmail)
            .subject("Bitte bestätige deine neue E-Mail-Adresse für den KonfPlan")
            .from(adminEmail)
            .data("firstName", nutzer.getFirstName())
            .data("lastName", nutzer.getLastName())
            .data("newEmail", newEmail)
            .data("confirmationLink", confirmationLink)
            .send()
            .subscribe().with(
                success -> System.out.println("Email change confirmation (new address) mail sent to " + newEmail),
                failure -> System.err.println("Failed to send email change confirmation (new address) mail: " + failure.getMessage())
            );
    }

    public void sendVerfuegbarkeitChangedNotification(Nutzer nutzer, Veranstaltung veranstaltung) {
        String subject = "Verfügbarkeit geändert für " + veranstaltung.getName();
        String body = String.format(
            "Hallo,\n\nder Teilnehmer %s %s hat seine Verfügbarkeit für die Veranstaltung '%s' geändert.\n\nDies ist eine automatische Benachrichtigung.",
            nutzer.getFirstName(), nutzer.getLastName(),
            veranstaltung.getName()
        );

        veranstaltung.organisatoren().forEach(organisator -> {
            mailer.send(Mail.withText(organisator.getEmail(), subject, body)
                .setFrom(senderEmail(veranstaltung)));
        });
    }

    // -------------------------------------------------------------------
    // Helper methods
    // -------------------------------------------------------------------


    private static String senderEmail(Veranstaltung v) {
        return v.organisatoren().iterator().next().getEmail();
    }
}
