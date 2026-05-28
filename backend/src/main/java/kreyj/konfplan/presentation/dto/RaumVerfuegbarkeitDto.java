package kreyj.konfplan.presentation.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import kreyj.konfplan.persistence.RaumVerfuegbarkeit;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Set;

@RegisterForReflection
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class RaumVerfuegbarkeitDto {
    private Long raumId;
    private Long veranstaltungId;
    private Set<Long> verfuegbareSlotIds;
    public boolean isBlockedByOtherEvent;
    public String blockingEventName;

    
    // -------------------------------------------------------------------
    // Konstruktoren
    // -------------------------------------------------------------------
    
    public RaumVerfuegbarkeitDto(RaumVerfuegbarkeit rv) {
        this(rv.getRaumId(), rv.getVeranstaltungId(), rv.getVerfuegbareSlotIds(), false, null);
    }
}