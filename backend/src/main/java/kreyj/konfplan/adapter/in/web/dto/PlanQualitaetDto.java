package kreyj.konfplan.adapter.in.web.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import java.util.Map;

@RegisterForReflection
@SuppressWarnings("unused")
public class PlanQualitaetDto {
    public int guete;
    public int zuweisungen;
    public int raumwechsel;
    public String status;
    // true, wenn seit der letzten Planerstellung ein bereits zugeteilter Wahlvortrag
    // zurückgezogen wurde (siehe Planungsergebnis#veraltet) - Organisator sollte neu planen.
    public boolean veraltet;

//    public Map<Integer, Long> prioVerteilung; // Prio -> Anzahl
//    public Map<String, Long> leerlaufProSlot; // Slot-Description -> Anzahl Teilnehmer ohne Zuweisung
//    public Map<String, Long> vortraegeProReferent; // Referent Name -> Anzahl Vorträge


    public PlanQualitaetDto() {}

    public PlanQualitaetDto(int guete, int zuweisungen, int raumwechsel, String status, boolean veraltet) {
        this.guete = guete;
        this.zuweisungen = zuweisungen;
        this.raumwechsel = raumwechsel;
        this.status = status;
        this.veraltet = veraltet;
    }
}
