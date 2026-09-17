package kreyj.konfplan.domain.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import kreyj.konfplan.adapter.in.web.dto.AnwesenheitAuswertungEintragDto;
import kreyj.konfplan.adapter.in.web.dto.AnwesenheitCheckinResultDto;
import kreyj.konfplan.adapter.in.web.dto.RaumBelegungUebersicht;
import kreyj.konfplan.adapter.in.web.dto.RaumplanEintragDto;
import kreyj.konfplan.adapter.in.web.dto.TeilnehmerDto;
import kreyj.konfplan.persistence.Anwesenheit;
import kreyj.konfplan.persistence.Raum;
import kreyj.konfplan.persistence.Slot;
import kreyj.konfplan.persistence.Teilnehmer;
import kreyj.konfplan.persistence.Veranstaltung;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Anwesenheitserfassung per QR-Code-Scan (siehe #735): Teilnehmer checken sich für einen Raum ein,
 * der aktuell aktive Slot der Veranstaltung wird serverseitig anhand der Uhrzeit bestimmt (der
 * QR-Code selbst kodiert nur Veranstaltung+Raum, keine Vortragsinstanz - siehe
 * {@link kreyj.konfplan.persistence.Planungsergebnis} dazu, warum eine Instanz keine über einen
 * Solver-Lauf hinweg stabile ID hat). Die Auswertung vergleicht die so gesammelten Check-ins erst
 * nachträglich gegen den zum Abfragezeitpunkt geplanten Zuweisungs-Stand
 * ({@link PlanService#getRaumbelegungsplan}).
 */
@ApplicationScoped
public class AnwesenheitService {

    /** Toleranz vor/nach dem eigentlichen Slot-Zeitfenster, in der ein Scan noch zählt. */
    private static final Duration SLOT_TOLERANZ = Duration.ofMinutes(5);

    private final PlanService planService;


    public AnwesenheitService(PlanService planService) {
        this.planService = planService;
    }


    @Transactional
    public AnwesenheitCheckinResultDto checkIn(Teilnehmer teilnehmer, Veranstaltung veranstaltung, Raum raum) {
        LocalDateTime jetzt = LocalDateTime.now();
        Slot aktiverSlot = findeAktivenSlot(veranstaltung, jetzt);
        if (null == aktiverSlot) {
            return AnwesenheitCheckinResultDto.keinAktiverTermin(raum.getName());
        }

        Anwesenheit vorhanden = Anwesenheit.findByTeilnehmerUndSlot(teilnehmer, aktiverSlot);
        if (null == vorhanden) {
            vorhanden = new Anwesenheit(teilnehmer, veranstaltung, aktiverSlot, raum, jetzt);
            vorhanden.persist();
        } else {
            vorhanden.setRaum(raum);
            vorhanden.setEingechecktAm(jetzt);
        }

        RaumplanEintragDto eintrag = planService.getRaumbelegungsplan(veranstaltung)
            .getOrDefault(raum.getId(), Map.of())
            .get(aktiverSlot.getId());

        String vortragTitel = null != eintrag ? eintrag.vortragTitel : null;
        return AnwesenheitCheckinResultDto.mitTermin(vortragTitel, raum.getName(), aktiverSlot.getStartTime().format(PlanService.TIME_FORMAT));
    }


    private Slot findeAktivenSlot(Veranstaltung veranstaltung, LocalDateTime jetzt) {
        return veranstaltung.getSlots().stream()
            .filter(slot -> !jetzt.isBefore(slot.getStartTime().minus(SLOT_TOLERANZ))
                && !jetzt.isAfter(slot.getEndTime().plus(SLOT_TOLERANZ)))
            .findFirst()
            .orElse(null);
    }


    @Transactional
    public List<AnwesenheitAuswertungEintragDto> getAuswertung(Veranstaltung veranstaltung, Long ergebnisId) {
        Map<Long, Map<Long, RaumplanEintragDto>> raumplan = planService.getRaumbelegungsplan(veranstaltung, ergebnisId);

        Map<String, List<Anwesenheit>> checkinsByRaumUndSlot = Anwesenheit.<Anwesenheit>find("veranstaltung", veranstaltung)
            .list().stream()
            .collect(Collectors.groupingBy(a -> schluessel(a.getSlot().getId(), a.getRaum().getId())));

        List<Slot> sortedSlots = veranstaltung.getSlots().stream()
            .sorted(Comparator.comparing(Slot::getStartTime)).toList();
        List<Raum> sortedRaeume = veranstaltung.getRaeume().stream()
            .sorted(Comparator.comparing((Raum r) -> r.getGebaeude().getName()).thenComparing(Raum::getName))
            .toList();

        List<AnwesenheitAuswertungEintragDto> auswertung = new ArrayList<>();
        for (Slot slot : sortedSlots) {
            for (Raum raum : sortedRaeume) {
                RaumplanEintragDto eintrag = raumplan.getOrDefault(raum.getId(), Map.of()).get(slot.getId());
                if (null == eintrag || RaumBelegungUebersicht.VORTRAG_TYP_FREI.equals(eintrag.vortragTyp)) {
                    continue;
                }

                List<TeilnehmerDto> geplant = null != eintrag.teilnehmer ? eintrag.teilnehmer : List.of();
                Set<Long> geplantIds = geplant.stream().map(t -> t.id).collect(Collectors.toCollection(LinkedHashSet::new));

                List<Anwesenheit> checkins = checkinsByRaumUndSlot.getOrDefault(schluessel(slot.getId(), raum.getId()), List.of());
                Map<Long, Teilnehmer> eingechecktById = checkins.stream()
                    .collect(Collectors.toMap(a -> a.getTeilnehmer().getId(), Anwesenheit::getTeilnehmer, (a, b) -> a));

                List<String> warDa = geplant.stream()
                    .filter(t -> eingechecktById.containsKey(t.id))
                    .map(TeilnehmerDto::getFullname)
                    .toList();
                List<String> fehlte = geplant.stream()
                    .filter(t -> !eingechecktById.containsKey(t.id))
                    .map(TeilnehmerDto::getFullname)
                    .toList();
                List<String> unangemeldet = eingechecktById.entrySet().stream()
                    .filter(e -> !geplantIds.contains(e.getKey()))
                    .map(e -> e.getValue().getFullName())
                    .toList();

                auswertung.add(new AnwesenheitAuswertungEintragDto(
                    slot.getId(), slot.getStartTime().format(PlanService.TIME_FORMAT),
                    raum.getId(), raum.getName(), eintrag.vortragTitel,
                    warDa, fehlte, unangemeldet));
            }
        }
        return auswertung;
    }


    private static String schluessel(Long slotId, Long raumId) {
        return slotId + ":" + raumId;
    }
}
