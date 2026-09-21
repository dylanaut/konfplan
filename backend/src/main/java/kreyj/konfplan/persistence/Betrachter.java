package kreyj.konfplan.persistence;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

/**
 * Primärrolle Betrachter (rein lesender Zugriff) - alle Felder/Methoden (gruppenwerte, ...) leben
 * seit #751 auf {@link Nutzer} als {@code betrachterGruppenwerte}, damit eine Person auch mit
 * BETRACHTER als Zusatzrolle vollwertig als Betrachter handeln kann. Anders als bei
 * {@code Nutzer#addTeilnehmerGruppenwert} gibt es hier keine Einwertigkeits-Regel: ein Betrachter
 * darf auch innerhalb derselben (nicht mehrwertigen) Kategorie mehrere Werte gleichzeitig
 * zugewiesen bekommen (z.B. zwei Klassen).
 */
@Entity
@DiscriminatorValue("BETRACHTER")
public class Betrachter extends Nutzer {

    public Betrachter() {
        this.setRole("BETRACHTER");
    }
}
