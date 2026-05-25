package kreyj.konfplan.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Objects;
import java.util.Set;

@Entity
@NoArgsConstructor
@Getter
@Table(uniqueConstraints = {
        @UniqueConstraint(columnNames = {"nutzer_id", "veranstaltung_id"})
})
public class NutzerVerfuegbarkeit extends VeranstaltungsVerfuegbarkeit {
    @Column(name = "nutzer_id", updatable = false)
    private Long nutzerId;

    // -------------------------------------------------------------------
    // Konstruktoren
    // -------------------------------------------------------------------

    public NutzerVerfuegbarkeit(Nutzer nutzer, Veranstaltung veranstaltung, Set<Long> verfuegbareSlotIds) {
        this(nutzer.getId(), veranstaltung.getId(), verfuegbareSlotIds);
    }

    public NutzerVerfuegbarkeit(Long nutzerId, Long veranstaltungId, Set<Long> verfuegbareSlotIds) {
        super(veranstaltungId, verfuegbareSlotIds);
        Objects.requireNonNull(nutzerId, "NutzerId darf nicht NULL sein");

        this.nutzerId = nutzerId;
    }

    // -------------------------------------------------------------------
    // Overrides
    // -------------------------------------------------------------------

    @Override
    public String toString() {
        return "Nutzer " + nutzerId + " ist für " + veranstaltungId
                + " verfügbar in Slots " + verfuegbareSlotIds;
    }

}