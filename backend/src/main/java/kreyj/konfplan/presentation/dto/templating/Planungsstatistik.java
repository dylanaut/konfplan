package kreyj.konfplan.presentation.dto.templating;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection

public record Planungsstatistik(
        long belegte_plaetze,
        long kapazitaet_total,
        long unerfuellte,
        long total_wuensche_erfuellt,
        long prio1,
        long prio2,
        long prio3,
        long anzahl_auffuellung
) {
}
