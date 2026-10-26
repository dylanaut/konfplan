package kreyj.konfplan.domain.lizenz;

public enum LizenzZustand {
    /** Gueltige, an diese Instanz gebundene Lizenz. */
    LIZENZIERT,
    /** Lizenz vorhanden, aber abgelaufen, ungueltig oder Systemzeit zurueckgestellt. */
    EINGESCHRAENKT,
    /** Es wurde noch keine Lizenz installiert. */
    UNLIZENZIERT
}
