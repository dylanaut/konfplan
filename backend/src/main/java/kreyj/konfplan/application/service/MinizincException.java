package kreyj.konfplan.application.service;

import lombok.Getter;

import java.util.EnumMap;
import java.util.Map;

@Getter
public class MinizincException extends RuntimeException {

    private final MZ_Exception exceptionType;
    private final String message;

    private final static Map<MZ_Exception, String> MELDUNGEN = new EnumMap<>(MZ_Exception.class);

    static {
        MELDUNGEN.put(MZ_Exception.NO_SOLUTION, "MiniZinc konnte keine Lösung für die Wahlvorträge finden.");
        MELDUNGEN.put(MZ_Exception.INVOCATION_ERROR, "Fehler beim Aufruf von MiniZinc.");
    }

    public MinizincException(MZ_Exception exceptionType) {
        this(exceptionType, MELDUNGEN.get(exceptionType));
    }

    public MinizincException(MZ_Exception exceptionType, String message) {
        this.exceptionType = exceptionType;
        this.message = message;
    }

    @Override
    public String getMessage() {
        return message;
    }


    public enum MZ_Exception {
        NO_SOLUTION,
        INVOCATION_ERROR,
        UNSATISFIABLE,
        INTERMEDIATE
    }
}
