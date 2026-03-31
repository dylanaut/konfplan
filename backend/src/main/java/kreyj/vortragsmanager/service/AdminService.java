package kreyj.vortragsmanager.service;

import io.quarkus.elytron.security.common.BcryptUtil;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;
import kreyj.vortragsmanager.dto.TalkStatDto;
import kreyj.vortragsmanager.entity.Priority;
import kreyj.vortragsmanager.entity.Talk;
import kreyj.vortragsmanager.entity.User;

import java.io.BufferedWriter;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.List;

@ApplicationScoped
public class AdminService {

    public List<User> getAllUsers() {
        return User.listAll();
    }

    @Transactional
    public User createUser(User user) {
        if (user.passwordHash == null || user.passwordHash.isEmpty()) {
            // Standardpasswort für neue Benutzer (sollten sie später ändern)
            user.passwordHash = BcryptUtil.bcryptHash("start123");
        }
        user.persist();
        return user;
    }

    @Transactional
    public User updateUser(Long id, User updated) {
        User entity = User.findById(id);
        if (entity == null) return null;

        entity.firstName = updated.firstName;
        entity.lastName = updated.lastName;
        entity.email = updated.email;
        entity.role = updated.role;
        entity.organization = updated.organization;
        entity.jobRole = updated.jobRole;
        entity.isActive = updated.isActive;

        return entity;
    }

    @Transactional
    public boolean deleteUser(Long id) {
        return User.deleteById(id);
    }

    @Transactional
    public void toggleUserStatus(Long id) {
        User entity = User.findById(id);
        if (entity != null) {
            entity.isActive = !entity.isActive;
        }
    }

    public List<Talk> getAllTalks() {
        return Talk.listAll();
    }

    public List<User> getAllSpeakers() {
        return User.list("role", "SPEAKER");
    }

    @Transactional
    public Talk updateTalk(Long id, Talk updated) {
        Talk entity = Talk.findById(id);
        if (entity == null || updated == null) {
            return null;
        }

        entity.title = updated.title;
        entity.abstractText = updated.abstractText;
        entity.targetAudience = updated.targetAudience;
        entity.maxRepetitions = updated.maxRepetitions;
        entity.readyToRepeat = updated.readyToRepeat;

        return entity;
    }

    @Transactional
    public boolean forceUpdatePriority(Long userId, Priority newPrio) {
        if (newPrio == null || newPrio.talk == null || newPrio.talk.id == null) {
            return false;
        }

        Priority entity = Priority.find("participant.id = ?1 and talk.id = ?2",
                userId, newPrio.talk.id).firstResult();

        if (entity == null) {
            return false;
        }

        entity.priorityValue = newPrio.priorityValue;
        entity.lastUpdated = newPrio.lastUpdated;
        return true;
    }

    public List<TalkStatDto> getStats() {
        List<Talk> allTalks = Talk.listAll();

        return allTalks.stream()
                .map(talk -> {
                    long p1 = Priority.count("talk = ?1 and priorityValue = 1", talk);
                    long top3 = Priority.count("talk = ?1 and priorityValue <= 3", talk);
                    long total = Priority.count("talk = ?1", talk);

                    return new TalkStatDto(talk.title, p1, top3, total);
                })
                .toList();
    }

    public Response exportCsv() {
        List<Priority> allPriorities = Priority.listAll();

        StreamingOutput stream = output -> {
            try (Writer writer = new BufferedWriter(new OutputStreamWriter(output, StandardCharsets.UTF_8))) {
                writer.write("Teilnehmer_Email;Nachname;Vorname;Organisation;Vortrag_Titel;Prioritaet;Zeitstempel\n");

                for (Priority p : allPriorities) {
                    String participantEmail = p.participant != null && p.participant.email != null ? p.participant.email : "";
                    String lastName = p.participant != null && p.participant.lastName != null ? p.participant.lastName : "";
                    String firstName = p.participant != null && p.participant.firstName != null ? p.participant.firstName : "";
                    String organization = p.participant != null && p.participant.organization != null ? p.participant.organization : "";
                    String talkTitle = p.talk != null && p.talk.title != null ? p.talk.title.replace(";", ",") : "";
                    String timestamp = p.lastUpdated != null ? p.lastUpdated.toString() : "";

                    writer.write(participantEmail + ";" +
                            lastName + ";" +
                            firstName + ";" +
                            organization + ";" +
                            talkTitle + ";" +
                            p.priorityValue + ";" +
                            timestamp + "\n");
                }

                writer.flush();
            }
        };

        return Response.ok(stream)
                .header("Content-Disposition", "attachment; filename=event_prioritaeten.csv")
                .build();
    }
}
