package kreyj.konfplan.presentation.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import kreyj.konfplan.persistence.NutzerVerfuegbarkeit;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.Set;

@RegisterForReflection
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class NutzerVerfuegbarkeitDto {
    private Long nutzerId;
    private Long veranstaltungId;
    private List<Long> verfuegbareSlotIds;

    // -------------------------------------------------------------------
    // Konstruktoren
    // -------------------------------------------------------------------

    public NutzerVerfuegbarkeitDto(NutzerVerfuegbarkeit nv) {
        this(nv.getNutzerId(), nv.getVeranstaltungId(), nv.getVerfuegbareSlotIds());
    }
}
