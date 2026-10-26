package kreyj.konfplan.domain.lizenz;

import kreyj.konfplan.domain.exception.BusinessException;

/**
 * Eine Lizenz wurde abgelehnt. {@link #getGrund()} nennt den Ablehnungsgrund, damit die UI
 * zwischen einer fehlerhaften, abgelaufenen und einer fremden Lizenz unterscheiden kann.
 */
public class LizenzFehler extends BusinessException {

    public enum Grund {
        FORMAT,
        ALGORITHMUS,
        UNBEKANNTER_SCHLUESSEL,
        GESPERRTER_SCHLUESSEL,
        SIGNATUR,
        AUSSTELLER,
        ZIELGRUPPE,
        TYP,
        UNVOLLSTAENDIG,
        INSTANZ,
        ABGELAUFEN,
        NOCH_NICHT_GUELTIG,
        ZEIT,
        VERALTET
    }

    private final Grund grund;

    public LizenzFehler(Grund grund, String message) {
        super(message);
        this.grund = grund;
    }

    public Grund getGrund() {
        return grund;
    }
}
