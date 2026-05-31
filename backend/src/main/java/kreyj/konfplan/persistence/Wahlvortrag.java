package kreyj.konfplan.persistence;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.PreRemove;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static kreyj.konfplan.persistence.VortragVerfuegbarkeitId.vvId;

@Entity
@NoArgsConstructor
@Getter
@Setter
@DiscriminatorValue("WAHL")
public class Wahlvortrag extends Vortrag {

    private boolean wiederholbar;

    private int maxWiederholungen = 1;

    // -------------------------------------------------------------------
    // Konstruktoren
    // -------------------------------------------------------------------

    public Wahlvortrag(String titel, String inhalt, Referent referent, boolean wiederholbar, int maxWiederholungen, Veranstaltung veranstaltung) {
        super(titel, inhalt, referent, veranstaltung);

        this.wiederholbar = wiederholbar;
        this.maxWiederholungen = maxWiederholungen;
    }

    // -------------------------------------------------------------------
    // Overrides
    // -------------------------------------------------------------------


    @Override
    public boolean istPflicht() {
        return false;
    }

    @Override
    public void afterPersistAndFlush() {
        provideVortragVerfuegbarkeit();
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
     * VortragVerfuegbarkeit einrichten (afterPersistAndFlush)
     */

    private void provideVortragVerfuegbarkeit() {
        List<Long> slotIds = veranstaltung.getSlotIds();

        VortragVerfuegbarkeit vv = VortragVerfuegbarkeit.findById(vvId(this, veranstaltung));
        if (null == vv) {
            new VortragVerfuegbarkeit(this, veranstaltung, slotIds).persist();
        } else {
            throw new IllegalStateException("VortragVerfuegbarkeit für Vortrag "
                    + this.getTitel() + " und " + veranstaltung.getName() + " existiert bereits");
        }
    }
}