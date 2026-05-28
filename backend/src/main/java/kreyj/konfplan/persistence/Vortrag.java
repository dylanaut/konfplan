package kreyj.konfplan.persistence;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorColumn;
import jakarta.persistence.DiscriminatorType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

@Entity
@NoArgsConstructor
@Getter
@Setter
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
    private String titel;

    @Column(columnDefinition = "TEXT")
    private String inhalt;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "referent_id")
    @JsonIgnoreProperties("vortraege")
    Referent referent;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "veranstaltung_id")
    @JsonIgnoreProperties({"vortraege", "nutzer", "gebaeude", "slots"})
    Veranstaltung veranstaltung;


    @JsonProperty("istPflicht")
    public abstract boolean istPflicht();


    // -------------------------------------------------------------------
    // Konstruktoren
    // -------------------------------------------------------------------


    public Vortrag(String titel, Referent referent) {
        this.titel = titel;
        this.referent = referent;
    }

    public Vortrag(String titel, Referent referent, Veranstaltung veranstaltung) {
        this.titel = titel;
        this.referent = referent;
        this.veranstaltung = veranstaltung;
    }
}