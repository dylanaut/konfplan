package kreyj.konfplan.adapter.in.web.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@RegisterForReflection
@NoArgsConstructor
@AllArgsConstructor
public class ZuweisungDto {
    public String teilnehmerName;
    public String vortragTitel;
    public LocalDateTime slotBeginn;
    public LocalDateTime slotEnde;
    public String raumName;
    public String gebaeudeName;
    public String referentName;
}
