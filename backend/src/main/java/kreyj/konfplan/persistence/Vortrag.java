package kreyj.konfplan.persistence;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorColumn;
import jakarta.persistence.DiscriminatorType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.Objects;
import java.util.Set;

import static kreyj.konfplan.persistence.VortragVerfuegbarkeitId.vvId;

@Entity
@NoArgsConstructor
@Getter
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
    @Setter
    private String titel;

    @Column(columnDefinition = "TEXT")
    @Setter
    private String inhalt;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "referent_id")
    @JsonIgnoreProperties("vortraege")
    Referent referent; // Pflege über Referent.addVortrag()

    public void setReferent(Referent aReferent) {
        if (null == aReferent) {
            throw new IllegalArgumentException("Referent darf nicht null sein");
        } else {
            if (null != this.referent && !this.referent.equals(aReferent)) {
                this.referent.removeVortrag(this);
            }
            aReferent.addVortrag(this);
        }
    }

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "veranstaltung_id")
    @JsonIgnoreProperties({"vortraege", "nutzer", "gebaeude", "slots"})
    Veranstaltung veranstaltung; // Pflege über Veranstaltung.addVortrag()

    public void setVeranstaltung(Veranstaltung aVeranstaltung) {
        if (null == aVeranstaltung) {
            throw new IllegalArgumentException("Veranstaltung darf nicht null sein");
        } else {
            if (null != this.veranstaltung && !this.veranstaltung.equals(aVeranstaltung)) {
                this.veranstaltung.removeVortrag(this);
            }
            aVeranstaltung.addVortrag(this);
        }
    }

    @JsonProperty("istPflicht")
    public abstract boolean istPflicht();


    // -------------------------------------------------------------------
    // Konstruktoren
    // -------------------------------------------------------------------

    public Vortrag(String titel, String inhalt, Referent referent, Veranstaltung veranstaltung) {
        this.titel = titel;
        this.inhalt = inhalt;
        this.referent = referent;

        Objects.requireNonNull(veranstaltung);
        this.veranstaltung = veranstaltung;

        veranstaltung.addVortrag(this);
    }

    public abstract void afterPersistAndFlush();


    // -------------------------------------------------------------------
    // public methods
    // -------------------------------------------------------------------

    public void updateVortragVerfuegbarkeit(Veranstaltung veranstaltung, Slot slot, boolean verfuegbar) {
        Objects.requireNonNull(veranstaltung);
        Objects.requireNonNull(slot);

        VortragVerfuegbarkeit vv = VortragVerfuegbarkeit.findById(vvId(this, veranstaltung));

        if (verfuegbar) {
            if (null == vv) {
                new VortragVerfuegbarkeit(this, veranstaltung, List.of(slot.getId())).persist();
            } else {
                vv.addSlot(slot);
            }
        } else // Vortrag für Slot und Veranstaltung NICHT verfuegbar
            if (null != vv) {
                vv.removeSlot(slot);
            }
    }
}