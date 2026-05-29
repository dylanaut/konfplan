package kreyj.konfplan.domain.port;

import kreyj.konfplan.presentation.dto.NutzerDto;
import kreyj.konfplan.presentation.dto.VortragDto;
import kreyj.konfplan.presentation.dto.VortragStatDto;
import kreyj.konfplan.persistence.Nutzer;
import kreyj.konfplan.persistence.Slot;
import kreyj.konfplan.persistence.Vortrag;
import jakarta.ws.rs.core.Response;

import java.nio.file.Path;
import java.util.List;

public interface AdminServiceInterface {
    List<NutzerDto> getAllUsers();
    List<NutzerDto> getAllUsers(Long veranstaltungId);
    Nutzer findNutzer(Long id);
    NutzerDto createUser(NutzerDto dto, List<Long> veranstaltungsIds);
    NutzerDto updateUser(Long id, NutzerDto dto, List<Long> vUpdateIds);
    boolean confirmEmailChange(String token);
    void inviteUserToEvent(Long userId, Long eventId);
    boolean deleteUser(Long id);
    void toggleUserStatus(Long id);
    List<Vortrag> getAllVortraege(Long veranstaltungId);
    Vortrag getVeranstaltungsVortrag(Long veranstaltungId, Long vortragId);
    List<Nutzer> getAllReferenten(Long veranstaltungId);
    Vortrag createVortrag(Vortrag vortrag, Long veranstaltungId);
    int importAdminsFromCsv(Path csvFilePath) throws Exception;
    int importVortraegeFromCsv(Path csvFilePath, Long veranstaltungId) throws Exception;
    int importPrioritaetenFromCsv(Path csvFilePath, Long veranstaltungId) throws Exception;
    List<Slot> getAllEventSlots(Long veranstaltungId);
    Slot createEventSlot(Slot slot, Long veranstaltungId);
    Slot updateEventSlot(Long id, Slot updated, Long veranstaltungId);
    boolean deleteEventSlot(Long id, Long veranstaltungId);
    int importSlotsFromCsv(Path csvFilePath, Long veranstaltungId) throws Exception;
    Vortrag updateVortrag(Long veranstaltungId, Long vortragId, VortragDto updated);
    boolean deleteVortrag(Long id, Long veranstaltungId);
    List<VortragStatDto> getStats(Long veranstaltungId);
}