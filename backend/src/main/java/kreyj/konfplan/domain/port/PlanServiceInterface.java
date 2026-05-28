package kreyj.konfplan.domain.port;

import kreyj.konfplan.presentation.dto.RaumBelegungUebersichtDto;
import kreyj.konfplan.presentation.dto.ReferentVortragDto;
import kreyj.konfplan.presentation.dto.ZuweisungDto;

import java.util.List;

public interface PlanServiceInterface {
    List<ZuweisungDto> getPlanFuerTeilnehmer(String username, Long veranstaltungId);
    List<ReferentVortragDto> getPlanFuerReferent(String username, Long veranstaltungId);
    List<RaumBelegungUebersichtDto> getDetaillierterPlan(Long veranstaltungId);
}