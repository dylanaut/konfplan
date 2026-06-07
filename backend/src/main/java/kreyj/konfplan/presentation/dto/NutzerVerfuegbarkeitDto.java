package kreyj.konfplan.presentation.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import kreyj.konfplan.persistence.NutzerVerfuegbarkeit;
import lombok.NoArgsConstructor;

import java.util.Set;

@RegisterForReflection
@NoArgsConstructor
public class NutzerVerfuegbarkeitDto {
    public Long nutzerId;
    public Long veranstaltungId;
    public Set<Long> verfuegbareSlotIds;

    // -------------------------------------------------------------------
    // Konstruktoren
    // -------------------------------------------------------------------

    public NutzerVerfuegbarkeitDto(Long nutzerId, Long veranstaltungId, Set<Long> verfuegbareSlotIds) {
        this.nutzerId = nutzerId;
        this.veranstaltungId = veranstaltungId;
        this.verfuegbareSlotIds = verfuegbareSlotIds;
    }

    public NutzerVerfuegbarkeitDto(NutzerVerfuegbarkeit nv) {
        this(nv.getNutzerId(), nv.getVeranstaltungId(), nv.getVerfuegbareSlotIds());
    }
}
