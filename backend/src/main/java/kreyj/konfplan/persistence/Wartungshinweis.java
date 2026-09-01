package kreyj.konfplan.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import kreyj.konfplan.persistence.converter.LocalDateTimeConverter;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Singleton-Ankuendigung eines geplanten Wartungsfensters, allen eingeloggten Nutzern als Banner
 * angezeigt (siehe Wartungshinweis.aktuelles() - es gibt nie mehr als eine Zeile). Ist bereits
 * kein Datum gesetzt oder liegt endeZeitpunkt in der Vergangenheit, gilt die Ankuendigung als
 * abgelaufen/inaktiv, ohne dass ein separater Aufraeum-Job noetig waere.
 */
@Entity
@NoArgsConstructor
@Getter
@Setter
public class Wartungshinweis extends VersionedEntity {

    @Column
    @Convert(converter = LocalDateTimeConverter.class)
    private LocalDateTime startZeitpunkt;

    @Column
    @Convert(converter = LocalDateTimeConverter.class)
    private LocalDateTime endeZeitpunkt;

    public static Wartungshinweis aktuelles() {
        return findAll().firstResult();
    }
}
