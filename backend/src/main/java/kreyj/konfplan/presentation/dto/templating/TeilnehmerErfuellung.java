package kreyj.konfplan.presentation.dto.templating;

import io.quarkus.runtime.annotations.RegisterForReflection;
import kreyj.konfplan.presentation.dto.TeilnehmerDto;

import java.util.Map;

@RegisterForReflection

public record TeilnehmerErfuellung(
        TeilnehmerDto teilnehmer,
        // keyed by wv.Oid
        Map<Long, WahlvortragStatus> wvStatuus
) {
}
