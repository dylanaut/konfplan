package kreyj.konfplan.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Singleton (hoechstens eine Zeile, siehe Lizenzzustand.aktuell()): Identitaet dieser Instanz
 * (Ed25519-Schluesselpaar) und der Zustand der installierten Lizenz. Der private Schluessel liegt
 * bewusst im Klartext - wer die Datenbank besitzt, betreibt auch die Instanz, eine Verschluesselung
 * mit einem lokal ablegbaren Schluessel brachte keinen Schutz.
 */
@Entity
@NoArgsConstructor
@Getter
@Setter
public class Lizenzzustand extends VersionedEntity {

    @Column(nullable = false, length = 36)
    private String instanzId;

    /** Base64 (X.509 SubjectPublicKeyInfo) */
    @Column(nullable = false)
    private String instanzPublicKey;

    /** Base64 (PKCS#8) */
    @Column(nullable = false)
    private String instanzPrivateKey;

    @Column(columnDefinition = "TEXT")
    private String lizenzToken;

    /** Hoechster bislang gesehener Zeitpunkt - erkennt zurueckgestellte Systemuhren. */
    @Column
    private Instant zeitWasserstand;

    @Column
    private Instant letzteValidierung;

    public static Lizenzzustand aktuell() {
        return findAll().firstResult();
    }
}
