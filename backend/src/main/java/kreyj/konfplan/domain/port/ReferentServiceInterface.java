package kreyj.konfplan.domain.port;

import kreyj.konfplan.presentation.dto.NutzerDto;
import kreyj.konfplan.presentation.dto.ReferentVeranstaltungDto;
import kreyj.konfplan.presentation.dto.VortragDto;
import kreyj.konfplan.persistence.Referent;

import java.util.List;

public interface ReferentServiceInterface {
    Referent getProfile(String username);
    Referent updateProfile(String username, NutzerDto dto);
    List<VortragDto> getReferentVortraege(String username);
    VortragDto createVortrag(String username, VortragDto dto);
    VortragDto updateVortrag(String username, Long vortragId, VortragDto dto);
    boolean deleteVortrag(String username, Long vortragId);
    VortragDto cloneTalkForEvent(String username, Long sourceVortragId, Long targetEventId);
    List<ReferentVeranstaltungDto> getReferentVeranstaltungen(String username);
    void registerTalkForEvent(String username, Long vortragId, Long eventId);
    void deregisterTalkFromEvent(String username, Long vortragId, Long eventId);
}