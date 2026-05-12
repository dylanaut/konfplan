package kreyj.konfplan.persistence;

import jakarta.persistence.*;

@Entity
@Table(uniqueConstraints = {
    @UniqueConstraint(columnNames = {"raum_id", "slot_id"})
})
public class RaumBelegbarkeit extends VersionedEntity {

    @ManyToOne
    @JoinColumn(name = "raum_id")
    public Raum raum;

    @ManyToOne
    @JoinColumn(name = "slot_id")
    public EventSlot slot;

    public boolean isBelegt = false;

    public RaumBelegbarkeit() {}
}
