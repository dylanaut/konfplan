package kreyj.konfplan.domain.port;

import kreyj.konfplan.presentation.dto.PrioritaetRequest;
import kreyj.konfplan.persistence.Prioritaet;

import java.util.List;

public interface PrioritaetServiceInterface {
    List<Prioritaet> getPrioritaetenForUser(String username);
    void savePrioritaeten(String username, List<PrioritaetRequest> requests);
    void updateSinglePrioritaet(Long teilnehmerId, Long vortragId, int prioWert);
}