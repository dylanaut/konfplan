package kreyj.vortragsmanager.entity;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

@Entity
@DiscriminatorValue("ADMIN")
public class Admin extends Nutzer {
    // Hier können später spezifische Admin-Rechte oder Logging-Felder rein.

    public Admin() {
        this.role = "ADMIN";
    }
}
