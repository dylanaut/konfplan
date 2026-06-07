package kreyj.konfplan.domain.exception;

import java.nio.file.Path;

public class CsvImportException extends BusinessException {
    private final Path csvFilePath;
    public CsvImportException(Path csvFilePath) {
        this.csvFilePath = csvFilePath;
    }

    public CsvImportException(Path csvFilePath, String message, Throwable cause) {
        super(message, cause);

        this.csvFilePath = csvFilePath;
    }

    public CsvImportException(Path csvFilePath, String message) {
        super(message);
        this.csvFilePath = csvFilePath;
    }
}
