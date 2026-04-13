package kreyj.vortragsmanager.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Entity
public class Raum extends SqliteEntity {

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
        joinColumns = @JoinColumn(name = "raum_id", columnDefinition = "INTEGER"),
        inverseJoinColumns = @JoinColumn(name = "eventslot_id", columnDefinition = "INTEGER")
    )
    public Set<EventSlot> verfuegbareSlots = new HashSet<>();

    public Gebaeude getGebaeude() {
        return gebaeude;
    }

    public void setGebaeude(Gebaeude gebaeude) {
        this.gebaeude = gebaeude;
    }
}
