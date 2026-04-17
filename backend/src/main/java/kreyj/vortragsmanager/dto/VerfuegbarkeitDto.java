package kreyj.vortragsmanager.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public class VerfuegbarkeitDto {
    public Long userId;
    public Long slotId;
    public boolean isAvailable;

    public VerfuegbarkeitDto() {}
    public VerfuegbarkeitDto(Long userId, Long slotId, boolean isAvailable) {
        this.userId = userId;
        this.slotId = slotId;
        this.isAvailable = isAvailable;
    }
}
