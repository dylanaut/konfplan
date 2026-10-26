package kreyj.konfplan.domain.lizenz;

import java.time.Instant;
import java.util.Set;

/**
 * Geprueftes Ergebnis einer Lizenzverifikation. Wird nur von {@link LizenzVerifier} erzeugt,
 * nachdem Signatur, Aussteller, Zielgruppe, Zeitraum und Instanzbindung stimmen.
 */
/**
 * @param zaehler                Revalidierungszaehler (nur Online-Lizenzen, sonst null) - dient der Klon-Erkennung
 * @param naechsteRevalidierung  ab hier ist eine Online-Revalidierung faellig (nur Online-Lizenzen, sonst null);
 *                               bis gueltigBis laeuft danach die Grace-Period
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
        Instant gueltigBis,
        Long zaehler,
        Instant naechsteRevalidierung) {

    public enum Typ {
        ONLINE,
        OFFLINE
    }
}
