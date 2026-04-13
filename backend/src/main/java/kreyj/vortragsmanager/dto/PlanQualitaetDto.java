package kreyj.vortragsmanager.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import java.util.Map;

@RegisterForReflection
public class PlanQualitaetDto {
    public Map<Integer, Long> prioVerteilung; // Prio -> Anzahl
    public Map<String, Long> leerlaufProSlot; // Slot-Description -> Anzahl Teilnehmer ohne Zuweisung
    public Map<String, Long> vortraegeProReferent; // Referent Name -> Anzahl Vorträge
    public double durchschnittsPrio;
    public long gesamtZuweisungen;

    public PlanQualitaetDto() {}
}
