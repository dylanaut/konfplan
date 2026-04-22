package kreyj.vortragsmanager.dto;

import java.util.List;

public class RefVortragDto extends VersionedDto {
    public Long id;
    public String titel;
    public String abstractText;
    public String zielgruppe;
    public boolean wiederholbar;
    public List<Long> verfuegIds; // Liste der Slot-IDs

    // Bestehende Felder (aus Kompatibilitätsgründen oder falls benötigt)
    public String pflichtgruppe;
    public int maxWiederholungen;
    public Long veranstaltungId;
    public String veranstaltungName;
}
