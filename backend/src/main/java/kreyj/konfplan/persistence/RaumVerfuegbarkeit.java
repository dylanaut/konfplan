package kreyj.konfplan.persistence;

import jakarta.persistence.AssociationOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Objects;
import java.util.Set;

import static kreyj.konfplan.persistence.RaumVerfuegbarkeitId.rvId;
import static kreyj.konfplan.persistence.RaumVerfuegbarkeitId.rvIdL;

@Entity
@NoArgsConstructor
@Getter
@Table(name = "RaumVerfuegbarkeit")
@IdClass(RaumVerfuegbarkeitId.class)
@AssociationOverride(
        name = "verfuegbareSlotIds",
        joinTable = @JoinTable(
                name = "raum_verfuegbarkeit_slots",
                joinColumns = {
                        @JoinColumn(name = "raum_id", referencedColumnName = "raum_id"),
                        @JoinColumn(name = "veranstaltung_id", referencedColumnName = "veranstaltung_id")
                }
        )
)
public class RaumVerfuegbarkeit extends VeranstaltungsVerfuegbarkeit {

    @Id
    @Column(name = "raum_id")
    private Long raumId;

    public RaumVerfuegbarkeit(Raum raum, Veranstaltung veranstaltung, Set<Long> verfuegbareSlotIds) {
        this(raum.getId(), veranstaltung.getId(), verfuegbareSlotIds);
    }

    public RaumVerfuegbarkeit(Long raumId, Long veranstaltungId, Set<Long> verfuegbareSlotIds) {
        super(veranstaltungId, verfuegbareSlotIds);
        Objects.requireNonNull(raumId, "RaumId darf nicht NULL sein");
        this.raumId = raumId;
    }

    // -------------------------------------------------------------------
    // Overrides
    // -------------------------------------------------------------------

    @Override
    public String toString() {
        return "RV<" + raumId + "," + veranstaltungId + ">=" + verfuegbareSlotIds;
    }


    // -------------------------------------------------------------------
    // Helper methods
    // -------------------------------------------------------------------


    public static boolean isRaumGebucht(Long raumId, Long slotId, Long veranstaltungId) {
        RaumVerfuegbarkeit rv = findById(rvIdL(raumId, veranstaltungId));

        return null != rv && !rv.verfuegbareSlotIds.contains(slotId);
    }


    public static boolean isRaumVerfuegbar(Raum raum, Slot slot, Veranstaltung veranstaltung) {
        Objects.requireNonNull(raum, "teilnehmer darf nicht NULL sein");
        Objects.requireNonNull(slot, "slot darf nicht NULL sein");
        Objects.requireNonNull(veranstaltung, "veranstaltung darf nicht NULL sein");

        RaumVerfuegbarkeit rv = findById(rvId(raum, veranstaltung));

        return null == rv || rv.verfuegbareSlotIds.contains(slot.getId());
    }
}
