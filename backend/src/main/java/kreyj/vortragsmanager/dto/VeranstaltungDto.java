package kreyj.vortragsmanager.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;

import java.time.LocalDateTime;
import java.util.List;

@RegisterForReflection
public class VeranstaltungDto extends VersionedDto {
    public String name;
    public LocalDateTime beginntAm;
    public LocalDateTime endetAm;

    public String logo;
    public String logo_link;
    public Long organisatorId;
    public String organisatorName;
    public List<GebaeudeSimpleDto> gebaeude;

    public VeranstaltungDto() {
    }
}
