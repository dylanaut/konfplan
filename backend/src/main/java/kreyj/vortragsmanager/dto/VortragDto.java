package kreyj.vortragsmanager.dto;

import java.util.List;

public class VortragDto extends VersionedDto {
    public Long id;
    public String titel;
    public String abstractText;
    public boolean istPflicht;
    public boolean wiederholbar;
    public List<Long> verfuegIds; // Liste der Slot-IDs

    public String pflichtgruppe;
    public int maxWiederholungen;
    public Long veranstaltungId;
    public String veranstaltungName;
    public Long referentId;
    public String referentName;
    public String referentOrganisation;
}
