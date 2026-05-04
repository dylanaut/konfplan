package kreyj.vortragsmanager.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public class AdminPrioritaetUpdateRequestDto {
    public Long vortragId;
    public int prioWert;

    public AdminPrioritaetUpdateRequestDto() {
    }

    public AdminPrioritaetUpdateRequestDto(Long vortragId, int prioWert) {
        this.vortragId = vortragId;
        this.prioWert = prioWert;
    }
}
