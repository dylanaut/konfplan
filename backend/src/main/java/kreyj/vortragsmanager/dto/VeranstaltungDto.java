package kreyj.vortragsmanager.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import kreyj.vortragsmanager.entity.Gebaeude;

import java.time.LocalDateTime;
import java.util.List;

@RegisterForReflection
public class VeranstaltungDto {
    public Long id;
    public String name;
    public LocalDateTime beginntAm;
    public LocalDateTime endetAm;
    // 'ort' wurde entfernt
    public String logo;
    public String logo_link;
    public Long organisatorId;
    public String organisatorName;
    public List<GebaeudeSimpleDto> gebaeude;
    public Long version;

    public VeranstaltungDto() {}
}
