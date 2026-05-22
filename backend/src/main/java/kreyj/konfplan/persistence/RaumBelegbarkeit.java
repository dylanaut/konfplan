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
        @UniqueConstraint(columnNames = {"raum_id", "slot_id"})
})
public class RaumBelegbarkeit extends VersionedEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "raum_id")
    private Raum raum;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "slot_id")
    private EventSlot slot;

    private boolean isBelegt = false;


    public RaumBelegbarkeit(Raum raum, EventSlot slot, boolean isBelegt) {
        this.raum = raum;
        this.slot = slot;
        this.isBelegt = isBelegt;
        System.out.println("RB " + raum.getId() + ", " + slot.getId() + ": " + isBelegt);
    }
}