package kreyj.konfplan.application.port.in;

import kreyj.konfplan.persistence.Referent;
import kreyj.konfplan.adapter.in.web.dto.NutzerDto;
import kreyj.konfplan.adapter.in.web.dto.ReferentVeranstaltungDto;
import kreyj.konfplan.adapter.in.web.dto.VortragDto;

import java.nio.file.Path;
import java.util.List;

public interface ReferentServiceInterface {

    Referent findByLoginName(String loginName);

    void updateProfile(String loginName, NutzerDto dto);

    List<VortragDto> getReferentVortraege(String loginName);

    List<ReferentVeranstaltungDto> getReferentVeranstaltungen(Referent referent);

    VortragDto createVortrag(String loginName, VortragDto dto);

    VortragDto updateVortrag(String loginName, Long vortragId, VortragDto dto);

    void meldeVortragFuerVeranstaltungAn(String loginName, Long vortragId, Long veranstaltungId);

    VortragDto uebernimmVortragInVeranstaltung(String loginName, Long sourceVortragId, Long veranstaltungId);

    void meldeVortragFuerVeranstaltungAb(String loginName, Long vortragId, Long veranstaltungId);

    boolean deleteVortrag(String loginName, Long vortragId);

    int importFromCsv(Path csvFilePath, Long veranstaltungId) throws Exception;
}
