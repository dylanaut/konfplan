package kreyj.vortragsmanager.dto;

import java.time.LocalDateTime;
import java.util.List;

public class ReferentVeranstaltungDto extends VersionedDto {
    public Long id;
    public String name;
    public LocalDateTime beginntAm;
    public LocalDateTime endetAm;

    public LocalDateTime deadlineReferenten;

    public List<Long> registeredTalkIds; // IDs of talks by this referent for this event
}
