package kreyj.konfplan.presentation.dto.templating;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.Getter;

import java.util.Map;
import java.util.stream.Collectors;

@RegisterForReflection
@Getter
public class WahlErfuellungStats {
    private final int total_prefs;
    private final int erfuellungen_gesamt;
    private final String gesamt_erfuellungen_prozentual;

    private final Map<Long, Integer> wv_gewaehlte;

    private final Map<Long, Integer> wv_erfuellungen;
    private final Map<Long, String> wv_erfuellungen_prozentual;

    private final Map<Integer, Integer> prio_prefs;
    private final Map<Integer, Integer> prio_erfuellungen;
    private final Map<Integer, String> prio_erfuellungen_prozentual;

    public WahlErfuellungStats(int total_prefs, Map<Integer, Integer> prio_prefs, Map<Long, Integer> wv_gewaehlte,
                               int erfuellungen_gesamt, Map<Integer, Integer> prio_erfuellungen, Map<Long, Integer> wv_erfuellungen) {
        this.total_prefs = total_prefs;
        this.prio_prefs = prio_prefs;
        this.wv_gewaehlte = wv_gewaehlte;
        this.erfuellungen_gesamt = erfuellungen_gesamt;
        this.prio_erfuellungen = prio_erfuellungen;
        this.wv_erfuellungen = wv_erfuellungen;

        this.gesamt_erfuellungen_prozentual =
                String.format("%.1f%%", ((erfuellungen_gesamt / (total_prefs > 0 ? total_prefs : 1.0f)) * 100));

        this.wv_erfuellungen_prozentual = wv_gewaehlte.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey,
                        entry -> {
                            int gewaehlte = entry.getValue();
                            int wv_erfuellung = wv_erfuellungen.getOrDefault(entry.getKey(), 0);
                            return String.format("%.1f%%",
                                    ((wv_erfuellung / (gewaehlte > 0 ? gewaehlte : 1.0f)) * 100));
                        }));

        this.prio_erfuellungen_prozentual = prio_erfuellungen.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey,
                        entry -> {
                            int gewaehlte = entry.getValue();
                            int wv_erfuellung = wv_erfuellungen.getOrDefault(entry.getKey(), 0);
                            return String.format("%.1f%%",
                                    ((wv_erfuellung / (gewaehlte > 0 ? gewaehlte : 1.0f)) * 100));
                        }));
    }
}
