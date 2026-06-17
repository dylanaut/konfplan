package kreyj.konfplan.presentation.dto.templating;

import io.quarkus.runtime.annotations.RegisterForReflection;
import kreyj.konfplan.presentation.dto.SlotDto;

import java.util.List;
import java.util.Map;

@RegisterForReflection

public record TeilnehmerDashboard(
        Map<Long, SlotDto> slots,
        List<TeilnehmerStundenplan> teilnehmer_stundenplan,
        List<String> gruppen
) {
}
