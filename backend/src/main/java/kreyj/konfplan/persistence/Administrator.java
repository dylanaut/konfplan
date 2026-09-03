package kreyj.konfplan.persistence;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

/**
 * Hat dieselben Rechte wie {@link Organisator} (per Java-Vererbung erfasst jedes
 * "instanceof Organisator" automatisch auch Administrator-Nutzer), zusaetzlich aber exklusiv
 * das Recht, eine Wartung anzukuendigen und den Verzeichnis-Import durchzufuehren (siehe
 * WartungshinweisResource/VeranstaltungImportResource, dort jeweils "ADMINISTRATOR"-only).
 */
@Entity
@DiscriminatorValue("ADMINISTRATOR")
public class Administrator extends Organisator {

    public Administrator() {
        super();
        this.setRole("ADMINISTRATOR");
    }
}
