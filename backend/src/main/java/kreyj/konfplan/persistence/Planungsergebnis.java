package kreyj.konfplan.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.OneToOne;
import kreyj.konfplan.domain.exception.EntityNotFoundException;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@NoArgsConstructor
@Getter
@Setter
public class Planungsergebnis extends VersionedEntity {

    @OneToOne
    @JoinColumn(name = "veranstaltung_id", nullable = false, unique = true)
    private Veranstaltung veranstaltung;

    @Lob
    @Column(nullable = false)
    @Basic(fetch = FetchType.EAGER) // Ensure eager loading of the LOB
    private String jsonErgebnis;

    @Column(nullable = false)
    private String solver;

    @Column(nullable = false)
    private int timeout;


    public static Planungsergebnis getPlanungsergebnis(Veranstaltung veranstaltung) {
        Planungsergebnis planungsergebnis = Planungsergebnis.find("veranstaltung = ?1", veranstaltung).firstResult();

        if (planungsergebnis == null) {
            throw new EntityNotFoundException(Planungsergebnis.class, "Kein Planungsergebnis für Veranstaltung '" +
                    veranstaltung.getName() + "' gefunden");
        }

        return planungsergebnis;
    }


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

        public int kosten;
        public int zuweisungen;

        public String toJson() {
            try {
                return new ObjectMapper().writeValueAsString(this);
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        }
    }
}