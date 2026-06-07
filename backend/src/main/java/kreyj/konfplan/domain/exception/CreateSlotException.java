package kreyj.konfplan.domain.exception;

public class CreateSlotException extends BusinessException {
    public CreateSlotException() {
    }

    public CreateSlotException(String message, Throwable cause) {
        super(message, cause);
    }

    public CreateSlotException(String message) {
        super(message);
    }
}
