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

import static kreyj.konfplan.persistence.VortragVerfuegbarkeitId.vvId;

@Entity
@NoArgsConstructor
@Getter
@Table(name = "VortragVerfuegbarkeit")
@IdClass(VortragVerfuegbarkeitId.class)
@AssociationOverride(
        name = "verfuegbareSlotIds",
        joinTable = @JoinTable(
                name = "vortrag_verfuegbarkeit_slots",
                joinColumns = {
                        @JoinColumn(name = "vortrag_id", referencedColumnName = "vortrag_id"),
                        @JoinColumn(name = "veranstaltung_id", referencedColumnName = "veranstaltung_id")
                }
        )
)
public class VortragVerfuegbarkeit extends VeranstaltungsVerfuegbarkeit {

    @Id
    @Column(name = "vortrag_id")
    private Long vortragId;

    // -------------------------------------------------------------------
    // Konstruktoren
    // -------------------------------------------------------------------

    public VortragVerfuegbarkeit(Vortrag vortrag, Veranstaltung veranstaltung, Set<Long> verfuegbareSlotIds) {
        this(vortrag.getId(), veranstaltung.getId(), verfuegbareSlotIds);
    }

    public VortragVerfuegbarkeit(Long vortragId, Long veranstaltungId, Set<Long> verfuegbareSlotIds) {
        super(veranstaltungId, verfuegbareSlotIds);

        Objects.requireNonNull(vortragId, "vortragId darf nicht NULL sein");
        this.vortragId = vortragId;
    }

    // -------------------------------------------------------------------
    // Helper methods
    // -------------------------------------------------------------------

    public static boolean isVortragEinplanbar(Vortrag vortrag, Slot slot, Veranstaltung veranstaltung) {
        Objects.requireNonNull(vortrag, "vortrag darf nicht NULL sein");
        Objects.requireNonNull(slot, "slot darf nicht NULL sein");
        Objects.requireNonNull(veranstaltung, "veranstaltung darf nicht NULL sein");

        VortragVerfuegbarkeit verfuegbarkeit = findById(vvId(vortrag, veranstaltung));

        return verfuegbarkeit.getVerfuegbareSlotIds().contains(slot.getId());
    }
}