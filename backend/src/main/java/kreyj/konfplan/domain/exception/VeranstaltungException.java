package kreyj.konfplan.domain.exception;

public class VeranstaltungException extends BusinessException {
    public VeranstaltungException() {
    }

    public VeranstaltungException(String message, Throwable cause) {
        super(message, cause);
    }

    public VeranstaltungException(String message) {
        super(message);
    }
}
