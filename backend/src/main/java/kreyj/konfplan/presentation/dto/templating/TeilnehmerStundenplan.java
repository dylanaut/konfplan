package kreyj.konfplan.presentation.dto.templating;

import io.quarkus.runtime.annotations.RegisterForReflection;
import kreyj.konfplan.presentation.dto.TeilnehmerDto;
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
