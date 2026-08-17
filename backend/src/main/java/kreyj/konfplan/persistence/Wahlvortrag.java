package kreyj.konfplan.persistence;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PreRemove;
import jakarta.transaction.Transactional;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import static kreyj.konfplan.persistence.VortragVerfuegbarkeitId.vvId;

@Entity
@NoArgsConstructor
@Getter
@Setter
@DiscriminatorValue("WAHL")
public class Wahlvortrag extends Vortrag {

    private boolean wiederholbar;

    private int maxWiederholungen = 1;

    @JsonIgnore
    @OneToMany(mappedBy = "vortrag", orphanRemoval = true, fetch = FetchType.LAZY)
    private Set<Prioritaet> prioritaeten;

    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "wahlvortrag_veranlagungen", joinColumns = @JoinColumn(name = "vortrag_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "veranlagung", length = 50)
    private Set<Veranlagung> veranlagungen = new HashSet<>();


    // -------------------------------------------------------------------
    // Konstruktoren
    // -------------------------------------------------------------------

    protected Wahlvortrag(String titel, String inhalt, Referent referent, boolean wiederholbar, int maxWiederholungen,
                          Veranstaltung veranstaltung) {
        super(titel, inhalt, referent, veranstaltung);

        this.wiederholbar = wiederholbar;
        this.maxWiederholungen = maxWiederholungen;
    }


    @Transactional
    public static Wahlvortrag create(String titel, String inhalt, Referent referent, boolean wiederholbar, int maxWiederholungen,
                                     Veranstaltung veranstaltung) {
        Objects.requireNonNull(veranstaltung);
        Wahlvortrag wv = new Wahlvortrag(titel, inhalt, referent, wiederholbar, maxWiederholungen, veranstaltung);

        wv.persistAndFlush();

        veranstaltung.addVortrag(wv);

        wv.provideVortragVerfuegbarkeit();

        wv.persist();

        return wv;
    }

    // -------------------------------------------------------------------
    // Overrides
    // -------------------------------------------------------------------


    @Override
    public boolean istPflicht() {
        return false;
    }


    // -------------------------------------------------------------------
    // Verfuegbarkeiten anpassen
    // -------------------------------------------------------------------


    @PreRemove
    public void deleteVerfuegbarkeit() {
        VortragVerfuegbarkeit vv = VortragVerfuegbarkeit.findById(vvId(this, this.getVeranstaltung()));
        if (null != vv) {
            vv.delete();
        }
    }

    // -------------------------------------------------------------------
    // Helper methods
    // -------------------------------------------------------------------


    /**
     * VortragVerfuegbarkeit einrichten
     */

    private void provideVortragVerfuegbarkeit() {
        Set<Long> slotIds = veranstaltung.getSlotIds();

        VortragVerfuegbarkeit vv = VortragVerfuegbarkeit.findById(vvId(this, veranstaltung));
        if (null == vv) {
            new VortragVerfuegbarkeit(this, veranstaltung, slotIds).persist();
        } else {
            throw new IllegalStateException("VortragVerfuegbarkeit für Vortrag "
                    + this.getTitel() + " und " + veranstaltung.getName() + " existiert bereits");
        }
    }


    public Set<Veranlagung> getVeranlagungen() {
        return Collections.unmodifiableSet(veranlagungen);
    }


    public void setVeranlagungen(Set<Veranlagung> neueVeranlagungen) {
        veranlagungen.clear();
        if (null != neueVeranlagungen) {
            veranlagungen.addAll(neueVeranlagungen);
        }
    }
}
