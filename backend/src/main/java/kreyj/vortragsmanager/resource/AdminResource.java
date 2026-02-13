package kreyj.vortragsmanager.resource;

import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
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
import java.util.stream.Collectors;

@Path("/api/admin")
@RolesAllowed("ADMIN")
public class AdminResource {
    @GET
    @PermitAll
    @Path("/talks")
    public List<Talk> getAllTalks() {
        // Liefert alle Talks inkl. der verknüpften Speaker-Objekte
        return Talk.listAll();
    }

    @GET
    @Path("/speakers")
    public List<User> getAllSpeakers() {
        // Liefert alle Benutzer, die die Rolle SPEAKER haben
        return User.list("role", "SPEAKER");
    }

    @PUT
    @Path("/talks/{id}")
    @Transactional
    public Response updateTalk(@PathParam("id") Long id, Talk updatedTalk) {
        Talk entity = Talk.findById(id);
        if (entity == null) return Response.status(404).build();

        // Optimistic Locking Check:
        // Wenn updatedTalk.version != entity.version, wirft Hibernate eine OptimisticLockException
        entity.title = updatedTalk.title;
        entity.abstractText = updatedTalk.abstractText;
        entity.targetAudience = updatedTalk.targetAudience;
        // entity.version wird automatisch hochgezählt

        return Response.ok(entity).build();
    }

    @PUT
    @Path("/participants/{userId}/priority")
    @Transactional
    public Response forceUpdatePriority(@PathParam("userId") Long userId, Priority newPrio) {
        // Suchen der spezifischen Prio-Verknüpfung
        Priority entity = Priority.find("participant.id = ?1 and talk.id = ?2",
                userId, newPrio.talk.id).firstResult();

        if (entity != null) {
            entity.priorityValue = newPrio.priorityValue;
            // Auch hier greift @Version automatisch
        }
        return Response.ok().build();
    }

    @GET
    @Path("/stats")
    public List<TalkStatDto> getStats() {
        // Aggregation: Zähle wie oft ein Talk in den Top 3 landet
        List<Talk> allTalks = Talk.listAll();

        return allTalks.stream().map(talk -> {
            // Zähle Prio 1 Stimmen
            long p1 = Priority.count("talk = ?1 and priorityValue = 1", talk);

            // Zähle Top 3 Stimmen (Prio 1, 2 oder 3)
            long top3 = Priority.count("talk = ?1 and priorityValue <= 3", talk);

            // Gesamtzahl der Stimmen für diesen Talk
            long total = Priority.count("talk = ?1", talk);

            return new TalkStatDto(talk.title, p1, top3, total);
        }).collect(Collectors.toList());
    }

    @GET
    @Path("/export/csv")
    @Produces(MediaType.TEXT_PLAIN)
    public Response exportCsv() {
        List<Priority> allPriorities = Priority.listAll();

        StreamingOutput stream = output -> {
            try (Writer writer = new BufferedWriter(new OutputStreamWriter(output, StandardCharsets.UTF_8))) {
                // Header-Zeile (mit Semikolon für Excel-Kompatibilität in DE)
                writer.write("Teilnehmer_Email;Nachname;Vorname;Organisation;Vortrag_Titel;Prioritaet;Zeitstempel\n");

                for (Priority p : allPriorities) {
                    String row = p.participant.email + ";" +
                            p.participant.lastName + ";" +
                            p.participant.firstName + ";" +
                            p.participant.organization + ";" +
                            p.talk.title.replace(";", ",") + ";" + // Semikolons im Titel escapen
                            p.priorityValue + ";" +
                            p.lastUpdated + "\n";

                    writer.write(row);
                }

                writer.flush();
            }
        };

        return Response.ok(stream)
                .header("Content-Disposition", "attachment; filename=event_prioritaeten.csv")
                .build();
    }
}