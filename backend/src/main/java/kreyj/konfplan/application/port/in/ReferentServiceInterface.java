package kreyj.konfplan.application.port.in;

import kreyj.konfplan.persistence.Referent;
import kreyj.konfplan.adapter.in.web.dto.NutzerDto;
import kreyj.konfplan.adapter.in.web.dto.ReferentVeranstaltungDto;
import kreyj.konfplan.adapter.in.web.dto.VortragDto;

import java.nio.file.Path;
import java.util.List;

public interface ReferentServiceInterface {

    Referent findByEmail(String email);

    void updateProfile(String email, NutzerDto dto);

    List<VortragDto> getReferentVortraege(String email);

    List<ReferentVeranstaltungDto> getReferentVeranstaltungen(Referent referent);

    VortragDto createVortrag(String email, VortragDto dto);

    VortragDto updateVortrag(String email, Long vortragId, VortragDto dto);

    void meldeVortragFuerVeranstaltungAn(String email, Long vortragId, Long veranstaltungId);

    VortragDto uebernimmVortragInVeranstaltung(String email, Long sourceVortragId, Long veranstaltungId);

    void meldeVortragFuerVeranstaltungAb(String email, Long vortragId, Long veranstaltungId);

    boolean deleteVortrag(String email, Long vortragId);

    int importFromCsv(Path csvFilePath, Long veranstaltungId) throws Exception;
}
