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
@Table(name = "RaumVerfuegbarkeit")
@IdClass(RaumVerfuegbarkeitId.class)
public class RaumVerfuegbarkeit extends VeranstaltungsVerfuegbarkeit {

    @Id
    @Column(name = "raum_id")
    private Long raumId;

    public RaumVerfuegbarkeit(Raum raum, Veranstaltung veranstaltung, List<Long> verfuegbareSlotIds) {
        this(raum.getId(), veranstaltung.getId(), verfuegbareSlotIds);
    }

    public RaumVerfuegbarkeit(Long raumId, Long veranstaltungId, List<Long> verfuegbareSlotIds) {
        super(veranstaltungId, verfuegbareSlotIds);
        Objects.requireNonNull(raumId, "RaumId darf nicht NULL sein");
        this.raumId = raumId;
    }
}