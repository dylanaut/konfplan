package kreyj.vortragsmanager.dto;

import java.util.List;

public class RefVortragDto extends VersionedDto {
    public Long id;
    public String title;
    public String abstractText;
    public boolean wiederholbar;
    public List<Long> availabilities; // Liste der Slot-IDs
    
    public String pflichtgruppe;
    public int maxWiederholungen;

    public Long veranstaltungId;
    public String veranstaltungName;
}
