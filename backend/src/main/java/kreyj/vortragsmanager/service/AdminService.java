package kreyj.vortragsmanager.service;

import io.quarkus.elytron.security.common.BcryptUtil;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;
import kreyj.vortragsmanager.dto.VortragStatDto;
import kreyj.vortragsmanager.entity.Prioritaet;
import kreyj.vortragsmanager.entity.Vortrag;
import kreyj.vortragsmanager.entity.User;
import kreyj.vortragsmanager.entity.Referent;
import kreyj.vortragsmanager.entity.Teilnehmer;

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
        // In Single Table Inheritance ist die Rolle fest durch die Klasse definiert
        
        if (entity instanceof Teilnehmer && updated instanceof Teilnehmer) {
            ((Teilnehmer) entity).organization = ((Teilnehmer) updated).organization;
            ((Teilnehmer) entity).jobRole = ((Teilnehmer) updated).jobRole;
        } else if (entity instanceof Referent && updated instanceof Referent) {
            ((Referent) entity).biography = ((Referent) updated).biography;
        }

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

    public List<Vortrag> getAllVortraege() {
        return Vortrag.listAll();
    }

    public List<User> getAllReferenten() {
        return User.list("role", "REFERENT");
    }

    @Transactional
    public Vortrag updateVortrag(Long id, Vortrag updated) {
        Vortrag entity = Vortrag.findById(id);
        if (entity == null || updated == null) {
            return null;
        }

        entity.title = updated.title;
        entity.abstractText = updated.abstractText;
        entity.targetAudience = updated.targetAudience;
        entity.maxRepetitions = updated.maxRepetitions;
        entity.readyToRepeat = updated.readyToRepeat;
        entity.referent = updated.referent;

        return entity;
    }

    @Transactional
    public boolean forceUpdatePrioritaet(Long teilnehmerId, Prioritaet newPrio) {
        if (newPrio == null || newPrio.vortrag == null || newPrio.vortrag.id == null) {
            return false;
        }

        Prioritaet entity = Prioritaet.find("teilnehmer.id = ?1 and vortrag.id = ?2",
                teilnehmerId, newPrio.vortrag.id).firstResult();

        if (entity == null) {
            return false;
        }

        entity.priorityValue = newPrio.priorityValue;
        entity.lastUpdated = newPrio.lastUpdated;
        return true;
    }

    public List<VortragStatDto> getStats() {
        List<Vortrag> allVortraege = Vortrag.listAll();

        return allVortraege.stream()
                .map(v -> {
                    long p1 = Prioritaet.count("vortrag = ?1 and priorityValue = 1", v);
                    long top3 = Prioritaet.count("vortrag = ?1 and priorityValue <= 3", v);
                    long total = Prioritaet.count("vortrag = ?1", v);

                    return new VortragStatDto(v.title, p1, top3, total);
                })
                .toList();
    }

    public Response exportCsv() {
        List<Prioritaet> allPrioritaeten = Prioritaet.listAll();

        StreamingOutput stream = output -> {
            try (Writer writer = new BufferedWriter(new OutputStreamWriter(output, StandardCharsets.UTF_8))) {
                writer.write("Teilnehmer_Email;Nachname;Vorname;Organisation;Vortrag_Titel;Prioritaet;Zeitstempel\n");

                for (Prioritaet p : allPrioritaeten) {
                    String email = p.teilnehmer != null ? p.teilnehmer.email : "";
                    String lastName = p.teilnehmer != null ? p.teilnehmer.lastName : "";
                    String firstName = p.teilnehmer != null ? p.teilnehmer.firstName : "";
                    String organization = p.teilnehmer != null ? p.teilnehmer.organization : "";
                    String title = p.vortrag != null ? p.vortrag.title.replace(";", ",") : "";
                    String ts = p.lastUpdated != null ? p.lastUpdated.toString() : "";

                    writer.write(email + ";" + lastName + ";" + firstName + ";" + organization + ";" + title + ";" + p.priorityValue + ";" + ts + "\n");
                }
                writer.flush();
            }
        };

        return Response.ok(stream)
                .header("Content-Disposition", "attachment; filename=prioritaeten.csv")
                .build();
    }
}
