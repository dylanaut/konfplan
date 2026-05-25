package kreyj.konfplan.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@RegisterForReflection
@NoArgsConstructor
@AllArgsConstructor
public class VerfuegbarkeitDto {
    public Long userId;
    public Long slotId;
    public boolean isAvailable;
}
