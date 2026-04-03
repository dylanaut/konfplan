package kreyj.vortragsmanager.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.WebApplicationException;
import kreyj.vortragsmanager.dto.PrioritaetRequest;
import kreyj.vortragsmanager.entity.Prioritaet;
import kreyj.vortragsmanager.entity.Vortrag;
import kreyj.vortragsmanager.entity.User;
import kreyj.vortragsmanager.entity.Teilnehmer;

import java.time.LocalDateTime;
import java.util.List;

@ApplicationScoped
public class PrioritaetService {

    @Transactional
    public void savePrioritaeten(String email, List<PrioritaetRequest> requests) {
        User user = User.findByEmail(email);
        if (!(user instanceof Teilnehmer)) throw new WebApplicationException("Benutzer ist kein Teilnehmer", 400);
        Teilnehmer teilnehmer = (Teilnehmer) user;

        // 1. Validierung: Nur Werte 1-10 erlaubt
        boolean invalidRange = requests.stream()
                .anyMatch(r -> r.prioWert < 1 || r.prioWert > 10);
        if (invalidRange) throw new WebApplicationException("Priorität muss zwischen 1 und 10 liegen", 400);

        // 2. Validierung: Keine doppelten Prioritäten (Ranking-Check)
        long uniquePriorities = requests.stream()
                .map(r -> r.prioWert)
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
                entity.priorityValue = req.prioWert;
                entity.lastUpdated = LocalDateTime.now();
                entity.persist();
            }
        }
    }

    public List<Prioritaet> getPrioritaetenForUser(String email) {
        User user = User.findByEmail(email);
        if (!(user instanceof Teilnehmer)) throw new WebApplicationException("Benutzer ist kein Teilnehmer", 400);
        Teilnehmer teilnehmer = (Teilnehmer) user;
        return Prioritaet.list("teilnehmer", teilnehmer);
    }
}
