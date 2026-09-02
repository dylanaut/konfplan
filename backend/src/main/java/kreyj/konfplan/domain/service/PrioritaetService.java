package kreyj.konfplan.domain.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.WebApplicationException;
import kreyj.konfplan.adapter.in.web.dto.TeilnehmerVortragPrioDto;
import kreyj.konfplan.adapter.in.web.dto.VortragPrioDto;
import kreyj.konfplan.application.port.in.PrioritaetServiceInterface;
import kreyj.konfplan.persistence.Nutzer;
import kreyj.konfplan.persistence.Prioritaet;
import kreyj.konfplan.persistence.Teilnehmer;
import kreyj.konfplan.persistence.Veranstaltung;
import kreyj.konfplan.persistence.Wahlvortrag;
import kreyj.konfplan.util.TemplateExtensions;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static jakarta.ws.rs.core.Response.Status.BAD_REQUEST;
import static jakarta.ws.rs.core.Response.Status.FORBIDDEN;
import static jakarta.ws.rs.core.Response.Status.NOT_FOUND;
import static kreyj.konfplan.persistence.Prioritaet.PRIO_MAX;
import static kreyj.konfplan.persistence.Prioritaet.PRIO_MIN;

@ApplicationScoped
public class PrioritaetService implements PrioritaetServiceInterface {

    @Transactional
    @Override
    public void savePrioritaeten(String loginName, List<VortragPrioDto> requests) {
        Nutzer nutzer = Nutzer.findByLoginName(loginName);
        if (!(nutzer instanceof Teilnehmer teilnehmer)) {
            throw new WebApplicationException("Nutzer ist kein Teilnehmer", BAD_REQUEST.getStatusCode());
        }

        // Deadline Check und Ermittlung der Veranstaltung (fuer die Maximal-Prioritaeten-Pruefung)
        Veranstaltung veranstaltung = null;
        if (!requests.isEmpty()) {
            Wahlvortrag v1 = Wahlvortrag.findById(requests.getFirst().vortragId);
            if (v1 != null) {
                veranstaltung = v1.getVeranstaltung();
                if (veranstaltung.getDeadlineTeilnehmer() != null && veranstaltung.getDeadlineTeilnehmer().isBefore(LocalDateTime.now())) {
                    throw new WebApplicationException("Die Deadline für Teilnehmer für diese Veranstaltung ist bereits abgelaufen.", FORBIDDEN.getStatusCode());
                }
            }
        }

        // 1. Validierung: Nur Werte 1-10 erlaubt
        List<VortragPrioDto> invalidRanges = requests.stream()
            .filter(r -> r.prioWert < PRIO_MIN || r.prioWert > PRIO_MAX)
            .toList();
        if (!invalidRanges.isEmpty()) {
            String invalids = invalidRanges.stream().map(req -> {
                    Wahlvortrag vortrag = Wahlvortrag.findById(req.vortragId);
                    return (vortrag != null ? TemplateExtensions.truncTo(vortrag.getTitel()) :
                        "Vortrag<???>") + " → " + req.prioWert;
                }
            ).collect(Collectors.joining("; "));
            throw new WebApplicationException("Prioritäten liegen nicht zwischen "
                + PRIO_MIN + " und " + PRIO_MAX + ": "
                + invalids, BAD_REQUEST.getStatusCode());
        }

        // Der Client sendet bewusst nur die seit dem letzten Speichern geaenderten Eintraege
        // (Delta), keinen Snapshot des gesamten Zustands (siehe TeilnehmerDashboard.vue
        // changedPriorities) - Duplikat- und Obergrenzen-Pruefung muessen deshalb den sich
        // ERGEBENDEN Gesamtzustand betrachten (bestehende, nicht im Request enthaltene
        // Prioritaeten bleiben unveraendert bestehen), nicht nur die im Request enthaltenen
        // Eintraege isoliert.
        Map<Long, Integer> ergebnisZustand = new HashMap<>();
        if (veranstaltung != null) {
            Long veranstaltungId = veranstaltung.getId();
            Prioritaet.<Prioritaet>list("teilnehmer = ?1", teilnehmer).stream()
                .filter(p -> p.getVortrag().getVeranstaltung().getId().equals(veranstaltungId))
                .forEach(p -> ergebnisZustand.put(p.getVortrag().getId(), p.getPrioWert()));
        }
        for (VortragPrioDto req : requests) {
            if (req.prioWert > PRIO_MIN) {
                ergebnisZustand.put(req.vortragId, req.prioWert);
            } else {
                ergebnisZustand.remove(req.vortragId);
            }
        }

        // 2. Validierung: konfigurierte Obergrenze fuer die Anzahl vergebener Prioritaeten im
        // resultierenden Gesamtzustand
        if (veranstaltung != null && veranstaltung.getMaxPrioritaeten() != null
            && ergebnisZustand.size() > veranstaltung.getMaxPrioritaeten()) {
            throw new WebApplicationException("Es dürfen höchstens " + veranstaltung.getMaxPrioritaeten()
                + " Prioritäten vergeben werden", BAD_REQUEST.getStatusCode());
        }

        // 3. Upsert je Eintrag: nur die im Request enthaltenen Vortraege werden veraendert, alle
        // anderen bestehenden Prioritaeten des Teilnehmers bleiben unangetastet.
        for (VortragPrioDto req : requests) {
            Wahlvortrag vortrag = Wahlvortrag.findById(req.vortragId);
            if (vortrag == null) {
                continue;
            }
            Prioritaet p = Prioritaet.find("teilnehmer = ?1 and vortrag = ?2", teilnehmer, vortrag).firstResult();
            if (req.prioWert == PRIO_MIN) {
                if (p != null) {
                    p.delete();
                }
                continue;
            }
            if (null == p) {
                p = new Prioritaet();
                p.setTeilnehmer(teilnehmer);
                p.setVortrag(vortrag);
            }
            p.setPrioWert(req.prioWert);
            p.persistAndFlush();
        }
    }


