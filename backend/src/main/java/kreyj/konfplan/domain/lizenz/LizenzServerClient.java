package kreyj.konfplan.domain.lizenz;

import java.security.PrivateKey;

/**
 * Out-Port zum Lizenzserver (Protokoll siehe Serverlizensierung.adoc). Jede Anfrage ist mit dem
 * Instanzschluessel signiert; der Server prueft die Signatur gegen den bei der Aktivierung
 * registrierten Public Key.
 */
public interface LizenzServerClient {

    /** @param version Version der laufenden KonfPlan-Instanz (nur zur Information des Servers) */
    record Identitaet(String instanzId, String publicKey, PrivateKey privateKey, String version) {
    }

    /** Ist ein (gueltiger) Lizenzserver konfiguriert? Ohne bleibt nur der Offline-Pfad. */
    boolean konfiguriert();

    /** @return die ausgestellte Online-Lizenz (JWS) */
    String aktivieren(Identitaet instanz, String lizenzschluessel) throws LizenzServerException;

    /** @return die erneuerte Online-Lizenz mit hoeherem Zaehler */
    String revalidieren(Identitaet instanz, String token) throws LizenzServerException;

    /** Gibt den Aktivierungsplatz beim Lizenzserver frei. */
    void deaktivieren(Identitaet instanz, String token) throws LizenzServerException;
}
