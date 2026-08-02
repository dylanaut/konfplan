package kreyj.konfplan.application.port.in;

import kreyj.konfplan.adapter.in.web.dto.VeranstaltungDto;
import kreyj.konfplan.adapter.in.web.dto.VeranstaltungImportDatasetDto;

import java.nio.file.Path;
import java.util.List;

public interface VeranstaltungImportServiceInterface {

    List<VeranstaltungImportDatasetDto> listDatasets();

    VeranstaltungDto importDataset(String datasetName) throws Exception;

    VeranstaltungDto importFromZip(Path zipFilePath) throws Exception;
}
