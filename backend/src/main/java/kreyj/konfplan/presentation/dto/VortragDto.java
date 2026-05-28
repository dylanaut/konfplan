package kreyj.konfplan.presentation.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;

import java.util.HashSet;
import java.util.Set;

@RegisterForReflection
public class VortragDto extends AbstractVersionedDto {
    public Long id;
    public String titel;
    public String inhalt;
    public boolean istPflicht;
    public boolean wiederholbar;
    public Set<Long> verfuegbareSlotIds = new HashSet<>();

    public String pflichtgruppe;
    public Long pflichtSlotId;
    public Long pflichtRaumId;
    public int maxWiederholungen;
    public Long veranstaltungId;
    public String veranstaltungName;
    public Long referentId;
    public String referentName;
    public String referentOrganisation;
}
