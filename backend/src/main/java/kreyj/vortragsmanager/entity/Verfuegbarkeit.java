package kreyj.vortragsmanager.entity;

import jakarta.persistence.*;

@Entity
public class Verfuegbarkeit extends SqliteEntity {

    @ManyToOne
    public Referent referent;

    @ManyToOne
    public EventSlot slot;

    public boolean isAvailable = true;

    public Verfuegbarkeit() {}
}
