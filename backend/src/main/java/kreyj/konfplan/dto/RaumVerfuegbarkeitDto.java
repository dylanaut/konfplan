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
public class RaumVerfuegbarkeitDto {
    private Long raumId;
    private Long veranstaltungId;
    private Set<Long> verfuegbareSlotIds;
    public boolean isBlockedByOtherEvent;
    public String blockingEventName;
}