package kreyj.vortragsmanager.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import jakarta.persistence.*;

@Entity
@Table(name = "Vortrag")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "vortrag_typ", discriminatorType = DiscriminatorType.STRING)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "vortrag_typ", visible = true) // Korrigiert: property = "vortrag_typ"
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
    @JoinColumn(name = "referent_id", columnDefinition = "INTEGER")
    public Referent referent;

    @ManyToOne(optional = false) // Relation zur Veranstaltung
    @JoinColumn(name = "veranstaltung_id", columnDefinition = "INTEGER")
    public Veranstaltung veranstaltung;

    @JsonProperty("istPflicht")
    public abstract boolean istPflicht();

    public Vortrag() {}
}
