package kreyj.konfplan.adapter.in.web.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@RegisterForReflection
@NoArgsConstructor
@AllArgsConstructor
public class TeilnehmerAktuelleZuweisungDto {
    public Long wahlvortragId;
    public int instanzIndex;
    public String vortragTitel;
    public String slotZeit;
    public String raumName;
}
