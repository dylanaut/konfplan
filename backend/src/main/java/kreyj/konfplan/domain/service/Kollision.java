package kreyj.konfplan.domain.service;

import lombok.Getter;

/**
 * Beschreibt eine vor der Planerstellung erkannte Inkonsistenz in den Eingangsdaten
 * (z.B. ein Teilnehmer, der trotz Pflichtvortrag noch für dessen Slot verfügbar ist,
 * oder ein Raum, der trotz Pflichtbelegung noch für Wahlvorträge freigegeben ist).
 */
@Getter
public class Kollision {

    public enum Typ {
        /** Teilnehmer ist trotz Pflichtvortrag noch für dessen Slot verfügbar. */
        TEILNEHMER_VERFUEGBARKEIT,
        /** Raum eines Pflichtvortrags steht im Pflichtslot noch für Wahlvorträge zur Verfügung. */
        RAUM_SLOT
    }

    private final Typ typ;
    private final String nachricht;

    public Kollision(Typ typ, String nachricht) {
        this.typ = typ;
        this.nachricht = nachricht;
    }

    @Override
    public String toString() {
        return nachricht;
    }
}