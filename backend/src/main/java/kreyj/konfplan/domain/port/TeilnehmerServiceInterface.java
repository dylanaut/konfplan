package kreyj.konfplan.domain.port;

import kreyj.konfplan.presentation.dto.NutzerDto;
import kreyj.konfplan.presentation.dto.VortragPrioDto;
import kreyj.konfplan.persistence.Nutzer;
import kreyj.konfplan.persistence.Teilnehmer;

import java.nio.file.Path;
import java.util.List;

public interface TeilnehmerServiceInterface {
    List<Teilnehmer> findAll(Long veranstaltungId);
    Teilnehmer findById(Long id);
    Teilnehmer findByEmail(String email);
    Teilnehmer createTeilnehmer(Teilnehmer user, Long veranstaltungId);
    int importFromCsv(Path csvFilePath, Long veranstaltungId) throws Exception;
    void deleteUser(Nutzer nutzer);
    void toggleActive(Nutzer nutzer);
    Teilnehmer updateTeilnehmerProfile(Teilnehmer teilnehmer, NutzerDto dto);
    Teilnehmer updateTeilnehmer(Long id, NutzerDto tnDto, Long veranstaltungId);
    void savePriorities(Long userId, Long veranstaltungId, List<VortragPrioDto> priorityDtos);
}