package kreyj.vortragsmanager.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Entity
public class Raum extends PanacheEntity {

    @Column(nullable = false)
    public String name;

    @Column(nullable = false)
    public Integer kapazitaet;

    public String etage;

    @ManyToMany
    @JoinTable(
        name = "Raum_EventSlot",
        joinColumns = @JoinColumn(name = "raum_id"),
        inverseJoinColumns = @JoinColumn(name = "eventslot_id")
    )
    public Set<EventSlot> verfuegbareSlots = new HashSet<>();
}
