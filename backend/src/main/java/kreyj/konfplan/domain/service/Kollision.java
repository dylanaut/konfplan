package kreyj.konfplan.domain.service;

/**
 * Beschreibt eine vor der Planerstellung erkannte Inkonsistenz in den Eingangsdaten
 * (z.B. ein Teilnehmer, der trotz Pflichtvortrag noch für dessen Slot verfügbar ist,
 * oder ein Raum, der trotz Pflichtbelegung noch für Wahlvorträge freigegeben ist).
 */
public record Kollision(Typ typ, String nachricht) {

    public enum Typ {
        /**
         * Teilnehmer ist trotz Pflichtvortrag noch für dessen Slot verfügbar.
         */
        TEILNEHMER_VERFUEGBARKEIT,
        /**
         * Raum eines Pflichtvortrags steht im Pflichtslot noch für Wahlvorträge zur Verfügung.
         */
        RAUM_SLOT,
        /**
         * Referent ist trotz Pflichtvortrag noch für dessen Slot verfügbar.
         */
        REFERENT_VERFUEGBARKEIT
    }


    @Override
    public String toString() {
        return nachricht;
    }
}
