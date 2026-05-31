package kreyj.konfplan.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.Objects;
import java.util.Set;

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

    // -------------------------------------------------------------------
    // Helper methods
    // -------------------------------------------------------------------

    public void updateRaumVerfuegbarkeit(Veranstaltung veranstaltung, Slot slot, boolean verfuegbar) {
        Objects.requireNonNull(veranstaltung);
        Objects.requireNonNull(slot);

        RaumVerfuegbarkeit rv = RaumVerfuegbarkeit.findById(rvId(this, veranstaltung));

        if (verfuegbar) {
            if (null == rv) {
                new RaumVerfuegbarkeit(this, veranstaltung, List.of(slot.getId())).persistAndFlush();
            } else {
                rv.addSlot(slot);
            }
        } else // Raum für Slot und Veranstaltung NICHT verfuegbar
            if (null != rv) {
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