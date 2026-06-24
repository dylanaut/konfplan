package kreyj.konfplan.application.port.in;

import kreyj.konfplan.persistence.Prioritaet;
import kreyj.konfplan.adapter.in.web.dto.PrioritaetRequest;

import java.util.List;
import java.util.Map;

public interface PrioritaetServiceInterface {

    void savePrioritaeten(String email, List<PrioritaetRequest> requests);

    void updateSinglePrioritaet(Long userId, Long vortragId, int prioWert);

    List<Prioritaet> getNutzerPrioritaeten(String email);

    List<Prioritaet> getNutzerPrioritaeten(Long userId);

    Map<Long, Integer> getVortragPrioritaeten(Long nutzerId, Long veranstaltungId);
}
