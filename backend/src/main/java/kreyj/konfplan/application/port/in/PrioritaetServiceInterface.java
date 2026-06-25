package kreyj.konfplan.application.port.in;

import kreyj.konfplan.adapter.in.web.dto.PrioritaetRequest;

import java.util.List;
import java.util.Map;

public interface PrioritaetServiceInterface {

    void savePrioritaeten(String email, List<PrioritaetRequest> requests);

    void updateSinglePrioritaet(Long userId, Long vortragId, int prio);

    Map<Long, Integer> getVortragPrioritaeten(Long nutzerId, Long veranstaltungId);
}
