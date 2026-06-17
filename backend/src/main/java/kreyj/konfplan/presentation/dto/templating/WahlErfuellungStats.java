package kreyj.konfplan.presentation.dto.templating;

import io.quarkus.runtime.annotations.RegisterForReflection;

import java.util.Map;

@RegisterForReflection

public record WahlErfuellungStats(
        int total_prefs,
        Map<Integer, Integer> prio_prefs,
        Map<Long, Integer> wv_prefs,
        int total_fillups,
        Map<Integer, Integer> prio_fillups,
        Map<Long, Integer> wv_fillups
) {
}
