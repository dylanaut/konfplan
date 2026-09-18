package kreyj.konfplan.domain.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import kreyj.konfplan.adapter.in.web.dto.BetrachterTeilnehmerVortragDto;
import kreyj.konfplan.adapter.in.web.dto.NutzerVerfuegbarkeitDto;
import kreyj.konfplan.adapter.in.web.dto.RaumplanEintragDto;
import kreyj.konfplan.adapter.in.web.dto.TeilnehmerVortragZuweisungDto;
import kreyj.konfplan.adapter.in.web.dto.ZuweisungDto;
import kreyj.konfplan.persistence.Anwesenheit;
import kreyj.konfplan.persistence.Betrachter;
import kreyj.konfplan.persistence.IdEntity;
import kreyj.konfplan.persistence.NutzerVerfuegbarkeit;
import kreyj.konfplan.persistence.Teilnehmer;
import kreyj.konfplan.persistence.Veranstaltung;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static kreyj.konfplan.adapter.in.web.dto.RaumBelegungUebersicht.VORTRAG_TITEL_FREI;
import static kreyj.konfplan.domain.service.PlanService.TIME_FORMAT;

/**
 * Kapselt die Lese-Logik für die Rolle Betrachter (siehe #718): auf die einem Betrachter
 * zugewiesenen {@link kreyj.konfplan.persistence.GruppenkategorieWert}-Werte beschränkte Sicht
 * auf Teilnehmer, deren Verfügbarkeiten und Buchungen einer Veranstaltung.
 */
@ApplicationScoped
public class BetrachterService {

    private final PlanService planService;
    private final PrioritaetService prioritaetService;


    public BetrachterService(PlanService planService, PrioritaetService prioritaetService) {
        this.planService = planService;
        this.prioritaetService = prioritaetService;
    }


    @Transactional
    public List<Teilnehmer> getSichtbareTeilnehmer(Betrachter betrachter, Veranstaltung veranstaltung) {
        if (betrachter.getBetrachterGruppenwerte().isEmpty()) {
            return List.of();
        }
        return Teilnehmer.find(
            "SELECT DISTINCT tn FROM Teilnehmer tn JOIN tn.teilnehmerGruppenwerte gkw JOIN tn.veranstaltungen v "
                + "WHERE v = ?1 AND gkw IN ?2 AND tn.isActive = true",
            veranstaltung, betrachter.getBetrachterGruppenwerte()).list();
    }


    @Transactional
    public List<NutzerVerfuegbarkeitDto> getVerfuegbarkeiten(Betrachter betrachter, Veranstaltung veranstaltung) {
        List<Long> sichtbareIds = getSichtbareTeilnehmer(betrachter, veranstaltung).stream().map(IdEntity::getId).toList();
        if (sichtbareIds.isEmpty()) {
            return List.of();
        }
        return NutzerVerfuegbarkeit.<NutzerVerfuegbarkeit>find("nutzerId in ?1 and veranstaltungId = ?2", sichtbareIds, veranstaltung.getId())
            .list().stream()
            .map(NutzerVerfuegbarkeitDto::new)
            .toList();
    }


    /**
     * Buchungen je sichtbarem Teilnehmer - absichtlich ohne ergebnisId-Parameter: ein Betrachter
     * darf nie ein unveröffentlichtes Planungsergebnis einsehen (siehe
     * {@link PlanService#getPlanFuerTeilnehmer(Teilnehmer, Veranstaltung)}, das immer nur das
     * veröffentlichte Ergebnis liefert).
     */
    @Transactional
    public Map<Long, List<ZuweisungDto>> getZuweisungen(Betrachter betrachter, Veranstaltung veranstaltung) {
        List<Teilnehmer> sichtbareTeilnehmer = getSichtbareTeilnehmer(betrachter, veranstaltung);
        return sichtbareTeilnehmer.stream()
            .collect(Collectors.toMap(IdEntity::getId, t -> planService.getPlanFuerTeilnehmer(t, veranstaltung)));
    }


