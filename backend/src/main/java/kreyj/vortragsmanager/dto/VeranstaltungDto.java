package kreyj.vortragsmanager.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@RegisterForReflection
public class VeranstaltungDto extends VersionedDto {
    public String name;
    public LocalDateTime beginntAm;
    public LocalDateTime endetAm;

    public String logo;
    public String logo_link;
    public List<Long> organisatorIds = new ArrayList<>();
    public List<String> organisatorNamen = new ArrayList<>();
    public List<GebaeudeSimpleDto> gebaeude = new ArrayList<>();

    public VeranstaltungDto() {
    }
}
