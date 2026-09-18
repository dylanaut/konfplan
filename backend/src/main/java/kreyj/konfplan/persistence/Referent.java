package kreyj.konfplan.persistence;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

/**
 * Primärrolle Referent - alle Felder/Methoden (jobRole, organisation, vortraege, ...) leben seit
 * #751 auf {@link Nutzer}, damit eine Person auch mit REFERENT als Zusatzrolle vollwertig als
 * Referent handeln kann.
 */
@Entity
@DiscriminatorValue("REFERENT")
public class Referent extends Nutzer {

    public Referent() {
        this.setRole("REFERENT");
    }
}
