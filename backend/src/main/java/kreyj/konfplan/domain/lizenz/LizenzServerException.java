package kreyj.konfplan.domain.lizenz;

import kreyj.konfplan.domain.exception.BusinessException;

/** Der Lizenzserver war nicht erreichbar, hat die Anfrage abgelehnt oder eine unbrauchbare Antwort geliefert. */
public class LizenzServerException extends BusinessException {

    public enum Art {
        NICHT_ERREICHBAR,
        ABGELEHNT,
        UNGUELTIGE_ANTWORT
    }

    private final Art art;
    /** Maschinenlesbarer Fehlercode des Servers (z.B. LIMIT_ERREICHT), nur bei ABGELEHNT. */
    private final String code;

    public LizenzServerException(Art art, String code, String message) {
        super(message);
        this.art = art;
        this.code = code;
    }

    public LizenzServerException(Art art, String message, Throwable cause) {
        super(message, cause);
        this.art = art;
        this.code = null;
    }

    public Art getArt() {
        return art;
    }

    public String getCode() {
        return code;
    }
}
