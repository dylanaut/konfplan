package kreyj.vortragsmanager.entity;

import jakarta.persistence.*;

@Entity
public class Verfuegbarkeit extends SqliteEntity {

    @ManyToOne
    @JoinColumn(name = "referent_id", columnDefinition = "INTEGER")
    public Referent referent;

    @ManyToOne
    @JoinColumn(name = "slot_id", columnDefinition = "INTEGER")
    public EventSlot slot;

    public boolean isAvailable = true;

    public Verfuegbarkeit() {}
}
