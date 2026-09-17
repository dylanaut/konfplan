package kreyj.konfplan.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Syntax-Anforderung an Passwörter, die ein Organisator/Administrator für eine Veranstaltung und
 * eine bestimmte Nutzerrolle selbst setzt (Einzel-Reset, ZIP-Bulk-Generierung - siehe #741). Gilt
 * NICHT für Keycloaks eigenen Self-Service-Flow ("Passwort vergessen"), der weiterhin
 * ausschließlich Keycloaks realm-weiter Policy unterliegt. Existiert für ein
 * (Veranstaltung, Rolle)-Paar keine Zeile, gilt {@link #STANDARD} als Fallback.
 */
@Entity
@Table(uniqueConstraints = @UniqueConstraint(name = "UK_PASSWORTRICHTLINIE_VERANSTALTUNG_ROLLE", columnNames = {"veranstaltung_id", "rolle"}))
@NoArgsConstructor
@Getter
@Setter
public class Passwortrichtlinie extends VersionedEntity {

    /** Entspricht der heutigen, einzigen realm-weiten Keycloak-Policy - Fallback, solange für ein (Veranstaltung, Rolle)-Paar nichts konfiguriert ist. */
    public static final Passwortrichtlinie STANDARD =
        new Passwortrichtlinie(null, null, 8, null, true, true, true, true, false);


    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Veranstaltung veranstaltung;

    /** Einer der Nutzer-Diskriminatorwerte: ORGANISATOR, ADMINISTRATOR, REFERENT, TEILNEHMER, BETRACHTER. */
    private String rolle;

    private int minLaenge;

    /** null = unbegrenzt. */
    private Integer maxLaenge;

    private boolean erfordertGrossbuchstabe;
    private boolean erfordertKleinbuchstabe;
    private boolean erfordertZiffer;
    private boolean erfordertSonderzeichen;

    /** PIN-Modus: das Passwort darf ausschließlich aus Ziffern bestehen; ignoriert dabei die anderen {@code erfordert*}-Flags. */
    private boolean nurZiffern;


    public Passwortrichtlinie(Veranstaltung veranstaltung, String rolle, int minLaenge, Integer maxLaenge,
                               boolean erfordertGrossbuchstabe, boolean erfordertKleinbuchstabe,
                               boolean erfordertZiffer, boolean erfordertSonderzeichen, boolean nurZiffern) {
        this.veranstaltung = veranstaltung;
        this.rolle = rolle;
        this.minLaenge = minLaenge;
        this.maxLaenge = maxLaenge;
        this.erfordertGrossbuchstabe = erfordertGrossbuchstabe;
        this.erfordertKleinbuchstabe = erfordertKleinbuchstabe;
        this.erfordertZiffer = erfordertZiffer;
        this.erfordertSonderzeichen = erfordertSonderzeichen;
        this.nurZiffern = nurZiffern;
    }


    public boolean erfuellt(String passwort) {
        if (null == passwort || passwort.length() < minLaenge) {
            return false;
        }
        if (null != maxLaenge && passwort.length() > maxLaenge) {
            return false;
        }
        if (nurZiffern) {
            return passwort.chars().allMatch(Character::isDigit);
        }
        if (erfordertGrossbuchstabe && passwort.chars().noneMatch(Character::isUpperCase)) {
            return false;
        }
        if (erfordertKleinbuchstabe && passwort.chars().noneMatch(Character::isLowerCase)) {
            return false;
        }
        if (erfordertZiffer && passwort.chars().noneMatch(Character::isDigit)) {
            return false;
        }
        return !erfordertSonderzeichen || passwort.chars().anyMatch(c -> !Character.isLetterOrDigit(c));
    }


    public static Passwortrichtlinie findByVeranstaltungUndRolle(Veranstaltung veranstaltung, String rolle) {
        return find("veranstaltung = ?1 and rolle = ?2", veranstaltung, rolle).firstResult();
    }
}
