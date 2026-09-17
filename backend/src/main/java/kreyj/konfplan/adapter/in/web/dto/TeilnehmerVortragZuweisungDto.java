package kreyj.konfplan.adapter.in.web.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Wie {@link ZuweisungDto}, aber zusätzlich mit den Ids von Vortrag/Slot/Raum sowie dem
 * Vortragstyp ("PFLICHT"/"WAHL") - benötigt, um eine Zuweisung gegen
 * {@link kreyj.konfplan.persistence.Prioritaet}- und
 * {@link kreyj.konfplan.persistence.Anwesenheit}-Datensätze abzugleichen (siehe #737).
 * {@link ZuweisungDto} selbst bleibt unverändert, da es bereits an vielen Stellen als reines
 * Anzeige-DTO verwendet wird.
 */
@RegisterForReflection
@NoArgsConstructor
@AllArgsConstructor
public class TeilnehmerVortragZuweisungDto {
    public Long vortragId;
    public String vortragTyp;
    public String vortragTitel;
    public Long slotId;
    public LocalDateTime slotBeginn;
    public LocalDateTime slotEnde;
    public Long raumId;
    public String raumName;
    public String gebaeudeName;
    public String referentName;
}
