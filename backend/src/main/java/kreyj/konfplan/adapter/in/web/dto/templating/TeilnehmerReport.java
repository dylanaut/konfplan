package kreyj.konfplan.adapter.in.web.dto.templating;

import io.quarkus.runtime.annotations.RegisterForReflection;
import kreyj.konfplan.adapter.in.web.dto.SlotDto;
import kreyj.konfplan.adapter.in.web.dto.TeilnehmerDto;
import kreyj.konfplan.adapter.in.web.dto.VeranstaltungDto;
import lombok.AllArgsConstructor;

import java.util.List;
import java.util.Map;

@RegisterForReflection
public record TeilnehmerReport(VeranstaltungDto veranstaltung, TeilnehmerDto teilnehmer, Map<Long, SlotDto> slots,
                               List<TeilnehmerStundenplan> teilnehmer_stundenplan, List<String> gruppen) {
}
