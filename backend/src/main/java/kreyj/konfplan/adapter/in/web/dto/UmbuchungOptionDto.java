package kreyj.konfplan.adapter.in.web.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@RegisterForReflection
@NoArgsConstructor
@AllArgsConstructor
public class UmbuchungOptionDto {
    public Long wahlvortragId;
    public int instanzIndex;
    public String vortragTitel;
    public String referentName;
    public String raumName;
    public int kapazitaet;
    public int belegteAnzahl;
    // Anzahl der Neigungen, die Teilnehmer und Wahlvortrag gemeinsam haben - Entscheidungshilfe für
    // den Organisator, um dem Teilnehmer eine passende Alternative vorzuschlagen.
    public int neigungsUeberschneidung;
}
