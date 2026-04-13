package kreyj.vortragsmanager.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import java.util.List;

@RegisterForReflection
public class RaumBelegungDto {
    public String raumName;
    public String gebaeudeName;
    public List<VortragBelegungDto> eintraege; // Vorträge in diesem Raum

    public RaumBelegungDto() {}
}
