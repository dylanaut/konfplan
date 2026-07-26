package kreyj.konfplan.adapter.in.web.dto.templating;

import io.quarkus.runtime.annotations.RegisterForReflection;
import kreyj.konfplan.adapter.in.web.dto.TeilnehmerDto;

import java.util.Map;

@RegisterForReflection

public record TeilnehmerErfuellung(
        TeilnehmerDto teilnehmer,
        // keyed by wv.Oid
        Map<Long, WahlvortragStatus> wvStatuus
) {
}
