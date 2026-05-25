package kreyj.konfplan.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

@Entity
@NoArgsConstructor
@Getter
@Table(uniqueConstraints = {
        @UniqueConstraint(columnNames = {"raum_id", "veranstaltung_id"})
})
public class RaumVerfuegbarkeit extends VeranstaltungsVerfuegbarkeit {
    @Column(name = "raum_id", updatable = false)
    private Long raumId;

    // -------------------------------------------------------------------
    // Konstruktoren
    // -------------------------------------------------------------------

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
        return "Raum " + raumId + " ist für " + veranstaltungId
                + " verfügbar in Slots " + verfuegbareSlotIds;
    }
}