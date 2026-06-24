package kreyj.konfplan.persistence;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PreRemove;
import jakarta.transaction.Transactional;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

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
}
