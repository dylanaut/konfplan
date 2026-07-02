package kreyj.konfplan.domain.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import kreyj.konfplan.adapter.in.web.dto.PlanQualitaetDto;
import kreyj.konfplan.adapter.in.web.dto.RaumBelegungUebersicht;
import kreyj.konfplan.adapter.in.web.dto.RaumDto;
import kreyj.konfplan.adapter.in.web.dto.RaumplanEintragDto;
import kreyj.konfplan.adapter.in.web.dto.ReferentVortragDto;
import kreyj.konfplan.adapter.in.web.dto.SlotDto;
import kreyj.konfplan.adapter.in.web.dto.TeilnehmerDto;
import kreyj.konfplan.adapter.in.web.dto.ZuweisungDto;
import kreyj.konfplan.persistence.IdEntity;
import kreyj.konfplan.persistence.Pflichtvortrag;
import kreyj.konfplan.persistence.Planungsergebnis;
import kreyj.konfplan.persistence.Raum;
import kreyj.konfplan.persistence.Referent;
import kreyj.konfplan.persistence.Slot;
import kreyj.konfplan.persistence.Teilnehmer;
import kreyj.konfplan.persistence.Veranstaltung;
import kreyj.konfplan.persistence.Vortrag;
import kreyj.konfplan.persistence.Wahlvortrag;
import org.apache.commons.lang3.ArrayUtils;
import org.jboss.logging.Logger;
import org.jspecify.annotations.NonNull;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.toMap;
import static kreyj.konfplan.adapter.in.web.dto.RaumBelegungUebersicht.VORTRAG_TITEL_FREI;
import static kreyj.konfplan.adapter.in.web.dto.RaumBelegungUebersicht.VORTRAG_TYP_FREI;
import static kreyj.konfplan.persistence.Planungsergebnis.getPlanungsergebnis;
import static kreyj.konfplan.util.TemplateExtensions.truncTo;

@ApplicationScoped
public class PlanService {
    private static final Logger LOG = Logger.getLogger(PlanService.class);

    public static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm"); // Made public for testing

    private final ObjectMapper objectMapper;


