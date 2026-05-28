package kreyj.konfplan.presentation.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.util.List;

@RegisterForReflection
@NoArgsConstructor
@AllArgsConstructor
public class RaumplanEintragDto {
    public Long slotId;
    public String slotZeit;
    public String vortragTitel;
    public String referentName;
    public String vortragTyp;
    public int teilnehmerCount;
    public List<TeilnehmerSimpleDto> teilnehmer; // Für Anwesenheitsliste
}