    @Transactional
    @Override
    public void updateSinglePrioritaet(Long userId, Long vortragId, int prioWert) {
        Teilnehmer teilnehmer = Teilnehmer.findById(userId);
        if (null == teilnehmer) {
            throw new WebApplicationException("Teilnehmer nicht gefunden", NOT_FOUND.getStatusCode());
        }

        Wahlvortrag vortrag = Wahlvortrag.findById(vortragId);
        if (null == vortrag) {
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
            return;
        }

        if (null == p) {
            p = new Prioritaet();
            p.setTeilnehmer(teilnehmer);
            p.setVortrag(vortrag);
        }
        p.setPrioWert(prioWert);
        p.persistAndFlush();
    }


    @Transactional
    @Override
    public Map<Long, Integer> getVortragPrioritaeten(Long nutzerId, Long veranstaltungId) {
        Objects.requireNonNull(nutzerId);
        Objects.requireNonNull(veranstaltungId);

        return Prioritaet
            .find("FROM Prioritaet p " +
                    "JOIN p.teilnehmer tn " +
                    "JOIN p.vortrag v " +
                    "JOIN v.veranstaltung e " +
                    "WHERE tn.id = ?1 AND e.id = ?2",
                nutzerId, veranstaltungId
            )
            .project(VortragPrioDto.class)
            .list().stream()
            .collect(Collectors.toMap(VortragPrioDto::getVortragId, VortragPrioDto::getPrioWert));
    }


    /**
     * Lädt die Prioritäten aller Teilnehmer einer Veranstaltung in einer einzigen Query,
     * statt sie (wie {@link #getVortragPrioritaeten}) pro Teilnehmer einzeln nachzuladen.
     */
    @Transactional
    public Map<Long, Map<Long, Integer>> getVortragPrioritaetenByVeranstaltung(Long veranstaltungId) {
        Objects.requireNonNull(veranstaltungId);

        Map<Long, Map<Long, Integer>> result = new HashMap<>();
        Prioritaet
            .find("FROM Prioritaet p JOIN p.vortrag v JOIN v.veranstaltung e WHERE e.id = ?1", veranstaltungId)
            .project(TeilnehmerVortragPrioDto.class)
            .<TeilnehmerVortragPrioDto>list()
            .forEach(row -> result.computeIfAbsent(row.teilnehmerId, k -> new HashMap<>())
                .put(row.vortragId, row.prioWert));
        return result;
    }
}
