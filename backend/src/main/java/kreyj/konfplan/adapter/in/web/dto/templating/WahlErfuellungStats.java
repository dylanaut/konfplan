package kreyj.konfplan.adapter.in.web.dto.templating;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.Getter;

import java.util.Map;
import java.util.stream.Collectors;

@RegisterForReflection
@Getter
public class WahlErfuellungStats {
    private final int totalPrefs;
    private final int erfuellungenGesamt;
    private final String gesamtErfuellungenProzentual;

    private final Map<Long, Integer> wvGewaehlte;

    private final Map<Long, Integer> wvErfuellungen;
    private final Map<Long, String> wvErfuellungenProzentual;

    private final Map<Integer, Integer> prioPrefs;
    private final Map<Integer, Integer> prioErfuellungen;
    private final Map<Integer, String> prioErfuellungenProzentual;

    public WahlErfuellungStats(int totalPrefs, Map<Integer, Integer> prioPrefs, Map<Long, Integer> wvGewaehlte,
                               int erfuellungenGesamt, Map<Integer, Integer> prioErfuellungen, Map<Long, Integer> wvErfuellungen) {
        this.totalPrefs = totalPrefs;
        this.prioPrefs = prioPrefs;
        this.wvGewaehlte = wvGewaehlte;
        this.erfuellungenGesamt = erfuellungenGesamt;
        this.prioErfuellungen = prioErfuellungen;
        this.wvErfuellungen = wvErfuellungen;

        this.gesamtErfuellungenProzentual =
                String.format("%.1f%%", ((erfuellungenGesamt / (totalPrefs > 0 ? totalPrefs : 1.0f)) * 100));

        this.wvErfuellungenProzentual = wvGewaehlte.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey,
                        entry -> {
                            int gewaehlte = entry.getValue();
                            int wv_erfuellung = wvErfuellungen.getOrDefault(entry.getKey(), 0);
                            return String.format("%.1f%%",
                                    ((wv_erfuellung / (gewaehlte > 0 ? gewaehlte : 1.0f)) * 100));
                        }));

        this.prioErfuellungenProzentual = prioErfuellungen.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey,
                        entry -> {
                            int gewaehlte = entry.getValue();
                            int wv_erfuellung = prioErfuellungen.getOrDefault(entry.getKey(), 0);
                            return String.format("%.1f%%",
                                    ((wv_erfuellung / (gewaehlte > 0 ? gewaehlte : 1.0f)) * 100));
                        }));
    }
}
