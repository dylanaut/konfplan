package kreyj.vortragsmanager.dto;

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
