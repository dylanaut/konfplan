package kreyj.vortragsmanager.entity;

import jakarta.persistence.*;

@Entity
@Table(uniqueConstraints = {
        @UniqueConstraint(columnNames = {"user_id", "slot_id"})
})
public class Verfuegbarkeit extends VersionedEntity {

    @ManyToOne
    @JoinColumn(name = "user_id")
    public Nutzer nutzer;

    @ManyToOne
    @JoinColumn(name = "slot_id")
    public EventSlot slot;

    public boolean isAvailable = true;

    public Verfuegbarkeit() {
    }

    @Override
    public String toString() {
        return nutzer + " ist in " + slot
                + (isAvailable ? "" : " nicht")
                + " verfügbar " + this.id;
    }
}
