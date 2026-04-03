package kreyj.vortragsmanager.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import java.time.LocalDateTime;

@RegisterForReflection
public class VeranstaltungDto {
    public Long id;
    public String name;
    public LocalDateTime beginntAm;
    public LocalDateTime endetAm;
    public String ort;
    public String logo;
    public String logo_link;
    public Long organisatorId;
    public String organisatorName; // Bequemlichkeit für das Frontend
    public Long version;

    public VeranstaltungDto() {}
}
