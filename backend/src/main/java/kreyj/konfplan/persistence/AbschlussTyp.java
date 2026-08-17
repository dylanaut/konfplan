package kreyj.konfplan.persistence;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum AbschlussTyp {
    BERUFSREIFE("Berufsreife"),
    MITTLERE_REIFE("Mittlere Reife"),
    ALLGEMEINE_HOCHSCHULREIFE("Allgemeine Hochschulreife"),
    FACHHOCHSCHULREIFE("Fachhochschulreife"),
    HOCHSCHULABSCHLUSS("Hochschulabschluss");

    private final String name;

    AbschlussTyp(String name) {
        this.name = name;
    }

    @JsonValue
    public String getName() {
        return name;
    }

    @JsonCreator
    public static AbschlussTyp fromName(String name) {
        for (AbschlussTyp typ : values()) {
            if (typ.name.equals(name)) {
                return typ;
            }
        }
        throw new IllegalArgumentException("Unbekannter AbschlussTyp: " + name);
    }

}
