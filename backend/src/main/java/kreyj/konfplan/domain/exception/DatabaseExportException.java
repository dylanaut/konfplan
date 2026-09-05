package kreyj.konfplan.domain.exception;

public class DatabaseExportException extends BusinessException {
    public DatabaseExportException(String message) {
        super(message);
    }


    public DatabaseExportException(String message, Throwable cause) {
        super(message, cause);
    }
}
