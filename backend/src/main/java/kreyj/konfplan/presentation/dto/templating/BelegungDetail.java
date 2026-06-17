package kreyj.konfplan.presentation.dto.templating;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.util.List;

@RegisterForReflection
@NoArgsConstructor
@AllArgsConstructor
public class BelegungDetail {
    public String titel;
    public String referent;
    public String organisation;
    public boolean isPflicht;
    public List<String> teilnehmer;
    public int anzahl;
}
