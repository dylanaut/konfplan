package kreyj.konfplan.presentation.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@RegisterForReflection
@NoArgsConstructor
@AllArgsConstructor
public class ZuweisungDto {
    public String teilnehmerName;
    public String vortragTitel;
    @Getter
    public String slotZeit;
    public String raumName;
    public String gebaeudeName;
}
