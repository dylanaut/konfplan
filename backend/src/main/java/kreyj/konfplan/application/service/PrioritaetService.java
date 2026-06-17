package kreyj.konfplan.application.service;

import io.quarkus.hibernate.orm.panache.common.ProjectedFieldName;
import io.quarkus.runtime.annotations.RegisterForReflection;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.WebApplicationException;
import kreyj.konfplan.persistence.Nutzer;
import kreyj.konfplan.persistence.Prioritaet;
import kreyj.konfplan.persistence.Teilnehmer;
import kreyj.konfplan.persistence.Veranstaltung;
import kreyj.konfplan.persistence.Wahlvortrag;
import kreyj.konfplan.presentation.dto.PrioritaetRequest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static jakarta.ws.rs.core.Response.Status.BAD_REQUEST;
import static jakarta.ws.rs.core.Response.Status.FORBIDDEN;
import static jakarta.ws.rs.core.Response.Status.NOT_FOUND;

@ApplicationScoped
public class PrioritaetService {

    @Transactional
    public void savePrioritaeten(String email, List<PrioritaetRequest> requests) {
        Nutzer nutzer = Nutzer.findByEmail(email);
        if (!(nutzer instanceof Teilnehmer teilnehmer)) {
            throw new WebApplicationException("Nutzer ist kein Teilnehmer", BAD_REQUEST.getStatusCode());
        }

        // Deadline Check
        if (!requests.isEmpty()) {
            Wahlvortrag v1 = Wahlvortrag.findById(requests.getFirst().vortragId);
            if (v1 != null) {
                Veranstaltung v = v1.getVeranstaltung();
                if (v.getDeadlineTeilnehmer() != null && v.getDeadlineTeilnehmer().isBefore(LocalDateTime.now())) {
                    throw new WebApplicationException("Die Deadline für Teilnehmer für diese Veranstaltung ist bereits abgelaufen.", FORBIDDEN.getStatusCode());
                }
            }
        }

        // 1. Validierung: Nur Werte 1-10 erlaubt
        boolean invalidRange = requests.stream()
                .anyMatch(r -> r.prioWert < 1 || r.prioWert > 10); // Hier umbenannt
        if (invalidRange) {
            throw new WebApplicationException("Priorität muss zwischen 1 und 10 liegen", BAD_REQUEST.getStatusCode());
        }

        // 2. Validierung: Keine doppelten Prioritäten (Ranking-Check)
        long uniquePriorities = requests.stream()
                .map(r -> r.prioWert) // Hier umbenannt
                .distinct()
                .count();
        if (uniquePriorities < requests.size()) {
            throw new WebApplicationException("Jede Priorität darf nur einmal vergeben werden", BAD_REQUEST.getStatusCode());
        }

        // 3. Bestehende Prioritäten des Users löschen (Einfacher Update-Weg)
        Prioritaet.delete("teilnehmer", teilnehmer);

        // 4. Neue Prioritäten speichern
        for (PrioritaetRequest req : requests) {
            Wahlvortrag vortrag = Wahlvortrag.findById(req.vortragId);
            if (vortrag != null) {
                Prioritaet entity = new Prioritaet();
                entity.setTeilnehmer(teilnehmer);
                entity.setVortrag(vortrag);
                entity.setPrioWert(req.prioWert); // Hier umbenannt
                entity.persist();
            }
        }
    }


    @Transactional
    public void updateSinglePrioritaet(Long userId, Long vortragId, int prioWert) {
        Teilnehmer teilnehmer = Teilnehmer.findById(userId);
        if (teilnehmer == null) {
            throw new WebApplicationException("Teilnehmer nicht gefunden", NOT_FOUND.getStatusCode());
        }

        Wahlvortrag vortrag = Wahlvortrag.findById(vortragId);
        if (vortrag == null) {
            throw new WebApplicationException("Wahlvortrag nicht gefunden", NOT_FOUND.getStatusCode());
        }

        if (prioWert < 0 || prioWert > 10) {
            throw new WebApplicationException("Priorität muss zwischen 0 und 10 liegen", BAD_REQUEST.getStatusCode());
        }

        Prioritaet p = Prioritaet.find("teilnehmer = ?1 and vortrag = ?2", teilnehmer, vortrag).firstResult();

        if (prioWert == 0) {
            if (p != null) {
                p.delete();
            }
        }

        if (null == p) {
            p = new Prioritaet();
            p.setTeilnehmer(teilnehmer);
            p.setVortrag(vortrag);
        }
        p.setPrioWert(prioWert);
        p.persist();
    }


    @Transactional
    public List<Prioritaet> getNutzerPrioritaeten(String email) {
        Nutzer nutzer = Nutzer.findByEmail(email);

        if (!(nutzer instanceof Teilnehmer teilnehmer)) {
            throw new WebApplicationException("Nutzer ist kein Teilnehmer", BAD_REQUEST.getStatusCode());
        }

        return Prioritaet.list("teilnehmer", teilnehmer);
    }


    @Transactional
    public List<Prioritaet> getNutzerPrioritaeten(Long userId) {
        Nutzer nutzer = Nutzer.findById(userId);

        if (!(nutzer instanceof Teilnehmer teilnehmer)) {
            throw new WebApplicationException("Nutzer ist kein Teilnehmer", BAD_REQUEST.getStatusCode());
        }

        return Prioritaet.list("teilnehmer", teilnehmer);
    }


    @RegisterForReflection
    record VortragPrio(
            @ProjectedFieldName("vortrag.id")
            Long vortragId,
            @ProjectedFieldName("prioWert")
            Integer prioWert) {
    }


    @Transactional
    public Map<Long, Integer> getVortragPrioritaeten(Long nutzerId, Long veranstaltungId) {
        Objects.requireNonNull(nutzerId);
        Objects.requireNonNull(veranstaltungId);

        Map<Long, Integer> vortragIdToPrioWert = Prioritaet
                .find("FROM Prioritaet p " +
                                "JOIN p.teilnehmer tn " +
                                "JOIN p.vortrag v " +
                                "JOIN v.veranstaltung e " +
                                "WHERE tn.id = ?1 AND e.id = ?2",
                        nutzerId, veranstaltungId
                )
                .project(VortragPrio.class)
                .list().stream()
                .collect(Collectors.toMap(VortragPrio::vortragId, VortragPrio::prioWert));

        return vortragIdToPrioWert;
    }
}
