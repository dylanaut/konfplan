package kreyj.konfplan.adapter.in.web.dto.templating;

import io.quarkus.runtime.annotations.RegisterForReflection;
import kreyj.konfplan.adapter.in.web.dto.SlotDto;
import kreyj.konfplan.adapter.in.web.dto.TeilnehmerDto;
import kreyj.konfplan.adapter.in.web.dto.VeranstaltungDto;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;
import java.util.Map;

@RegisterForReflection
@AllArgsConstructor
@Getter
public class TeilnehmerReport {
    private final VeranstaltungDto veranstaltung;
    private final TeilnehmerDto teilnehmer;
    private final Map<Long, SlotDto> slots;
    private final List<TeilnehmerStundenplan> teilnehmer_stundenplan;
    private final List<String> gruppen;
}
