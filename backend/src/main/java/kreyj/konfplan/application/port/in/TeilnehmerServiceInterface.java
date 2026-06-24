package kreyj.konfplan.application.port.in;

import kreyj.konfplan.persistence.Nutzer;
import kreyj.konfplan.persistence.Teilnehmer;
import kreyj.konfplan.adapter.in.web.dto.NutzerDto;
import kreyj.konfplan.adapter.in.web.dto.TeilnehmerDto;
import kreyj.konfplan.adapter.in.web.dto.TeilnehmerVeranstaltungDto;
import kreyj.konfplan.adapter.in.web.dto.VortragPrioDto;

import java.nio.file.Path;
import java.util.List;

public interface TeilnehmerServiceInterface {

    List<Teilnehmer> findAll(Long veranstaltungId);

    Teilnehmer findById(Long id);

    Teilnehmer findByEmail(String email);

    List<TeilnehmerVeranstaltungDto> getTeilnehmerVeranstaltungen(String email);

    Teilnehmer createTeilnehmer(Teilnehmer user, Long veranstaltungId);

    int importFromCsv(Path csvFilePath, Long veranstaltungId) throws Exception;

    void deleteUser(Nutzer nutzer);

    void toggleActive(Nutzer nutzer);

    Teilnehmer updateTeilnehmerProfile(Teilnehmer teilnehmer, NutzerDto dto);

    Teilnehmer updateTeilnehmer(Long id, NutzerDto tnDto, Long veranstaltungId);

    void savePriorities(Long userId, Long veranstaltungId, List<VortragPrioDto> priorityDtos);
}
