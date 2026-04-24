package kreyj.vortragsmanager.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.WebApplicationException;
import kreyj.vortragsmanager.dto.PrioritaetRequest;
import kreyj.vortragsmanager.entity.Nutzer;
import kreyj.vortragsmanager.entity.Prioritaet;
import kreyj.vortragsmanager.entity.Vortrag;
import kreyj.vortragsmanager.entity.Teilnehmer;
import kreyj.vortragsmanager.entity.Veranstaltung;

import java.time.LocalDateTime;
import java.util.List;

@ApplicationScoped
public class PrioritaetService {

    @Transactional
    public void savePrioritaeten(String email, List<PrioritaetRequest> requests) {
        Nutzer nutzer = Nutzer.findByEmail(email);
        if (!(nutzer instanceof Teilnehmer)) throw new WebApplicationException("Benutzer ist kein Teilnehmer", 400);
        Teilnehmer teilnehmer = (Teilnehmer) nutzer;

        // Deadline Check
        if (!requests.isEmpty()) {
            Vortrag v1 = Vortrag.findById(requests.get(0).vortragId);
            if (v1 != null) {
                Veranstaltung v = v1.veranstaltung;
                if (v.deadlineTeilnehmer != null && v.deadlineTeilnehmer.isBefore(LocalDateTime.now())) {
                    throw new WebApplicationException("Die Deadline für Teilnehmer für diese Veranstaltung ist bereits abgelaufen.", 403);
                }
            }
        }

        // 1. Validierung: Nur Werte 1-10 erlaubt
        boolean invalidRange = requests.stream()
                .anyMatch(r -> r.prioWert < 1 || r.prioWert > 10); // Hier umbenannt
        if (invalidRange) throw new WebApplicationException("Priorität muss zwischen 1 und 10 liegen", 400);

        // 2. Validierung: Keine doppelten Prioritäten (Ranking-Check)
        long uniquePriorities = requests.stream()
                .map(r -> r.prioWert) // Hier umbenannt
                .distinct()
                .count();
        if (uniquePriorities < requests.size()) {
            throw new WebApplicationException("Jede Priorität darf nur einmal vergeben werden", 400);
        }

        // 3. Bestehende Prioritäten des Users löschen (Einfacher Update-Weg)
        Prioritaet.delete("teilnehmer", teilnehmer);

        // 4. Neue Prioritäten speichern
        for (PrioritaetRequest req : requests) {
            Vortrag vortrag = Vortrag.findById(req.vortragId);
            if (vortrag != null) {
                Prioritaet entity = new Prioritaet();
                entity.teilnehmer = teilnehmer;
                entity.vortrag = vortrag;
                entity.prioWert = req.prioWert; // Hier umbenannt
                entity.lastUpdated = LocalDateTime.now();
                entity.persist();
            }
        }
    }

    public List<Prioritaet> getPrioritaetenForUser(String email) {
        Nutzer nutzer = Nutzer.findByEmail(email);
        if (!(nutzer instanceof Teilnehmer)) throw new WebApplicationException("Benutzer ist kein Teilnehmer", 400);
        Teilnehmer teilnehmer = (Teilnehmer) nutzer;
        return Prioritaet.list("teilnehmer", teilnehmer);
    }
}
