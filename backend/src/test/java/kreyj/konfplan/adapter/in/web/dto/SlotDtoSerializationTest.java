package kreyj.konfplan.adapter.in.web.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression für #169: Reports (Teilnehmer-Zuordnungen, Stundenplan, Prioritaeten) zeigten statt
 * der Zeitangaben nur ", -" in der Kopfzeile, weil das reale JSON die virtuellen
 * @JsonProperty-Methoden tag()/start()/ende()/zeitraum() nicht enthielt - verifiziert mit dem
 * echten, per CDI injizierten ObjectMapper (derselbe, den auch die REST-Endpunkte verwenden).
 */
@QuarkusTest
class SlotDtoSerializationTest {

    @Inject
    ObjectMapper objectMapper;

    @Test
    void serialisiertVirtuelleZeitfelder() throws Exception {
        SlotDto slot = new SlotDto();
        slot.id = 500L;
        slot.version = 0L;
        slot.description = "Slot 1";
        slot.startTime = LocalDateTime.of(2026, 9, 16, 9, 0);
        slot.endTime = LocalDateTime.of(2026, 9, 16, 9, 45);
        slot.veranstaltungId = 1L;

        String json = objectMapper.writeValueAsString(slot);
        JsonNode node = objectMapper.readTree(json);

        assertThat(node.has("tag")).as("tag fehlt im JSON: %s", json).isTrue();
        assertThat(node.get("start").asText()).isEqualTo("09:00");
        assertThat(node.get("ende").asText()).isEqualTo("09:45");
        assertThat(node.get("zeitraum").asText()).isEqualTo("09:00 - 09:45");
    }
}
