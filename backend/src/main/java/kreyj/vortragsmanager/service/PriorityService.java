package kreyj.vortragsmanager.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.WebApplicationException;
import kreyj.vortragsmanager.dto.PriorityRequest;
import kreyj.vortragsmanager.entity.Priority;
import kreyj.vortragsmanager.entity.Talk;
import kreyj.vortragsmanager.entity.User;

import java.time.LocalDateTime;
import java.util.List;

@ApplicationScoped
public class PriorityService {

    @Transactional
    public void savePriorities(String email, List<PriorityRequest> requests) {
        User user = User.findByEmail(email);
        if (user == null) throw new WebApplicationException("User nicht gefunden", 404);

        // 1. Validierung: Nur Werte 1-10 erlaubt
        boolean invalidRange = requests.stream()
                .anyMatch(r -> r.priorityValue < 1 || r.priorityValue > 10);
        if (invalidRange) throw new WebApplicationException("Priorität muss zwischen 1 und 10 liegen", 400);

        // 2. Validierung: Keine doppelten Prioritäten (Ranking-Check)
        long uniquePriorities = requests.stream()
                .map(r -> r.priorityValue)
                .distinct()
                .count();
        if (uniquePriorities < requests.size()) {
            throw new WebApplicationException("Jede Priorität darf nur einmal vergeben werden", 400);
        }

        // 3. Bestehende Prioritäten des Users löschen (Einfacher Update-Weg)
        Priority.delete("participant", user);

        // 4. Neue Prioritäten speichern
        for (PriorityRequest req : requests) {
            Talk talk = Talk.findById(req.talkId);
            if (talk != null) {
                Priority entity = new Priority();
                entity.participant = user;
                entity.talk = talk;
                entity.priorityValue = req.priorityValue;
                entity.lastUpdated = LocalDateTime.now();
                entity.persist();
            }
        }
    }

    public List<Priority> getPrioritiesForUser(String email) {
        User user = User.findByEmail(email);
        return Priority.list("participant", user);
    }
}