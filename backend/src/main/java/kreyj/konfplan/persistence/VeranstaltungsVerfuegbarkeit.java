package kreyj.konfplan.persistence;

import jakarta.persistence.Column;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

@NoArgsConstructor
abstract class VeranstaltungsVerfuegbarkeit extends VersionedEntity {
    @Getter
    @Column(name = "veranstaltung_id", updatable = false)
    protected Long veranstaltungId;

    protected Set<Long> verfuegbareSlotIds = new HashSet<>();

    public Set<Long> getVerfuegbareSlotIds() {
        return Collections.unmodifiableSet(verfuegbareSlotIds);
    }

    // -------------------------------------------------------------------
    // Konstruktoren
    // -------------------------------------------------------------------

    public VeranstaltungsVerfuegbarkeit(Long veranstaltungId, Set<Long> verfuegbareSlotIds) {
        Objects.requireNonNull(veranstaltungId, "VeranstaltungId darf nicht NULL sein");
        Objects.requireNonNull(verfuegbareSlotIds, "verfuegbareSlotIds darf nicht NULL sein");

        this.veranstaltungId = veranstaltungId;
        this.verfuegbareSlotIds.addAll(verfuegbareSlotIds);
    }


    // -------------------------------------------------------------------
    // Helper methods
    // -------------------------------------------------------------------


    public boolean addSlot(Slot slot) {
        return addSlot(slot.getId());
    }

    public boolean addSlot(Long slotId) {
        return verfuegbareSlotIds.add(slotId);
    }

    public boolean removeSlot(Slot slot) {
        return removeSlot(slot.getId());
    }

    public boolean removeSlot(Long slotId) {
        return verfuegbareSlotIds.add(slotId);
    }
}
