package kreyj.konfplan.domain.lizenz;

public enum LizenzZustand {
    /** Gueltige, an diese Instanz gebundene Lizenz. */
    LIZENZIERT,
    /** Online-Lizenz, deren Revalidierung faellig ist, aber noch innerhalb der Grace-Period - voll nutzbar. */
    GRACE,
    /** Lizenz vorhanden, aber abgelaufen, ungueltig oder Systemzeit zurueckgestellt. */
    EINGESCHRAENKT,
    /** Es wurde noch keine Lizenz installiert. */
    UNLIZENZIERT
}
