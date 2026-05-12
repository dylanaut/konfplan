package kreyj.konfplan.persistence;

import jakarta.persistence.*;

import java.util.HashSet;
import java.util.Set;

@Entity
public class Raum extends VersionedEntity {

    @Column(nullable = false)
    public String name;

    @Column(nullable = false)
    public Integer kapazitaet;

    public String etage;

    @ManyToOne
    @JoinColumn(name = "gebaeude_id")
    public Gebaeude gebaeude;

    @ManyToMany
    @JoinTable(
        name = "Raum_EventSlot",
        joinColumns = @JoinColumn(name = "raum_id"),
        inverseJoinColumns = @JoinColumn(name = "eventslot_id")
    )
    public Set<EventSlot> verfuegbareSlots = new HashSet<>();

    public Gebaeude getGebaeude() {
        return gebaeude;
    }

    public void setGebaeude(Gebaeude gebaeude) {
        this.gebaeude = gebaeude;
    }
}
