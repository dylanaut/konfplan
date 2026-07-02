package kreyj.konfplan.domain.exception;

public class CollisionsException extends BusinessException {
    public CollisionsException() {
    }


    public CollisionsException(String message, Throwable cause) {
        super(message, cause);
    }


    public CollisionsException(String message) {
        super(message);
    }
}
