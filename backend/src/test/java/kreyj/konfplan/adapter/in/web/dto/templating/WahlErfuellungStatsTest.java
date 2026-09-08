package kreyj.konfplan.adapter.in.web.dto.templating;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression: prioErfuellungenProzentual lieferte immer "100.0%", weil die Prozentberechnung
 * fälschlich prioErfuellungen (erfüllte Wünsche) mit sich selbst statt mit prioPrefs (gewählte
 * Wünsche) ins Verhältnis setzte.
 */
class WahlErfuellungStatsTest {

    @Test
    void prioErfuellungenProzentual_beruecksichtigtNichtErfuellteWuensche() {
        Map<Integer, Integer> prioPrefs = Map.of(10, 4);
        Map<Integer, Integer> prioErfuellungen = Map.of(10, 1);

        WahlErfuellungStats stats = new WahlErfuellungStats(
                4, prioPrefs, Map.of(), 1, prioErfuellungen, Map.of());

        assertThat(stats.getPrioErfuellungenProzentual().get(10)).isEqualTo(String.format("%.1f%%", 25.0f));
    }
}
