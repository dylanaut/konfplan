package kreyj.konfplan.adapter.in.web.dto.templating;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public record Planungsstatistik(
        long belegtePlaetze,
        long kapazitaetTotal,
        long unerfuellte,
        long totalWuenscheErfuellt,
        long prio1,
        long prio2,
        long prio3,
        long anzahlAuffuellungen
) {
}
