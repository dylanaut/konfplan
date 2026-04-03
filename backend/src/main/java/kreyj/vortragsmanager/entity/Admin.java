package kreyj.vortragsmanager.entity;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

@Entity
@DiscriminatorValue("ADMIN")
public class Admin extends User {
    // Hier können später spezifische Admin-Rechte oder Logging-Felder rein.
    
    public Admin() {
        // Die role wird von Hibernate über @DiscriminatorValue gesetzt
    }
}
