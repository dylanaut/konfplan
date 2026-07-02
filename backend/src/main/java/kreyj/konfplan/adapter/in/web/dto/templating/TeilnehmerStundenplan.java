package kreyj.konfplan.adapter.in.web.dto.templating;

import io.quarkus.runtime.annotations.RegisterForReflection;
import kreyj.konfplan.adapter.in.web.dto.TeilnehmerDto;
import lombok.AllArgsConstructor;

import java.util.Map;

@RegisterForReflection
public record TeilnehmerStundenplan(TeilnehmerDto teilnehmer, Map<Long, TeilnehmerSlotBelegung> tnSlotBelegungen) {
}
