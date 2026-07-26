package kreyj.konfplan.adapter.in.web.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import java.util.Map;

@RegisterForReflection
@SuppressWarnings("unused")
public class PlanQualitaetDto {
    public int kosten;
    public int anzahlZuweisungen;
    public String status;

    public Map<Integer, Long> prioVerteilung; // Prio -> Anzahl
    public Map<String, Long> leerlaufProSlot; // Slot-Description -> Anzahl Teilnehmer ohne Zuweisung
    public Map<String, Long> vortraegeProReferent; // Referent Name -> Anzahl Vorträge
    public double durchschnittsPrio;
    public long gesamtZuweisungen;

    public PlanQualitaetDto() {}

    public PlanQualitaetDto(int kosten, int anzahlZuweisungen, String status) {
        this.kosten = kosten;
        this.anzahlZuweisungen = anzahlZuweisungen;
        this.status = status;
    }
}
