package kreyj.konfplan.persistence;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "Vortrag")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "vortrag_typ", discriminatorType = DiscriminatorType.STRING)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "vortrag_typ", visible = true)
@JsonSubTypes({
    @JsonSubTypes.Type(value = Pflichtvortrag.class, name = "PFLICHT"),
    @JsonSubTypes.Type(value = Wahlvortrag.class, name = "WAHL")
})
public abstract class Vortrag extends VersionedEntity {
    @Column(nullable = false)
    public String titel;

    @Column(columnDefinition = "TEXT")
    public String inhalt;

    @ManyToOne(optional = false)
    @JoinColumn(name = "referent_id")
    @JsonIgnoreProperties("vortraege")
    public Referent referent;

    @ManyToOne(optional = false) // Relation zur Veranstaltung
    @JoinColumn(name = "veranstaltung_id")
    @JsonIgnoreProperties({"vortraege", "nutzer", "gebaeude", "eventSlots"})
    public Veranstaltung veranstaltung;
    @ManyToMany
    @JoinTable(name = "Vortrag_EventSlot",
            joinColumns = @JoinColumn(name = "vortrag_id"),
            inverseJoinColumns = @JoinColumn(name = "eventslot_id"))
    private Set<EventSlot> verfuegbareSlots = new HashSet<>();

    @JsonProperty("istPflicht")
    public abstract boolean istPflicht();

    public Vortrag() {}

    public Set<EventSlot> getVerfuegbareSlots() {
        return Collections.unmodifiableSet(verfuegbareSlots);
    }

    public void addVerfuegbarenSlot(EventSlot slot) {
        verfuegbareSlots.add(slot);
    }

    public void removeVerfuegbarenSlot(EventSlot slot) {
        verfuegbareSlots.remove(slot);
    }

    public void clearVerfuegbareSlots() {
        for (EventSlot eventSlot : new ArrayList<>(verfuegbareSlots)) {
            removeVerfuegbarenSlot(eventSlot);
        }
    }
}
