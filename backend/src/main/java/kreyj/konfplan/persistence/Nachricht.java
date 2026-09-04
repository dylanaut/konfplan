package kreyj.konfplan.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.ManyToOne;
import kreyj.konfplan.persistence.converter.LocalDateTimeConverter;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Systemgenerierte Nachricht an einen einzelnen Nutzer (In-App-Postfach). Erste Ausprägung:
 * Warnung an Organisatoren bzw. Aufforderung zur Neu-Priorisierung an Teilnehmer, wenn ein
 * Referent einen Wahlvortrag zurückzieht (siehe NachrichtService#benachrichtigeUeberZurueckgezogenenVortrag).
 */
@Entity
@NoArgsConstructor
@Getter
@Setter
public class Nachricht extends VersionedEntity {

    @ManyToOne
    private Nutzer empfaenger;

    @Column(nullable = false)
    private String titel;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String inhalt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NachrichtKategorie kategorie;

    @Column(nullable = false)
    @Convert(converter = LocalDateTimeConverter.class)
    private LocalDateTime erstelltAm;

    @Convert(converter = LocalDateTimeConverter.class)
    private LocalDateTime gelesenAm;

    private Long veranstaltungId;

    public static List<Nachricht> findFuerEmpfaenger(Nutzer empfaenger) {
        return list("empfaenger = ?1 order by erstelltAm desc", empfaenger);
    }

    public static long countUngelesenFuerEmpfaenger(Nutzer empfaenger) {
        return count("empfaenger = ?1 and gelesenAm is null", empfaenger);
    }
}
