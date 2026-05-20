package kreyj.konfplan.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.Setter;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

@Entity
@Getter
@Setter
public class Raum extends VersionedEntity {

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private Integer kapazitaet;

    private String etage;

    @ManyToOne
    @JoinColumn(name = "gebaeude_id")
    private Gebaeude gebaeude;

    @ManyToMany
    @JoinTable(
            name = "Raum_EventSlot",
            joinColumns = @JoinColumn(name = "raum_id"),
            inverseJoinColumns = @JoinColumn(name = "eventslot_id")
    )
    private Set<EventSlot> verfuegbareSlots = new HashSet<>();

    public Raum() {
    }

    public Raum(String name, int kapazitaet) {
        super();
        this.name = name;
        this.kapazitaet = kapazitaet;
    }

    public void setGebaeude(Gebaeude gebaeude) {
        this.gebaeude = gebaeude;
        gebaeude.addRaum(this);
    }

    public Set<EventSlot> getVerfuegbareSlots() {
        return Collections.unmodifiableSet(verfuegbareSlots);
    }
}