package kreyj.konfplan.adapter.in.web.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@RegisterForReflection
@NoArgsConstructor
@AllArgsConstructor
public class ReferentVortragDto {
    public String vortragTitel;
    public LocalDateTime slotBeginn;
    public LocalDateTime slotEnde;
    public String raumName;
    public String gebaeudeName;
    public String referentName;
    public String referentOrganisation;
    public List<TeilnehmerDto> teilnehmer;
}
