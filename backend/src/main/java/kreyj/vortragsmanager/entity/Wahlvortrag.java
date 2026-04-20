package kreyj.vortragsmanager.entity;

import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;

@Entity
@DiscriminatorValue("WAHL")
public class Wahlvortrag extends Vortrag {

    public boolean wiederholbar;

    public int maxWiederholungen = 1;

    @ManyToMany
    @JoinTable(
        name = "Wahlvortrag_EventSlot",
        joinColumns = @JoinColumn(name = "vortrag_id"),
        inverseJoinColumns = @JoinColumn(name = "eventslot_id")
    )
    public List<EventSlot> wahlSlots = new ArrayList<>();

    @Override
    public boolean istPflicht() {
        return false;
    }
}