    /**
     * Vorträge-Subtabelle je sichtbarem Teilnehmer (siehe #737): verknüpft die geplante Zuweisung
     * (Pflichtvortrag, priorisierter oder Neigungs-aufgefüllter Wahlvortrag) mit der vom
     * Teilnehmer selbst vergebenen {@link kreyj.konfplan.persistence.Prioritaet} und dem per
     * QR-Code erfassten {@link Anwesenheit}sstatus. "Füll" wird angezeigt, wenn für einen
     * zugewiesenen Wahlvortrag keine eigene Prioritaet-Zeile existiert - der Solver kann einen
     * Wahlvortrag nur zuweisen, wenn entweder eine explizite Prioritaet (1-10) vorliegt, oder die
     * Zuweisung nachträglich per Neigungs-/Zufalls-Auffüllung erfolgte (siehe
     * AuffuellungService), was per Definition ohne Prioritaet-Zeile geschieht. Unangemeldete
     * Besuche (QR-Scan ohne zugehörige Zuweisung) erscheinen als eigene Zeile.
     */
    @Transactional
    public Map<Long, List<BetrachterTeilnehmerVortragDto>> getTeilnehmerVortraege(Betrachter betrachter, Veranstaltung veranstaltung) {
        List<Teilnehmer> sichtbareTeilnehmer = getSichtbareTeilnehmer(betrachter, veranstaltung);
        if (sichtbareTeilnehmer.isEmpty()) {
            return Map.of();
        }

        Map<Long, Map<Long, Integer>> prioritaetenByTeilnehmer = prioritaetService.getVortragPrioritaetenByVeranstaltung(veranstaltung.getId());

        Map<Long, List<Anwesenheit>> anwesenheitenByTeilnehmer = Anwesenheit.<Anwesenheit>find("veranstaltung", veranstaltung)
            .list().stream()
            .collect(Collectors.groupingBy(a -> a.getTeilnehmer().getId()));

        Map<Long, Map<Long, RaumplanEintragDto>> raumplan = planService.getRaumbelegungsplan(veranstaltung);

        Map<Long, List<BetrachterTeilnehmerVortragDto>> ergebnis = new HashMap<>();
        for (Teilnehmer teilnehmer : sichtbareTeilnehmer) {
            ergebnis.put(teilnehmer.getId(), ermittleVortraegeFuerTeilnehmer(
                planService.getPlanFuerTeilnehmerDetailliert(teilnehmer, veranstaltung),
                prioritaetenByTeilnehmer.getOrDefault(teilnehmer.getId(), Map.of()),
                anwesenheitenByTeilnehmer.getOrDefault(teilnehmer.getId(), List.of()),
                raumplan));
        }
        return ergebnis;
    }


    private record ZeileMitSortierschluessel(LocalDateTime slotBeginn, BetrachterTeilnehmerVortragDto dto) {
    }


    private List<BetrachterTeilnehmerVortragDto> ermittleVortraegeFuerTeilnehmer(
        List<TeilnehmerVortragZuweisungDto> geplant, Map<Long, Integer> eigenePrios,
        List<Anwesenheit> eigeneAnwesenheiten, Map<Long, Map<Long, RaumplanEintragDto>> raumplan) {

        Map<Long, Anwesenheit> anwesenheitBySlotId = eigeneAnwesenheiten.stream()
            .collect(Collectors.toMap(a -> a.getSlot().getId(), a -> a, (a, b) -> a));

        List<ZeileMitSortierschluessel> zeilen = new ArrayList<>();
        Set<Long> abgedeckteSlotIds = new HashSet<>();

        for (TeilnehmerVortragZuweisungDto z : geplant) {
            abgedeckteSlotIds.add(z.slotId);

            String prioAnzeige;
            if ("PFLICHT".equals(z.vortragTyp)) {
                prioAnzeige = "Pflicht";
            } else {
                Integer prio = eigenePrios.get(z.vortragId);
                prioAnzeige = null != prio ? String.valueOf(prio) : "Füll";
            }

            Anwesenheit besuch = anwesenheitBySlotId.get(z.slotId);
            String besuchtAnzeige = null != besuch
                ? besuch.getRaum().getName() + " · " + z.slotBeginn.format(TIME_FORMAT)
                : "nicht besucht";

            zeilen.add(new ZeileMitSortierschluessel(z.slotBeginn, new BetrachterTeilnehmerVortragDto(z.vortragTitel, prioAnzeige, besuchtAnzeige)));
        }

        for (Anwesenheit besuch : eigeneAnwesenheiten) {
            if (abgedeckteSlotIds.contains(besuch.getSlot().getId())) {
                continue;
            }
            RaumplanEintragDto eintrag = raumplan.getOrDefault(besuch.getRaum().getId(), Map.of()).get(besuch.getSlot().getId());
            String vortragTitel = null != eintrag ? eintrag.vortragTitel : VORTRAG_TITEL_FREI;
            String besuchtAnzeige = besuch.getRaum().getName() + " · " + besuch.getSlot().getStartTime().format(TIME_FORMAT);
            zeilen.add(new ZeileMitSortierschluessel(besuch.getSlot().getStartTime(), new BetrachterTeilnehmerVortragDto(vortragTitel, "unangemeldet", besuchtAnzeige)));
        }

        return zeilen.stream()
            .sorted(Comparator.comparing(ZeileMitSortierschluessel::slotBeginn))
            .map(ZeileMitSortierschluessel::dto)
            .toList();
    }
}
