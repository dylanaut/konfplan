package kreyj.konfplan.adapter.in.web.dto.templating;

import io.quarkus.runtime.annotations.RegisterForReflection;
import kreyj.konfplan.adapter.in.web.dto.NutzerDto;
import kreyj.konfplan.adapter.in.web.dto.RaumDto;
import kreyj.konfplan.adapter.in.web.dto.SlotDto;
import kreyj.konfplan.adapter.in.web.dto.VeranstaltungDto;
import kreyj.konfplan.adapter.in.web.dto.VortragDto;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;
import java.util.Map;

@RegisterForReflection
@AllArgsConstructor
@Getter
public class PrioReport {
    private final VeranstaltungDto veranstaltung;
    private final Map<Long, SlotDto> slots;
    private final Map<Long, RaumDto> raeume;
    private final int[][] instanz_raum;
    private final int[][] instanz_slot;
    private final List<Integer> num_instanzen_pro_wv;
    private final List<TeilnehmerErfuellung> teilnehmer_erfuellung;
    private final Map<Long, VortragDto> wv_dict;
    private final Map<Long, NutzerDto> ref_dict;
    private final List<String> gruppen;
    private final String geplantAm;

}
