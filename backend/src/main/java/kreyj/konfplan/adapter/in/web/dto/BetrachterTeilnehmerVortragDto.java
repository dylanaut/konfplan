package kreyj.konfplan.adapter.in.web.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

/**
 * Eine Zeile der Vorträge-Subtabelle je Teilnehmer in der Betrachter-Ansicht (siehe #737):
 * verknüpft die Planung (Pflichtvortrag/priorisierter oder aufgefüllter Wahlvortrag, ggf.
 * unangemeldeter Besuch) mit dem per QR-Code erfassten Anwesenheitsstatus.
 */
@RegisterForReflection
@NoArgsConstructor
@AllArgsConstructor
public class BetrachterTeilnehmerVortragDto {
    public String vortragTitel;
    /** "Pflicht", "Füll", "unangemeldet", oder die vergebene Priorität als Zahl. */
    public String prioAnzeige;
    /** "nicht besucht", oder "{Raum} · {Uhrzeit}", falls per QR-Code-Scan bestätigt. */
    public String besuchtAnzeige;
}
