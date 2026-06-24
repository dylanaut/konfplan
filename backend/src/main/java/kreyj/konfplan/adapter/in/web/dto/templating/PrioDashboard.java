package kreyj.konfplan.adapter.in.web.dto.templating;

import io.quarkus.runtime.annotations.RegisterForReflection;
import kreyj.konfplan.adapter.in.web.dto.NutzerDto;
import kreyj.konfplan.adapter.in.web.dto.RaumDto;
import kreyj.konfplan.adapter.in.web.dto.SlotDto;
import kreyj.konfplan.adapter.in.web.dto.VortragDto;

import java.util.List;
import java.util.Map;

@RegisterForReflection

public record PrioDashboard(
        Map<Long, SlotDto> slots,
        Map<Long, RaumDto> raeume,
        int[][] instanz_raum,
        int[][] instanz_slot,
        List<Integer> num_instanzen_pro_wv,
        List<TeilnehmerErfuellung> teilnehmer_erfuellung,
        Map<Long, VortragDto> wv_dict,
        Map<Long, NutzerDto> ref_dict,
        List<String> gruppen,
        String geplantAm
) {
}
