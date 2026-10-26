package kreyj.konfplan.domain.lizenz;

/**
 * Inhalt der Anforderungsdatei fuer eine Offline-Lizenz: der Hersteller bindet die Lizenz an
 * den Public Key dieser Instanz (siehe Serverlizensierung.adoc, Offline-Lizenz).
 */
public record LizenzAnforderung(String instanzId, String publicKey, String thumbprint) {
}
