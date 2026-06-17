package kreyj.konfplan.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Objects;

import static kreyj.konfplan.persistence.RaumVerfuegbarkeitId.rvId;

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
    Gebaeude gebaeude;


    // -------------------------------------------------------------------
    // Konstruktoren
    // -------------------------------------------------------------------

    public Raum(String name, int kapazitaet) {
        super();
        this.name = name;
        this.kapazitaet = kapazitaet;
    }

    public Raum(String name, int kapazitaet, String etage) {
        super();
        this.name = name;
        this.kapazitaet = kapazitaet;
    }

    // -------------------------------------------------------------------
    // Helper methods
    // -------------------------------------------------------------------

    public void updateRaumVerfuegbarkeit(Slot slot, Veranstaltung veranstaltung, boolean verfuegbar) {
        updateRaumVerfuegbarkeit(slot, veranstaltung, verfuegbar, false);
    }

    public void updateRaumVerfuegbarkeit(Slot slot, Veranstaltung veranstaltung, boolean verfuegbar, boolean createIfMissing) {
        Objects.requireNonNull(veranstaltung);
        Objects.requireNonNull(slot);

        RaumVerfuegbarkeit rv = RaumVerfuegbarkeit.findById(rvId(this, veranstaltung));
        if (null == rv) {
            if (createIfMissing) {
                rv = new RaumVerfuegbarkeit(this, veranstaltung, veranstaltung.getSlotIds());
                rv.persist();
            } else {
                throw new IllegalStateException("Missing RaumVerfuegbarkeit für " + this.getName()
                        + " in Veranstaltung '" + veranstaltung.getName() + "'");
            }
        }
        if (verfuegbar) {
            rv.addSlot(slot);
        } else {
            rv.removeSlot(slot);
        }
    }


    public void deleteRaumVerfuegbarkeit(Veranstaltung veranstaltung) {
        Objects.requireNonNull(veranstaltung);

        RaumVerfuegbarkeit rv = RaumVerfuegbarkeit.findById(rvId(this, veranstaltung));
        if (null != rv) {
            rv.delete();
        }
    }
}