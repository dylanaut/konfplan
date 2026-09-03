package kreyj.konfplan.persistence;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

@Entity
@DiscriminatorValue("ORGANISATOR")
public class Organisator extends Nutzer {
    // Hier können später spezifische Organisator-Rechte oder Logging-Felder rein.

    public Organisator() {
        this.setRole("ORGANISATOR");
    }
}
