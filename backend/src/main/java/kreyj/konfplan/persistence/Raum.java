package kreyj.konfplan.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

@Entity
@NoArgsConstructor
@Getter
@Setter
public class Raum extends VersionedEntity {

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private Integer kapazitaet;

    private String etage;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "gebaeude_id")
    private Gebaeude gebaeude;

    void setGebaeude(Gebaeude gebaeude) {
        this.gebaeude = gebaeude;
    }

    @ManyToMany
    @JoinTable(
            name = "Raum_EventSlot",
            joinColumns = @JoinColumn(name = "raum_id"),
            inverseJoinColumns = @JoinColumn(name = "eventslot_id")
    )
    private Set<EventSlot> verfuegbareSlots = new HashSet<>();

    public Set<EventSlot> getVerfuegbareSlots() {
        return Collections.unmodifiableSet(verfuegbareSlots);
    }


    public void addSlot(EventSlot slot) {
        if (null == slot) {
            return;
        }

        if (verfuegbareSlots.add(slot)) {
            slot.raeume.add(this);
        }
    }

    public void removeSlot(EventSlot slot) {
        if (null == slot) {
            return;
        }

        if (verfuegbareSlots.remove(slot)) {
            slot.raeume.remove(this);
        }
    }

    public Raum(String name, int kapazitaet) {
        super();
        this.name = name;
        this.kapazitaet = kapazitaet;
    }
}