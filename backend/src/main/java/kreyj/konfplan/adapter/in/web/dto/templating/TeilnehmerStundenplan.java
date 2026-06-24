package kreyj.konfplan.adapter.in.web.dto.templating;

import io.quarkus.runtime.annotations.RegisterForReflection;
import kreyj.konfplan.adapter.in.web.dto.TeilnehmerDto;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Map;

@RegisterForReflection
@AllArgsConstructor
@Getter
public class TeilnehmerStundenplan {
    private final TeilnehmerDto teilnehmer;
    private final Map<Long, TeilnehmerSlotBelegung> tnSlotBelegungen;
}
