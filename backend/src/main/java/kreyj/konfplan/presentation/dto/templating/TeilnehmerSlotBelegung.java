package kreyj.konfplan.presentation.dto.templating;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection

public record TeilnehmerSlotBelegung(
        String titel,
        String raum,
        String typ // "frei", "pflicht", "wahl", "auffuellung", "abwesend"
) {
}
