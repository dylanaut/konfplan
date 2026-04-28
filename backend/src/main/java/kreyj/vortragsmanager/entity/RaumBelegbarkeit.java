package kreyj.vortragsmanager.entity;

import jakarta.persistence.*;

@Entity
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
