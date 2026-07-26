package kreyj.konfplan.application.port.in;

import kreyj.konfplan.persistence.Veranstaltung;
import kreyj.konfplan.adapter.in.web.dto.VeranstaltungDto;

import java.nio.file.Path;
import java.util.List;

public interface VeranstaltungServiceInterface {

    List<Veranstaltung> listAll();

    Veranstaltung findById(Long id);

    VeranstaltungDto save(VeranstaltungDto dto);

    int importFromCsv(Path csvFilePath) throws Exception;

    boolean delete(Long id);
}
