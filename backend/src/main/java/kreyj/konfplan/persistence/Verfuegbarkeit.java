package kreyj.konfplan.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@NoArgsConstructor
@Getter
@Setter
@Table(uniqueConstraints = {
        @UniqueConstraint(columnNames = {"user_id", "slot_id"})
})
public class Verfuegbarkeit extends VersionedEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private Nutzer nutzer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "slot_id")
    private EventSlot slot;

    private boolean isAvailable = true;


    public Verfuegbarkeit(Nutzer nutzer, EventSlot slot, boolean isAvailable) {
        this.nutzer = nutzer;
        this.slot = slot;
        this.isAvailable = isAvailable;
    }

    @Override
    public String toString() {
        return nutzer + " ist in " + slot.getDescription()
                + (isAvailable ? "" : " nicht")
                + " verfügbar " + this.getId();
    }
}