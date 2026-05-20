package kreyj.konfplan.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
@Table(uniqueConstraints = {
        @UniqueConstraint(columnNames = {"user_id", "slot_id"})
})
public class Verfuegbarkeit extends VersionedEntity {

    @ManyToOne
    @JoinColumn(name = "user_id")
    private Nutzer nutzer;

    @ManyToOne
    @JoinColumn(name = "slot_id")
    private EventSlot slot;

    private boolean isAvailable = true;

    public Verfuegbarkeit() {
    }

    public Verfuegbarkeit(Nutzer nutzer, EventSlot slot, boolean isAvailable) {
        this.nutzer = nutzer;
        this.slot = slot;
        this.isAvailable = isAvailable;
    }

    @Override
    public String toString() {
        return nutzer + " ist in " + slot
                + (isAvailable ? "" : " nicht")
                + " verfügbar " + this.getId();
    }
}