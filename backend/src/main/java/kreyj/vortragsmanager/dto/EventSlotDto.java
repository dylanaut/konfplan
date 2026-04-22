package kreyj.vortragsmanager.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;

import java.time.LocalDateTime;

@RegisterForReflection
public class EventSlotDto extends VersionedDto {
    public String description;

    public LocalDateTime startTime;

    public LocalDateTime endTime;

    public Long veranstaltungId;
}
