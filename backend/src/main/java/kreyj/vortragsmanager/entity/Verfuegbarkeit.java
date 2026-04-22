package kreyj.vortragsmanager.entity;

import jakarta.persistence.*;

@Entity
public class Verfuegbarkeit extends VersionedEntity {

    @ManyToOne
    @JoinColumn(name = "user_id")
    public User user;

    @ManyToOne
    @JoinColumn(name = "slot_id")
    public EventSlot slot;

    public boolean isAvailable = true;

    public Verfuegbarkeit() {}
}
