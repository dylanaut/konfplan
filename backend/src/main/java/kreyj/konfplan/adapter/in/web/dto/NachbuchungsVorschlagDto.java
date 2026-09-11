package kreyj.konfplan.adapter.in.web.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.util.List;

@RegisterForReflection
@NoArgsConstructor
@AllArgsConstructor
public class NachbuchungsVorschlagDto {
    public Long slotId;
    public String slotZeit;
    public List<UmbuchungOptionDto> optionen;
}
