package kreyj.vortragsmanager.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public class RaumBelegbarkeitDto {
    public Long raumId;
    public Long slotId;
    public boolean isBelegt;
    public boolean isBlockedByOtherEvent;
    public String blockingEventName;

    public RaumBelegbarkeitDto() {}

    public RaumBelegbarkeitDto(Long raumId, Long slotId, boolean isBelegt) {
        this.raumId = raumId;
        this.slotId = slotId;
        this.isBelegt = isBelegt;
    }
}
