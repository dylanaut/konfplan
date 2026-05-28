package kreyj.konfplan.presentation.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;

import java.time.LocalDateTime;

@RegisterForReflection
public class SlotDto extends AbstractVersionedDto {
    public String description;

    public LocalDateTime startTime;

    public LocalDateTime endTime;

    public Long veranstaltungId;
}
