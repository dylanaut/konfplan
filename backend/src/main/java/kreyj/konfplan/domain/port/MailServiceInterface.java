package kreyj.konfplan.domain.port;

import kreyj.konfplan.persistence.Admin;
import kreyj.konfplan.persistence.Nutzer;
import kreyj.konfplan.persistence.Referent;
import kreyj.konfplan.persistence.Veranstaltung;
import kreyj.konfplan.persistence.Vortrag;

public interface MailServiceInterface {
    void sendVortragsRegistrierung(Veranstaltung v, Referent referent, Vortrag vortrag, boolean isAdded);
    void sendVortragsRegistrierung(Admin organisator, Veranstaltung v, Referent referent, Vortrag vortrag, boolean isAdded);
    void sendEinladungZuVeranstaltung(Nutzer nutzer, Veranstaltung v);
    void sendRegistrationConfirmation(Nutzer nutzer);
    void sendUserDeletionNotification(Nutzer nutzer);
    void sendEmailChangeNotificationOldAddress(Nutzer nutzer, String oldEmail, String newEmail);
    void sendEmailChangeConfirmationNewAddress(Nutzer nutzer, String newEmail, String confirmationLink);
}