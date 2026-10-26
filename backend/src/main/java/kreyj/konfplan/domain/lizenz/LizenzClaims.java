package kreyj.konfplan.domain.lizenz;

import java.time.Instant;
import java.util.Set;

/**
 * Geprueftes Ergebnis einer Lizenzverifikation. Wird nur von {@link LizenzVerifier} erzeugt,
 * nachdem Signatur, Aussteller, Zielgruppe, Zeitraum und Instanzbindung stimmen.
 */
public record LizenzClaims(
        String lizenzId,
        String kundenId,
        int maxInstanzen,
        Set<String> features,
        Typ typ,
        String kid,
        Instant ausgestelltAm,
        Instant gueltigAb,
        Instant gueltigBis) {

    public enum Typ {
        ONLINE,
        OFFLINE
    }
}
