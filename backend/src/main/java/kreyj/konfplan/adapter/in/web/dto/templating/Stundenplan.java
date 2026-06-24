package kreyj.konfplan.adapter.in.web.dto.templating;

import io.quarkus.runtime.annotations.RegisterForReflection;
import kreyj.konfplan.adapter.in.web.dto.RaumDto;
import kreyj.konfplan.adapter.in.web.dto.SlotDto;
import kreyj.konfplan.adapter.in.web.dto.VortragDto;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;
import java.util.Map;


@RegisterForReflection
@AllArgsConstructor
@Getter
public class Stundenplan {
    private final Map<Long, SlotDto> slots;
    private final Map<Long, RaumDto> raeume;
    private final Map<String, BelegungDetail> belegung_details;
    private final Map<Long, List<String>> freieTnProSlot;
    private final Planungsstatistik stats;
    private final WahlErfuellungStats wahlErfuellungStats;
    private final Map<Long, VortragDto> wahlvortraege;
    private final String geplantAm;
}
