package kreyj.konfplan.application.port.in;

import kreyj.konfplan.adapter.in.web.dto.VortragPrioDto;

import java.util.List;
import java.util.Map;

public interface PrioritaetServiceInterface {

    void savePrioritaeten(String loginName, List<VortragPrioDto> requests);

    void updateSinglePrioritaet(Long userId, Long vortragId, int prioWert);

    Map<Long, Integer> getVortragPrioritaeten(Long nutzerId, Long veranstaltungId);
}
