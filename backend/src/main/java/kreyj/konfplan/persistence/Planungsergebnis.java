package kreyj.konfplan.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.inject.Inject;
import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Convert;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import kreyj.konfplan.adapter.in.web.dto.SolverConfig;
import kreyj.konfplan.persistence.converter.LocalDateTimeConverter;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@NoArgsConstructor
@Getter
@Setter
public class Planungsergebnis extends VersionedEntity {
    // Vor Issue #461 @OneToOne mit Unique-Constraint (genau ein Ergebnis pro Veranstaltung) -
    // jetzt @ManyToOne, da jeder Planungslauf eine eigene Zeile anlegt (siehe
    // PlanErstellungService#speicherePlanungsergebnis) statt die bestehende zu überschreiben.
    @ManyToOne
    @JoinColumn(name = "veranstaltung_id", nullable = false)
    private Veranstaltung veranstaltung;

    @Lob
    @Column(nullable = false)
    @Basic(fetch = FetchType.EAGER)
    private String jsonErgebnis;

    @Embedded
    @JdbcTypeCode(SqlTypes.JSON)
    private SolverConfig solverConfig;

    // Gesetzt, wenn nach der letzten Planerstellung ein bereits zugeteilter Wahlvortrag
    // zurückgezogen wurde (siehe NachrichtService#benachrichtigeUeberZurueckgezogenenVortrag) -
    // wird bei der naechsten erfolgreichen Neu-Erstellung wieder zurueckgesetzt.
    private boolean veraltet = false;

    // Name/E-Mail des Organisators, der diesen Planungslauf ausgelöst hat (null bei Zeilen aus
    // der Zeit vor Issue #461). Erstellungszeitpunkt ebenso.
    private String ersteller;

    @Convert(converter = LocalDateTimeConverter.class)
    private LocalDateTime erstelltAm;

    // Genau ein Ergebnis pro Veranstaltung darf publiziert sein - das steht dann Organisatoren,
    // Teilnehmern und Referenten als Plan/Report zur Verfügung (siehe getPlanungsergebnis unten).
    // Neue Ergebnisse starten unpubliziert, ein Publizieren muss der Organisator explizit
    // bestätigen (siehe PlanungResource#publiziereErgebnis).
    private boolean publiziert = false;

    /**
     * Liefert das aktuell PUBLIZIERTE Planungsergebnis einer Veranstaltung (oder {@code null},
     * falls noch keines veröffentlicht wurde) - der zentrale Chokepoint, über den praktisch alle
     * Teilnehmer-/Referenten-/Organisator-Reports laufen. Für die Verwaltungsansicht mit ALLEN
     * Ergebnissen (veröffentlicht oder nicht) siehe {@link #findAlleFuer}.
     */
    public static Planungsergebnis getPlanungsergebnis(Veranstaltung veranstaltung) {
        return Planungsergebnis.find("veranstaltung = ?1 and publiziert = true", veranstaltung).firstResult();
    }


    public static List<Planungsergebnis> findAlleFuer(Veranstaltung veranstaltung) {
        return Planungsergebnis.find("veranstaltung = ?1 order by id desc", veranstaltung).list();
    }

    @SuppressWarnings("unused")
    public static class MinizincResult {

        // enthält für jeden Wahlvortrag und über alle Instanzen die MZ-SlotId
        public int[][] instanz_slot;
        // enthält für jeden Raum und über alle Instanzen die MZ-RaumId
        public int[][] instanz_raum;
        // enthält für jeden Teilnehmer, jeden Wahlvortrag und über alle Instanzen: true/false  für teilnahme
        public boolean[][][] besucht;

        public long[] teilnehmer_oids;
        public long[] wahlvortrag_oids;
        public long[] slot_oids;
        public long[] raum_oids;

        public int guete;
        public int zuweisungen;
        public int raumwechsel;


        public String toJson(final ObjectMapper mapper) {
            try {
                return mapper.writeValueAsString(this);
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
