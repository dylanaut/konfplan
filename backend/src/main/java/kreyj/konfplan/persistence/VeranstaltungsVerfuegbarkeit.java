package kreyj.konfplan.persistence;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

@MappedSuperclass
@NoArgsConstructor
@Getter
public abstract class VeranstaltungsVerfuegbarkeit extends PanacheEntityBase {
    @Id
    @Column(name = "veranstaltung_id")
    protected Long veranstaltungId;

    Set<Long> verfuegbareSlotIds = new HashSet<>();

    public Set<Long> getVerfuegbareSlotIds() {
        return Collections.unmodifiableSet(verfuegbareSlotIds);
    }

    public void  setVerfuegbareSlotIds(Set<Long> verfuegbareSlotIds) {
        if (null == verfuegbareSlotIds) {
            this.verfuegbareSlotIds = Collections.emptySet();
        } else {
            this.verfuegbareSlotIds = new HashSet<>(verfuegbareSlotIds);
        }
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

    public boolean addSlot(Slot slot) {
        Objects.requireNonNull(slot);

        return addSlot(slot.getId());
    }

    public boolean addSlot(Long slotId) {
        Objects.requireNonNull(slotId);

        return verfuegbareSlotIds.add(slotId);
    }

    public boolean removeSlot(Slot slot) {
        Objects.requireNonNull(slot);

        return removeSlot(slot.getId());
    }

    public boolean removeSlot(Long slotId) {
        Objects.requireNonNull(slotId);

        return verfuegbareSlotIds.remove(slotId);
    }

    public boolean isVerfuegbar(Slot slot) {
        Objects.requireNonNull(slot);
        return isVerfuegbar(slot.getId());
    }

    public boolean isVerfuegbar(Long slotId) {
        Objects.requireNonNull(slotId);
        return verfuegbareSlotIds.contains(slotId);
    }
}
