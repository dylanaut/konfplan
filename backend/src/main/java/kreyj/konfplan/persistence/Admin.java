package kreyj.konfplan.persistence;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import lombok.NoArgsConstructor;

@Entity
@DiscriminatorValue("ADMIN")
public class Admin extends Nutzer {
    // Hier können später spezifische Admin-Rechte oder Logging-Felder rein.

    public Admin() {
        this.setRole("ADMIN");
    }
}
