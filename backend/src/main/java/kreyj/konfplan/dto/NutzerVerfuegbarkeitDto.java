package kreyj.konfplan.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
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
public class NutzerVerfuegbarkeitDto {
    private Long nutzerId;
    private Long veranstaltungId;
    private Set<Long> verfuegbareSlotIds;
}