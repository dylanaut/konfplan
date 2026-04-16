package kreyj.vortragsmanager.dto;

import java.util.List;

public class RefVortragDto extends VersionedDto {
    public Long id;
    public String title;
    public String abstractText;
    public String targetAudience;
    public boolean willingToRepeat;
    public List<Long> availabilities; // Liste der Slot-IDs
    
    // Bestehende Felder (aus Kompatibilitätsgründen oder falls benötigt)
    public String pflichtgruppe;
    public int maxWiederholungen;
}
