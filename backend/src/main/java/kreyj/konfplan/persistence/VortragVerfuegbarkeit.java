package kreyj.konfplan.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;
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
// -------------------------------------------------------------------
// Konstruktoren
// -------------------------------------------------------------------

    public VortragVerfuegbarkeit(Vortrag vortrag, Veranstaltung veranstaltung, List<Long> verfuegbareSlotIds) {
        this(vortrag.getId(), veranstaltung.getId(), verfuegbareSlotIds);
    }

    public VortragVerfuegbarkeit(Long vortragId, Long veranstaltungId, List<Long> verfuegbareSlotIds) {
        super(veranstaltungId, verfuegbareSlotIds);

        Objects.requireNonNull(vortragId, "vortragId darf nicht NULL sein");
        this.vortragId = vortragId;
    }
}