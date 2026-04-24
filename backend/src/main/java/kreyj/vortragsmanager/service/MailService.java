package kreyj.vortragsmanager.service;

import io.quarkus.mailer.Mail;
import io.quarkus.mailer.Mailer;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import kreyj.vortragsmanager.entity.*;

@ApplicationScoped
public class MailService {

    @Inject
    Mailer mailer;

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
}
