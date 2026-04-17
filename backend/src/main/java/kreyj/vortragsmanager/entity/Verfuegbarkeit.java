package kreyj.vortragsmanager.entity;

import jakarta.persistence.*;

@Entity
public class Verfuegbarkeit extends VersionedEntity {

    @ManyToOne
    @JoinColumn(name = "user_id", columnDefinition = "INTEGER")
    public User user;

    @ManyToOne
    @JoinColumn(name = "slot_id", columnDefinition = "INTEGER")
    public EventSlot slot;

    public boolean isAvailable = true;

    public Verfuegbarkeit() {}
}
