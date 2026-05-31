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
@Table(name = "NutzerVerfuegbarkeit")
@IdClass(NutzerVerfuegbarkeitId.class)
public class NutzerVerfuegbarkeit extends VeranstaltungsVerfuegbarkeit {
    @Id
    @Column(name = "nutzer_id")
    private Long nutzerId;

    public NutzerVerfuegbarkeit(Nutzer nutzer, Veranstaltung veranstaltung, List<Long> verfuegbareSlotIds) {
        this(nutzer.getId(), veranstaltung.getId(), verfuegbareSlotIds);
    }

    public NutzerVerfuegbarkeit(Long nutzerId, Long veranstaltungId, List<Long> verfuegbareSlotIds) {
        super(veranstaltungId, verfuegbareSlotIds);
        Objects.requireNonNull(nutzerId, "nutzerId darf nicht NULL sein");
        this.nutzerId = nutzerId;
    }
}