package kreyj.konfplan.domain.exception;

public class KeycloakProvisioningException extends BusinessException {
    public KeycloakProvisioningException(String message) {
        super(message);
    }


    public KeycloakProvisioningException(String message, Throwable cause) {
        super(message, cause);
    }
}
