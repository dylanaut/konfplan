package kreyj.konfplan.adapter.in.web.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;

import java.time.LocalDateTime;
import java.util.List;

@RegisterForReflection
public class ReferentVeranstaltungDto extends AbstractVersionedDto {
    public Long id;
    public String name;
    public LocalDateTime beginntAm;
    public LocalDateTime endetAm;
    public LocalDateTime deadlineReferenten;
    public List<Long> vortraegeIds;
    public boolean planErstellt; // Neues Feld
}
