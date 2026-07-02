package kreyj.konfplan.application.port.in;

import kreyj.konfplan.persistence.Nutzer;
import kreyj.konfplan.persistence.Referent;
import kreyj.konfplan.persistence.Slot;
import kreyj.konfplan.persistence.Veranstaltung;
import kreyj.konfplan.persistence.Vortrag;
import kreyj.konfplan.adapter.in.web.dto.NutzerDto;
import kreyj.konfplan.adapter.in.web.dto.RaumVerfuegbarkeitDto;
import kreyj.konfplan.adapter.in.web.dto.SlotDto;
import kreyj.konfplan.adapter.in.web.dto.VortragDto;
import kreyj.konfplan.adapter.in.web.dto.VortragStatDto;

import java.nio.file.Path;
import java.util.List;

public interface AdminServiceInterface {

    List<NutzerDto> getAllUsers();

    List<NutzerDto> getAllUsers(Long veranstaltungId);

    Nutzer findNutzer(Long id);

    NutzerDto createUser(NutzerDto dto, List<Long> veranstaltungsIds);

    NutzerDto updateUser(Long id, NutzerDto dto, List<Long> vUpdateIds);

    boolean confirmEmailChange(String token);

    void inviteUserToEvent(Long nutzerId, Long veranstaltungId);

    boolean deleteUser(Long id);

    void toggleUserStatus(Long id);

    List<Vortrag> getAllVortraege(Long veranstaltungId);

    Vortrag getVeranstaltungsVortrag(Long veranstaltungId, Long vortragId);

    List<Referent> getAllReferenten(Long veranstaltungId);

    Vortrag createVortrag(VortragDto vortragDto);

    int importAdminsFromCsv(Path csvFilePath) throws Exception;

    int importVortraegeFromCsv(Path csvFilePath, Long veranstaltungId);

    int importPrioritaetenFromCsv(Path csvFilePath, Long veranstaltungId) throws Exception;

    List<Slot> getAllEventSlots(Long veranstaltungId);

    Slot createSlot(SlotDto slotDto, Long veranstaltungId);

    Slot updateSlot(Long slotId, SlotDto updated, Long veranstaltungId);

    boolean deleteSlot(Long id, Veranstaltung veranstaltung);

    int importSlotsFromCsv(Path csvFilePath, Long veranstaltungId);

    VortragDto updateVortrag(Long vortragId, Long veranstaltungId, VortragDto updated);

    boolean deleteVortrag(Long id, Veranstaltung veranstaltung);

    List<VortragStatDto> getStats(Long veranstaltungId);



    List<RaumVerfuegbarkeitDto> getRaumVerfuegbarkeiten(Long veranstaltungId);

    List<String> getGruppen(Long veranstaltungId);

    void createGruppe(Long veranstaltungId, String gruppenName);

    void renameGruppe(Long veranstaltungId, String alterName, String neuerName);

    void deleteGruppe(Long veranstaltungId, String gruppenName);

    int importNutzerVerfuegbarkeitenFromCsv(Path csvFilePath, Class<? extends Nutzer> nutzerKlasse, Long veranstaltungId);

    int importRaumVerfuegbarkeitenFromCsv(Path csvFilePath, Long veranstaltungId);
}
