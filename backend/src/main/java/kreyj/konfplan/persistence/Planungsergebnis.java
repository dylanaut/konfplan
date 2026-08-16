package kreyj.konfplan.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.inject.Inject;
import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.OneToOne;
import kreyj.konfplan.adapter.in.web.dto.SolverConfig;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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
    @Basic(fetch = FetchType.EAGER)
    private String jsonErgebnis;

    @Embedded
    @JdbcTypeCode(SqlTypes.JSON)
    private SolverConfig solverConfig;

    public static Planungsergebnis getPlanungsergebnis(Veranstaltung veranstaltung) {
        return Planungsergebnis.find("veranstaltung = ?1", veranstaltung).firstResult();
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