    public PlanService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }


    @Transactional
    public List<ZuweisungDto> getGesamtplan(Veranstaltung veranstaltung) {
        Objects.requireNonNull(veranstaltung);

        Map<Long, Map<Long, RaumplanEintragDto>> raumplan = getRaumbelegungsplan(veranstaltung);
        if (raumplan.isEmpty()) {
            return Collections.emptyList();
        }

        Map<Long, Slot> slotMap = veranstaltung.getSlots().stream()
            .collect(toMap(IdEntity::getId, Function.identity()));
        Map<Long, Raum> raumMap = veranstaltung.getRaeume().stream()
            .collect(toMap(IdEntity::getId, Function.identity()));

        List<ZuweisungDto> zuweisungen = new ArrayList<>();

        for (var raumEntry : raumplan.entrySet()) {
            Raum raum = raumMap.get(raumEntry.getKey());
            if (raum == null) {
                continue;
            }
            for (var slotEntry : raumEntry.getValue().entrySet()) {
                Slot slot = slotMap.get(slotEntry.getKey());
                RaumplanEintragDto eintrag = slotEntry.getValue();
                if (slot == null || eintrag.teilnehmer == null) {
                    continue;
                }
                for (TeilnehmerDto tn : eintrag.teilnehmer) {
                    zuweisungen.add(new ZuweisungDto(
                        tn.lastName,
                        eintrag.vortragTitel,
                        slot.getStartTime(),
                        slot.getEndTime(),
                        raum.getName(),
                        raum.getGebaeude().getName(),
                        eintrag.referentName
                    ));
                }
            }
        }

        LOG.info("Zuweisungen im Gesamtplan für Veranstaltung " + veranstaltung.getName() + ":\n" +
            zuweisungen.stream().map(ZuweisungDto::toString).collect(Collectors.joining("\n")));
        return zuweisungen;
    }


    @Transactional
    public List<RaumBelegungUebersicht> getDetaillierterPlan(Veranstaltung veranstaltung) {
        assert veranstaltung != null;
        Objects.requireNonNull(veranstaltung);

        // Nur wenn gar kein Plan existiert, leere Liste liefern. Existiert ein Ergebnis ohne
        // Zuweisungen, soll trotzdem das vollständige Raster (alle Plätze "FREI") gebaut werden.
        if (null == getPlanungsergebnis(veranstaltung)) {
            return Collections.emptyList();
        }
        Map<Long, Map<Long, RaumplanEintragDto>> raumplan = getRaumbelegungsplan(veranstaltung);

        List<RaumBelegungUebersicht> detaillierterPlan = new ArrayList<>();
        List<Slot> sortedSlots = veranstaltung.getSlots().stream()
            .sorted(comparing(Slot::getStartTime)).toList();
        List<Raum> sortedRaeume =
            veranstaltung.getRaeume().stream()
                .sorted(comparing((Raum r) -> r.getGebaeude().getName())
                    .thenComparing(Raum::getName))
                .toList();

        for (Slot slot : sortedSlots) {
            for (Raum raum : sortedRaeume) {
                RaumplanEintragDto eintrag = null;
                if (raumplan.containsKey(raum.getId())) {
                    eintrag = raumplan.get(raum.getId()).get(slot.getId());
                }

                if (eintrag != null) {
                    List<String> tnNamen = eintrag.teilnehmer != null
                        ? eintrag.teilnehmer.stream().map(TeilnehmerDto::getFullname).toList()
                        : new ArrayList<>();

                    detaillierterPlan.add(new RaumBelegungUebersicht(
                        slot.getId(),
                        slot.getStartTime().format(TIME_FORMAT),
                        raum.getId(),
                        raum.getName(),
                        eintrag.vortragTitel,
                        eintrag.referentName,
                        eintrag.vortragTyp,
                        tnNamen,
                        raum.getKapazitaet()
                    ));
                } else {
                    detaillierterPlan.add(new RaumBelegungUebersicht(
                        slot.getId(),
                        slot.getStartTime().format(TIME_FORMAT),
                        raum.getId(),
                        raum.getName(),
                        VORTRAG_TITEL_FREI,
                        null,
                        VORTRAG_TYP_FREI,
                        new ArrayList<>(),
                        raum.getKapazitaet()
                    ));
                }
            }
        }

        LOG.info("Detaillierter Plan für Veranstaltung " + veranstaltung.getName() + ":\n" +
            detaillierterPlan.stream()
                .map(RaumBelegungUebersicht::toString)
                .collect(Collectors.joining("\n")));

        return detaillierterPlan;
    }


    @Transactional
    public PlanQualitaetDto getPlanQualitaet(Veranstaltung veranstaltung) {
        Planungsergebnis planungsergebnis = Planungsergebnis.find("veranstaltung = ?1", veranstaltung).firstResult();
        if (null == planungsergebnis) {
            return new PlanQualitaetDto(0, 0, "Kein Ergebnis vorhanden");
        }

        try {
            JsonNode root = objectMapper.readTree(planungsergebnis.getJsonErgebnis());
            int kosten = root.has("kosten") ? root.get("kosten").asInt() : 0;
            int anzahlZuweisungen = root.has("zuweisungen") ? root.get("zuweisungen").asInt() : 0;
            String status = "Planerstellung abgeschlossen";

            return new PlanQualitaetDto(kosten, anzahlZuweisungen, status);
        } catch (Exception e) {
            LOG.error("Fehler beim Parsen der Planqualität für Veranstaltung " + veranstaltung.getName(), e);
            return new PlanQualitaetDto(0, 0, "Fehler beim Parsen");
        }
    }


    @Transactional
    public List<ZuweisungDto> getPlanFuerTeilnehmer(Teilnehmer teilnehmer, Veranstaltung veranstaltung) {
        Objects.requireNonNull(veranstaltung);
        if (null == teilnehmer) {
            return Collections.emptyList();
        }

        Planungsergebnis planungsergebnis = Planungsergebnis.find("veranstaltung = ?1", veranstaltung).firstResult();
        if (null == planungsergebnis) {
            return Collections.emptyList();
        }

        try {
            Planungsergebnis.MinizincResult result =
                getMinizincResult(planungsergebnis);

            long[] tnOids = result.teilnehmer_oids;
            long[] wvOids = result.wahlvortrag_oids;
            long[] slotOids = result.slot_oids;
            long[] raumOids = result.raum_oids;
            boolean[][][] besucht = result.besucht;
            int[][] instanzSlot = result.instanz_slot;
            int[][] instanzRaum = result.instanz_raum;

            Set<Vortrag> vVortraege = veranstaltung.getVortraege();
            Set<Slot> vSlots = veranstaltung.getSlots();
            List<Raum> vRaeume = veranstaltung.getRaeume();

            Map<Long, Vortrag> vortragMap = vVortraege.stream().collect(toMap(IdEntity::getId, Function.identity()));
            Map<Long, Slot> slotMap = vSlots.stream().collect(toMap(IdEntity::getId, Function.identity()));
            Map<Long, Raum> raumMap = vRaeume.stream().collect(toMap(IdEntity::getId, Function.identity()));

            int tnIdx = ArrayUtils.indexOf(tnOids, teilnehmer.getId());
            if (tnIdx == -1) {
                return Collections.emptyList();
            }

            List<ZuweisungDto> zuweisungen = new ArrayList<>();

            for (Pflichtvortrag pv : veranstaltung.getPflichtvortraege()) {
                Set<String> tnGruppe = teilnehmer.getGruppen();
                if (tnGruppe != null && tnGruppe.contains(pv.getPflichtgruppe())) {
                    zuweisungen.add(new ZuweisungDto(
                        teilnehmer.getLastName(),
                        pv.getTitel(),
                        pv.getPflichtslot().getStartTime(),
                        pv.getPflichtslot().getEndTime(),
                        pv.getPflichtraum().getName(),
                        pv.getPflichtraum().getGebaeude().getName(),
                        pv.getReferent().getLastName()
                    ));
                }
            }

            for (int wvIdx = 0; wvIdx < wvOids.length; wvIdx++) {
                Long vortragId = wvOids[wvIdx];
                Vortrag vortrag = vortragMap.get(vortragId);
                if (null == vortrag) {
                    continue;
                }

                for (int iIdx = 0; iIdx < besucht[tnIdx][wvIdx].length; iIdx++) {
                    if (besucht[tnIdx][wvIdx][iIdx]) {
                        int sIdx = instanzSlot[wvIdx][iIdx] - 1;
                        int rIdx = instanzRaum[wvIdx][iIdx] - 1;

                        if (sIdx >= 0 && sIdx < slotOids.length && rIdx >= 0 && rIdx < raumOids.length) {
                            long slotId = slotOids[sIdx];
                            long raumId = raumOids[rIdx];

                            Slot slot = slotMap.get(slotId);
                            Raum raum = raumMap.get(raumId);

                            if (slot != null && raum != null) {
                                zuweisungen.add(new ZuweisungDto(
                                    teilnehmer.getLastName(),
                                    vortrag.getTitel(),
                                    slot.getStartTime(),
                                    slot.getEndTime(),
                                    raum.getName(),
                                    raum.getGebaeude().getName(),
                                    vortrag.getReferent().getLastName()
                                ));
                            }
                        }
                    }
                }
            }
            return zuweisungen.stream()
                .sorted(comparing(d -> d.slotBeginn))
                .toList();

        } catch (Exception e) {
            LOG.error("Fehler beim Erstellen des Teilnehmerplans für " + teilnehmer.getEmail(), e);
            return Collections.emptyList();
        }
    }


    @Transactional
    public List<ReferentVortragDto> getPlanFuerReferent(Referent referent, Veranstaltung veranstaltung) {
        if (null == referent) {
            return Collections.emptyList();
        }

        Planungsergebnis planungsergebnis = Planungsergebnis.find("veranstaltung = ?1", veranstaltung).firstResult();
        if (null == planungsergebnis) {
            return Collections.emptyList();
        }

        try {
            Planungsergebnis.MinizincResult result =
                getMinizincResult(planungsergebnis);

            long[] tnOids = result.teilnehmer_oids;
            long[] wvOids = result.wahlvortrag_oids;
            long[] slotOids = result.slot_oids;
            long[] raumOids = result.raum_oids;
            boolean[][][] besucht = result.besucht;
            int[][] instanzSlot = result.instanz_slot;
            int[][] instanzRaum = result.instanz_raum;

            Map<Long, TeilnehmerDto> teilnehmerMap = veranstaltung.teilnehmer().stream()
                .collect(toMap(IdEntity::getId, TeilnehmerDto::from));
            Map<Long, SlotDto> slotMap = veranstaltung.getSlots().stream().collect(toMap(IdEntity::getId, SlotDto::from));
            Map<Long, RaumDto> raumMap = veranstaltung.getRaeume().stream().collect(toMap(IdEntity::getId, RaumDto::from));

            List<ReferentVortragDto> referentPlan = new ArrayList<>();

            List<Pflichtvortrag> pflichtvortraege = Pflichtvortrag.find("veranstaltung = ?1 and referent = ?2", veranstaltung, referent).list();
            for (Pflichtvortrag pv : pflichtvortraege) {
                List<Teilnehmer> gruppenTeilnehmer = Teilnehmer.getGruppenTeilnehmer(pv.getPflichtgruppe(), veranstaltung);
                List<TeilnehmerDto> teilnehmerDtos = gruppenTeilnehmer.stream()
                    .map(TeilnehmerDto::from)
                    .toList();
                referentPlan.add(new ReferentVortragDto(pv.getTitel(), pv.getPflichtslot().getStartTime(), pv.getPflichtslot().getEndTime(),
                    pv.getPflichtraum().getName(), pv.getPflichtraum().getGebaeude().getName(),
                    pv.getReferent().getLastName(), teilnehmerDtos));
            }

            List<Wahlvortrag> referentenWahlvortraege = Wahlvortrag.find("veranstaltung = ?1 and referent = ?2", veranstaltung, referent).list();
            for (Wahlvortrag wv : referentenWahlvortraege) {
                int wvIdx = ArrayUtils.indexOf(wvOids, wv.getId());
                if (wvIdx == -1) {
                    continue;
                }

                for (int iIdx = 0; iIdx < instanzSlot[wvIdx].length; iIdx++) {
                    int sIdx = instanzSlot[wvIdx][iIdx] - 1;
                    if (sIdx < 0) {
                        continue;
                    }

                    int rIdx = instanzRaum[wvIdx][iIdx] - 1;
                    long slotId = slotOids[sIdx];
                    long raumId = raumOids[rIdx];
                    SlotDto slot = slotMap.get(slotId);
                    RaumDto raum = raumMap.get(raumId);

                    if (slot != null && raum != null) {
                        List<TeilnehmerDto> zugewieseneTeilnehmer = new ArrayList<>();
                        for (int tnIdx = 0; tnIdx < tnOids.length; tnIdx++) {
                            if (besucht[tnIdx][wvIdx][iIdx]) {
                                TeilnehmerDto tnDto = teilnehmerMap.get(tnOids[tnIdx]);
                                if (tnDto != null) {
                                    zugewieseneTeilnehmer.add(tnDto);
                                }
                            }
                        }
                        referentPlan.add(new ReferentVortragDto(wv.getTitel(), slot.startTime, slot.endTime,
                            raum.name, raum.gebaeudeName,
                            wv.getReferent().getLastName(),
                            zugewieseneTeilnehmer));
                    }
                }
            }
            return referentPlan.stream()
                .sorted(comparing(d -> d.slotBeginn))
                .toList();

        } catch (Exception e) {
            LOG.error("Fehler beim Erstellen des Referentenplans für " + referent.getEmail(), e);
            return Collections.emptyList();
        }
    }


    @Transactional
    public Map<Long, Map<Long, RaumplanEintragDto>> getRaumbelegungsplan(Veranstaltung veranstaltung) {
        Planungsergebnis planungsergebnis = Planungsergebnis.find("veranstaltung = ?1", veranstaltung).firstResult();
        if (null == planungsergebnis) {
            return Collections.emptyMap();
        }

        try {
            Planungsergebnis.MinizincResult result =
                getMinizincResult(planungsergebnis);

            long[] tnOids = result.teilnehmer_oids;
            long[] wvOids = result.wahlvortrag_oids;
            long[] slotOids = result.slot_oids;
            long[] raumOids = result.raum_oids;
            boolean[][][] besucht = result.besucht;
            int[][] instanzSlot = result.instanz_slot;
            int[][] instanzRaum = result.instanz_raum;

            List<Teilnehmer> alleTeilnehmer = veranstaltung.teilnehmer();
            Set<Vortrag> alleVortraege = veranstaltung.getVortraege();
            Set<Slot> alleSlots = veranstaltung.getSlots();
            List<Raum> alleRaeume = veranstaltung.getRaeume();

            Map<Long, Teilnehmer> teilnehmerMap = alleTeilnehmer.stream().collect(toMap(IdEntity::getId, Function.identity()));
            Map<Long, Vortrag> vortragMap = alleVortraege.stream().collect(toMap(IdEntity::getId, Function.identity()));
            Map<Long, Slot> slotMap = alleSlots.stream().collect(toMap(IdEntity::getId, Function.identity()));
            Map<Long, Raum> raumMap = alleRaeume.stream().collect(toMap(IdEntity::getId, Function.identity()));

            Map<Long, Map<Long, RaumplanEintragDto>> raumplan = new HashMap<>();

            // Pflichtvorträge hinzufügen
            for (Pflichtvortrag pv : veranstaltung.getPflichtvortraege()) {
                Raum raum = pv.getPflichtraum();
                Slot slot = pv.getPflichtslot();

                List<Teilnehmer> gruppenTeilnehmer = Teilnehmer.getGruppenTeilnehmer(pv.getPflichtgruppe(), veranstaltung);
                List<TeilnehmerDto> teilnehmerDtos = gruppenTeilnehmer.stream()
                    .map(TeilnehmerDto::from)
                    .toList();

                RaumplanEintragDto eintrag = new RaumplanEintragDto(
                    slot.getId(),
                    slot.getSlotZeit(),
                    truncTo(pv.getTitel()),
                    pv.getReferent().getFullName(),
                    "PFLICHT",
                    teilnehmerDtos);
                raumplan.computeIfAbsent(raum.getId(), k -> new HashMap<>()).put(slot.getId(), eintrag);
            }

            // Wahlvorträge aus MiniZinc-Ergebnis hinzufügen
            for (int wIdx = 0; wIdx < wvOids.length; wIdx++) {
                Long vortragId = wvOids[wIdx];
                Vortrag vortrag = vortragMap.get(vortragId);
                if (null == vortrag) {
                    continue;
                }

                for (int iIdx = 0; iIdx < instanzSlot[wIdx].length; iIdx++) {
                    int sIdx = instanzSlot[wIdx][iIdx] - 1;
                    if (sIdx < 0) {
                        continue;
                    }

                    int rIdx = instanzRaum[wIdx][iIdx] - 1;
                    long slotId = slotOids[sIdx];
                    long raumId = raumOids[rIdx];
                    Slot slot = slotMap.get(slotId);
                    Raum raum = raumMap.get(raumId);

                    if (slot != null && raum != null) {
                        List<TeilnehmerDto> zugewieseneTeilnehmer = new ArrayList<>();
                        for (int pIdx = 0; pIdx < tnOids.length; pIdx++) {
                            if (besucht[pIdx][wIdx][iIdx]) {
                                Teilnehmer tn = teilnehmerMap.get(tnOids[pIdx]);
                                if (tn != null) {
                                    zugewieseneTeilnehmer.add(TeilnehmerDto.from(tn));
                                }
                            }
                        }
                        RaumplanEintragDto eintrag = new RaumplanEintragDto(
                            slot.getId(),
                            slot.getSlotZeit(),
                            vortrag.getTitel(),
                            vortrag.getReferent().getFullName(),
                            "WAHL",
                            zugewieseneTeilnehmer);
                        raumplan.computeIfAbsent(raum.getId(), k -> new HashMap<>()).put(slot.getId(), eintrag);
                    }
                }
            }

            LOG.info(raumplanDebug(veranstaltung, raumplan, raumMap, slotMap));

            return raumplan;

        } catch (Exception e) {
            LOG.error("Fehler beim Erstellen des Raumbelegungsplans für Veranstaltung " + veranstaltung.getName(), e);
            return Collections.emptyMap();
        }
    }


    @Transactional
    public Map<Long, List<Slot>> getFreieSlotsReferenten(Veranstaltung veranstaltung) {
        Map<Long, List<Slot>> freieSlotsReferenten = new HashMap<>();
        Planungsergebnis planungsergebnis = getPlanungsergebnis(veranstaltung);

        try {
            Planungsergebnis.MinizincResult result =
                getMinizincResult(planungsergebnis);

            long[] wvOids = result.wahlvortrag_oids;
            long[] slotOids = result.slot_oids;
            int[][] instanzSlot = result.instanz_slot;

            List<Referent> vReferenten = veranstaltung.referenten();
            Set<Slot> vSlots = veranstaltung.getSlots();
            List<Pflichtvortrag> pflichtvortraege = veranstaltung.getPflichtvortraege();
            List<Wahlvortrag> wahlvortraege = veranstaltung.getWahlvortraege();

            // "freie Slot"-Berechnung für alle Referenten
            for (Referent referent : vReferenten) {
                Set<Long> belegteSlotIdsFuerRef = new HashSet<>();

                // Pflichtvorträge des Referenten
                for (Pflichtvortrag pv : pflichtvortraege) {
                    if (pv.getReferent().equals(referent)) {
                        belegteSlotIdsFuerRef.add(pv.getPflichtslot().getId());
                    }
                }

                // Wahlvorträge des Referenten aus dem Planungsergebnis
                for (Wahlvortrag wv : wahlvortraege) {
                    if (wv.getReferent().equals(referent)) {
                        int wIdx = ArrayUtils.indexOf(wvOids, wv.getId());
                        if (wIdx == -1) {
                            continue;
                        }

                        for (int iIdx = 0; iIdx < instanzSlot[wIdx].length; iIdx++) {
                            int sIdx = instanzSlot[wIdx][iIdx] - 1;
                            if (sIdx >= 0 && sIdx < slotOids.length) {
                                belegteSlotIdsFuerRef.add(slotOids[sIdx]);
                            }
                        }
                    }
                }

                List<Slot> freieSlots = vSlots.stream()
                    .filter(slot -> !belegteSlotIdsFuerRef.contains(slot.getId()))
                    .sorted(comparing(Slot::getStartTime))
                    .toList();
                freieSlotsReferenten.put(referent.getId(), freieSlots);
            }

        } catch (Exception e) {
            LOG.error("Fehler beim Ermitteln freier Referenten-Slots für Veranstaltung " + veranstaltung, e);
        }
        return freieSlotsReferenten;
    }


    @Transactional
    public Map<Long, List<Slot>> getFreieSlotsTeilnehmer(Veranstaltung veranstaltung) {
        Map<Long, List<Slot>> freieSlotsTeilnehmer = new HashMap<>();
        Planungsergebnis planungsergebnis = Planungsergebnis.find("veranstaltung = ?1", veranstaltung).firstResult();
        if (null == planungsergebnis) {
            return Collections.emptyMap();
        }

        try {
            Planungsergebnis.MinizincResult result = getMinizincResult(planungsergebnis);

            long[] tnOids = result.teilnehmer_oids;
            long[] wvOids = result.wahlvortrag_oids;
            long[] slotOids = result.slot_oids;
            boolean[][][] besucht = result.besucht;
            int[][] instanzSlot = result.instanz_slot;

            List<Teilnehmer> vTeilnehmer = veranstaltung.teilnehmer();
            Set<Slot> alleSlots = veranstaltung.getSlots();
            List<Pflichtvortrag> pflichtvortraege = veranstaltung.getPflichtvortraege();

            for (Teilnehmer teilnehmer : vTeilnehmer) {
                Set<Long> belegteSlotIds = new HashSet<>();

                // Pflichtvorträge des Teilnehmers
                for (Pflichtvortrag pv : pflichtvortraege) {
                    Set<String> tnGruppe = teilnehmer.getGruppen();
                    if (tnGruppe != null && tnGruppe.contains(pv.getPflichtgruppe())) {
                        belegteSlotIds.add(pv.getPflichtslot().getId());
                    }
                }

                // Wahlvorträge des Teilnehmers aus dem Planungsergebnis
                int pIdx = ArrayUtils.indexOf(tnOids, teilnehmer.getId());
                if (pIdx != -1) {
                    for (int wIdx = 0; wIdx < wvOids.length; wIdx++) {
                        for (int iIdx = 0; iIdx < besucht[pIdx][wIdx].length; iIdx++) {
                            if (besucht[pIdx][wIdx][iIdx]) {
                                int sIdx = instanzSlot[wIdx][iIdx] - 1;
                                if (sIdx >= 0 && sIdx < slotOids.length) {
                                    belegteSlotIds.add(slotOids[sIdx]);
                                }
                            }
                        }
                    }
                }

                List<Slot> freieSlots = alleSlots.stream()
                    .filter(slot -> !belegteSlotIds.contains(slot.getId()))
                    .sorted(comparing(Slot::getStartTime))
                    .toList();
                freieSlotsTeilnehmer.put(teilnehmer.getId(), freieSlots);
            }

        } catch (Exception e) {
            LOG.error("Fehler beim Ermitteln freier Teilnehmer-Slots für Veranstaltung " + veranstaltung.getName(), e);
        }
        return freieSlotsTeilnehmer;
    }

    // -------------------------------------------------------------------
    // Helper methods
    // -------------------------------------------------------------------


    @Transactional
    public Planungsergebnis.MinizincResult getMinizincResult(Veranstaltung veranstaltung) {
        Objects.requireNonNull(veranstaltung, "veranstaltung must not be null");
        Planungsergebnis planungsergebnis = getPlanungsergebnis(veranstaltung);

        return getMinizincResult(planungsergebnis);

    }


    public Planungsergebnis.MinizincResult getMinizincResult(Planungsergebnis planungsergebnis) {
        Objects.requireNonNull(planungsergebnis, "planungsergebnis must not be null");

        try {
            return objectMapper.readValue(planungsergebnis.getJsonErgebnis(),
                Planungsergebnis.MinizincResult.class);
        } catch (JsonProcessingException e) {
            LOG.warn("Failed to parse Minizinc result for Veranstaltung" + planungsergebnis.getJsonErgebnis());
            throw new RuntimeException(e);
        }
    }


    private static @NonNull String raumplanDebug(Veranstaltung veranstaltung, Map<Long, Map<Long, RaumplanEintragDto>> raumplan
        , Map<Long, Raum> raumMap, Map<Long, Slot> slotMap) {
        StringBuilder sb =
            new StringBuilder("Raumbelegungsplan für Veranstaltung '%s':\n"
                .formatted(veranstaltung.getName()));

        raumplan.forEach((raumId, slotEntries) -> {
            sb.append("Raum ").append(raumMap.get(raumId).getName())
                .append(" (").append(raumId)
                .append("):\n");
            slotEntries.forEach((slotId, eintrag)
                -> sb.append("Slot ").append(slotMap.get(slotId).getDescription())
                .append(" (").append(slotId)
                .append("): ").append(eintrag).append("\n"));
        });

        return sb.toString();
    }
}
