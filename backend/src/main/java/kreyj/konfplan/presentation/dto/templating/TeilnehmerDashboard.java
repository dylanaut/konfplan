package kreyj.konfplan.presentation.dto.templating;

import io.quarkus.runtime.annotations.RegisterForReflection;
import kreyj.konfplan.presentation.dto.SlotDto;

import java.util.List;
import java.util.Map;
import java.util.Set;

@RegisterForReflection
public record TeilnehmerDashboard(
        TeilnehmerInfoDto teilnehmer,
        Map<Long, SlotDto> slots,
        List<TeilnehmerStundenplan> teilnehmer_stundenplan,
        List<String> gruppen
) {
    public record TeilnehmerInfoDto(
            String vorname,
            String nachname,
            Set<String> gruppen
    ) {}
}
