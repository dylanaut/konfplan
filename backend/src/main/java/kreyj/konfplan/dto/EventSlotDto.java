package kreyj.konfplan.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;

import java.time.LocalDateTime;

@RegisterForReflection
public class EventSlotDto extends AbstractVersionedDto {
    public String description;

    public LocalDateTime startTime;

    public LocalDateTime endTime;

    public Long veranstaltungId;
}
