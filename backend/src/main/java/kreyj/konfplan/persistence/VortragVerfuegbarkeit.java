package kreyj.konfplan.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Objects;
import java.util.Set;

@Entity
@NoArgsConstructor
@Getter
@Table(name = "VortragVerfuegbarkeit")
@IdClass(VortragVerfuegbarkeitId.class)
public class VortragVerfuegbarkeit extends VeranstaltungsVerfuegbarkeit {

    @Id
    @Column(name = "vortrag_id")
    private Long vortragId;

    public VortragVerfuegbarkeit(Vortrag vortrag, Veranstaltung veranstaltung, Set<Long> verfuegbareSlotIds) {
        this(vortrag.getId(), veranstaltung.getId(), verfuegbareSlotIds);
    }

    public VortragVerfuegbarkeit(Long vortragId, Long veranstaltungId, Set<Long> verfuegbareSlotIds) {
        super(veranstaltungId, verfuegbareSlotIds);
        Objects.requireNonNull(vortragId, "VortragId darf nicht NULL sein");
        this.vortragId = vortragId;
    }

    @Override
    public String toString() {
        return "Vortrag " + vortragId + " ist für " + veranstaltungId
                + " verfügbar in Slots " + getVerfuegbareSlotIds();
    }
}