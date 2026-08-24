package kreyj.konfplan.adapter.in.web.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;

import java.time.LocalDateTime;
import java.util.List;

@RegisterForReflection
public class TeilnehmerVeranstaltungDto {
    public Long id;
    public String name;
    public LocalDateTime beginntAm;
    public LocalDateTime endetAm;
    public LocalDateTime deadlineTeilnehmer;
    public Integer maxPrioritaeten;
    public boolean planErstellt;
    public String logo;
    public String logo_link;
    public List<String> organisatorNamen;
    public List<OrganisatorDto> organisatoren;
}
