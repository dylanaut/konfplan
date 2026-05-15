package kreyj.konfplan.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lowagie.text.*;
import com.lowagie.text.Font;
import com.lowagie.text.Image;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import kreyj.konfplan.dto.*;
import kreyj.konfplan.persistence.*;
import org.jboss.logging.Logger;

import java.awt.*;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static java.util.stream.Collectors.toMap;

@ApplicationScoped
public class PlanService {

    private static final Logger LOG = Logger.getLogger(PlanService.class);
    public static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm"); // Made public for testing

    @Inject
    ObjectMapper objectMapper;

    @Transactional
    public List<ZuweisungDto> getGesamtplan(Long veranstaltungId) {
        Planungsergebnis planungsergebnis = Planungsergebnis.find("veranstaltung.id", veranstaltungId).firstResult();
        if (planungsergebnis == null) {
            return Collections.emptyList();
        }


        try {
            JsonNode root = objectMapper.readTree(planungsergebnis.jsonErgebnis);
            JsonNode instanzSlot = root.get("instanz_slot");
            JsonNode instanzRaum = root.get("instanz_raum");
            JsonNode besucht = root.get("besucht");

            if (instanzSlot == null || instanzRaum == null || besucht == null) {
                LOG.warn("MiniZinc-Ergebnis für Veranstaltung " + veranstaltungId + " unvollständig.");
                return Collections.emptyList();
            }

            List<Teilnehmer> alleTeilnehmer = Teilnehmer.find("SELECT t FROM Teilnehmer t JOIN t.veranstaltungen v WHERE v.id = ?1", veranstaltungId).list();
            List<Vortrag> alleVortraege = Vortrag.find("veranstaltung.id", veranstaltungId).list();
            List<EventSlot> alleSlots = EventSlot.find("veranstaltung.id", veranstaltungId).list();
            List<Raum> alleRaeume = Raum.listAll();

            Map<Long, Teilnehmer> teilnehmerMap = alleTeilnehmer.stream().collect(toMap(t -> t.id, t -> t));
            Map<Long, Vortrag> vortragMap = alleVortraege.stream().collect(toMap(v -> v.id, v -> v));
            Map<Long, EventSlot> slotMap = alleSlots.stream().collect(toMap(s -> s.id, s -> s));
            Map<Long, Raum> raumMap = alleRaeume.stream().collect(toMap(r -> r.id, r -> r));

            List<ZuweisungDto> zuweisungen = new ArrayList<>();

            JsonNode inputData = root.get("input_data");
            if (inputData == null) {
                LOG.warn("MiniZinc-Ergebnis enthält keine Input-Daten für OIDs.");
                return Collections.emptyList();
            }

            List<Long> tnOids = StreamSupport.stream(inputData.get("teilnehmer_oids").spliterator(), false)
                    .map(JsonNode::asLong).toList();
            List<Long> wvOids = StreamSupport.stream(inputData.get("wahlvortrag_oids").spliterator(), false)
                    .map(JsonNode::asLong).toList();
            List<Long> slotOids = StreamSupport.stream(inputData.get("slot_oids").spliterator(), false)
                    .map(JsonNode::asLong).toList();
            List<Long> raumOids = StreamSupport.stream(inputData.get("raum_oids").spliterator(), false)
                    .map(JsonNode::asLong).toList();

            List<Pflichtvortrag> pflichtvortraege = Pflichtvortrag.find("veranstaltung.id", veranstaltungId).list();
            for (Pflichtvortrag pv : pflichtvortraege) {
                for (Teilnehmer tn : alleTeilnehmer) {
                    zuweisungen.add(new ZuweisungDto(
                            null,
                            tn.lastName,
                            pv.titel,
                            pv.pflichtslot.startTime.format(TIME_FORMAT),
                            pv.pflichtraum.name,
                            pv.pflichtraum.gebaeude.name
                    ));
                }
            }

            for (int pIdx = 0; pIdx < tnOids.size(); pIdx++) {
                Long teilnehmerId = tnOids.get(pIdx);
                Teilnehmer teilnehmer = teilnehmerMap.get(teilnehmerId);
                if (teilnehmer == null) {
                    continue;
                }

                for (int wIdx = 0; wIdx < wvOids.size(); wIdx++) {
                    Long vortragId = wvOids.get(wIdx);
                    Vortrag vortrag = vortragMap.get(vortragId);
                    if (vortrag == null) {
                        continue;
                    }

                    for (int iIdx = 0; iIdx < besucht.get(pIdx).get(wIdx).size(); iIdx++) {
                        if (besucht.get(pIdx).get(wIdx).get(iIdx).asBoolean()) {
                            int sIdx = instanzSlot.get(wIdx).get(iIdx).asInt() - 1;
                            int rIdx = instanzRaum.get(wIdx).get(iIdx).asInt() - 1;

                            if (sIdx >= 0 && sIdx < slotOids.size() && rIdx >= 0 && rIdx < raumOids.size()) {
                                Long slotId = slotOids.get(sIdx);
                                Long raumId = raumOids.get(rIdx);

                                EventSlot slot = slotMap.get(slotId);
                                Raum raum = raumMap.get(raumId);

                                if (slot != null && raum != null) {
                                    zuweisungen.add(new ZuweisungDto(
                                            null,
                                            teilnehmer.lastName,
                                            vortrag.titel,
                                            slot.startTime.format(TIME_FORMAT),
                                            raum.name,
                                            raum.gebaeude.name
                                    ));
                                }
                            }
                        }
                    }
                }
            }
            return zuweisungen;

        } catch (Exception e) {
            LOG.error("Fehler beim Parsen des Planungsergebnisses für Veranstaltung " + veranstaltungId, e);
            return Collections.emptyList();
        }
    }

    @Transactional
    public List<RaumBelegungUebersichtDto> getDetaillierterPlan(Long veranstaltungId) {
        Map<Long, Map<Long, RaumplanEintragDto>> raumplan = getRaumbelegungsplan(veranstaltungId);
        List<RaumBelegungUebersichtDto> detaillierterPlan = new ArrayList<>();

        List<EventSlot> alleSlots = EventSlot.find("veranstaltung.id", veranstaltungId).list();
        List<Raum> alleRaeume = Raum.listAll();

        // Sortiere Slots und Räume für konsistente Ausgabe
        alleSlots.sort(Comparator.comparing(s -> s.startTime));
        alleRaeume.sort(Comparator.comparing(r -> r.name));

        for (EventSlot slot : alleSlots) {
            for (Raum raum : alleRaeume) {
                RaumplanEintragDto eintrag = null;
                if (raumplan.containsKey(raum.id)) {
                    eintrag = raumplan.get(raum.id).get(slot.id);
                }

                if (eintrag != null) {
                    List<String> tnNamen = eintrag.teilnehmer != null ?
                            eintrag.teilnehmer.stream().map(t -> t.firstName + " " + t.lastName).collect(Collectors.toList()) :
                            new ArrayList<>();

                    detaillierterPlan.add(new RaumBelegungUebersichtDto(
                            slot.id,
                            slot.startTime.format(TIME_FORMAT),
                            raum.id,
                            raum.name,
                            eintrag.vortragTitel,
                            eintrag.referentName,
                            eintrag.vortragTyp,
                            tnNamen,
                            raum.kapazitaet
                    ));
                } else {
                    detaillierterPlan.add(new RaumBelegungUebersichtDto(
                            slot.id,
                            slot.startTime.format(TIME_FORMAT),
                            raum.id,
                            raum.name,
                            "Frei",
                            null,
                            "FREI",
                            new ArrayList<>(),
                            raum.kapazitaet
                    ));
                }
            }
        }
        return detaillierterPlan;
    }

    @Transactional
    public PlanQualitaetDto getPlanQualitaet(Long veranstaltungId) {
        Planungsergebnis planungsergebnis = Planungsergebnis.find("veranstaltung.id", veranstaltungId).firstResult();
        if (planungsergebnis == null) {
            return new PlanQualitaetDto(0, 0, "Kein Ergebnis vorhanden");
        }

        try {
            JsonNode root = objectMapper.readTree(planungsergebnis.jsonErgebnis);
            int kosten = root.has("kosten") ? root.get("kosten").asInt() : 0;
            int anzahlZuweisungen = root.has("zuweisungen") ? root.get("zuweisungen").asInt() : 0;
            String status = "Optimierung abgeschlossen";

            return new PlanQualitaetDto(kosten, anzahlZuweisungen, status);
        } catch (Exception e) {
            LOG.error("Fehler beim Parsen der Planqualität für Veranstaltung " + veranstaltungId, e);
            return new PlanQualitaetDto(0, 0, "Fehler beim Parsen");
        }
    }

    @Transactional
    public List<ZuweisungDto> getPlanFuerTeilnehmer(String email, Long veranstaltungId) {
        Teilnehmer teilnehmer = Teilnehmer.find("email", email).firstResult();
        if (teilnehmer == null) {
            return Collections.emptyList();
        }

        Planungsergebnis planungsergebnis = Planungsergebnis.find("veranstaltung.id", veranstaltungId).firstResult();
        if (planungsergebnis == null) {
            return Collections.emptyList();
        }

        try {
            JsonNode root = objectMapper.readTree(planungsergebnis.jsonErgebnis);
            JsonNode inputData = root.get("input_data");
            JsonNode instanzSlot = root.get("instanz_slot");
            JsonNode instanzRaum = root.get("instanz_raum");
            JsonNode besucht = root.get("besucht");

            if (inputData == null || instanzSlot == null || instanzRaum == null || besucht == null) {
                LOG.warn("MiniZinc-Ergebnis für Veranstaltung " + veranstaltungId + " unvollständig.");
                return Collections.emptyList();
            }

            List<Long> tnOids = StreamSupport.stream(inputData.get("teilnehmer_oids").spliterator(), false)
                    .map(JsonNode::asLong).toList();
            List<Long> wvOids = StreamSupport.stream(inputData.get("wahlvortrag_oids").spliterator(), false)
                    .map(JsonNode::asLong).toList();
            List<Long> slotOids = StreamSupport.stream(inputData.get("slot_oids").spliterator(), false)
                    .map(JsonNode::asLong).toList();
            List<Long> raumOids = StreamSupport.stream(inputData.get("raum_oids").spliterator(), false)
                    .map(JsonNode::asLong).toList();

            Map<Long, Vortrag> vortragMap = Vortrag.find("veranstaltung.id", veranstaltungId).<Vortrag>list().stream().collect(toMap(v -> v.id, v -> v));
            Map<Long, EventSlot> slotMap = EventSlot.find("veranstaltung.id", veranstaltungId).<EventSlot>list().stream().collect(toMap(s -> s.id, s -> s));
            Map<Long, Raum> raumMap = Raum.<Raum>listAll().stream().collect(toMap(r -> r.id, r -> r));

            int pIdx = tnOids.indexOf(teilnehmer.id);
            if (pIdx == -1) {
                return Collections.emptyList();
            }

            List<ZuweisungDto> zuweisungen = new ArrayList<>();

            List<Pflichtvortrag> pflichtvortraege = Pflichtvortrag.find("veranstaltung.id", veranstaltungId).list();
            for (Pflichtvortrag pv : pflichtvortraege) {
                if (teilnehmer.gruppe != null && teilnehmer.gruppe.equals(pv.pflichtgruppe)) {
                    zuweisungen.add(new ZuweisungDto(
                            null,
                            teilnehmer.lastName,
                            pv.titel,
                            pv.pflichtslot.startTime.format(TIME_FORMAT),
                            pv.pflichtraum.name,
                            pv.pflichtraum.gebaeude.name
                    ));
                }
            }

            for (int wIdx = 0; wIdx < wvOids.size(); wIdx++) {
                Long vortragId = wvOids.get(wIdx);
                Vortrag vortrag = vortragMap.get(vortragId);
                if (vortrag == null) {
                    continue;
                }

                for (int iIdx = 0; iIdx < besucht.get(pIdx).get(wIdx).size(); iIdx++) {
                    if (besucht.get(pIdx).get(wIdx).get(iIdx).asBoolean()) {
                        int sIdx = instanzSlot.get(wIdx).get(iIdx).asInt() - 1;
                        int rIdx = instanzRaum.get(wIdx).get(iIdx).asInt() - 1;

                        if (sIdx >= 0 && sIdx < slotOids.size() && rIdx >= 0 && rIdx < raumOids.size()) {
                            Long slotId = slotOids.get(sIdx);
                            Long raumId = raumOids.get(rIdx);

                            EventSlot slot = slotMap.get(slotId);
                            Raum raum = raumMap.get(raumId);

                            if (slot != null && raum != null) {
                                zuweisungen.add(new ZuweisungDto(
                                        null,
                                        teilnehmer.lastName,
                                        vortrag.titel,
                                        slot.startTime.format(TIME_FORMAT),
                                        raum.name,
                                        raum.gebaeude.name
                                ));
                            }
                        }
                    }
                }
            }
            return zuweisungen.stream()
                    .sorted(Comparator.comparing(ZuweisungDto::getSlotZeit))
                    .collect(Collectors.toList());

        } catch (Exception e) {
            LOG.error("Fehler beim Erstellen des Teilnehmerplans für " + email, e);
            return Collections.emptyList();
        }
    }

    @Transactional
    public List<ReferentVortragDto> getPlanFuerReferent(String email, Long veranstaltungId) {
        Referent referent = Referent.find("email", email).firstResult();
        if (referent == null) {
            return Collections.emptyList();
        }

        Planungsergebnis planungsergebnis = Planungsergebnis.find("veranstaltung.id", veranstaltungId).firstResult();
        if (planungsergebnis == null) {
            return Collections.emptyList();
        }

        try {
            JsonNode root = objectMapper.readTree(planungsergebnis.jsonErgebnis);
            JsonNode inputData = root.get("input_data");
            JsonNode instanzSlot = root.get("instanz_slot");
            JsonNode instanzRaum = root.get("instanz_raum");
            JsonNode besucht = root.get("besucht");

            if (inputData == null || instanzSlot == null || instanzRaum == null || besucht == null) {
                LOG.warn("MiniZinc-Ergebnis für Veranstaltung " + veranstaltungId + " unvollständig.");
                return Collections.emptyList();
            }

            List<Long> tnOids = StreamSupport.stream(inputData.get("teilnehmer_oids").spliterator(), false).map(JsonNode::asLong).toList();
            List<Long> wvOids = StreamSupport.stream(inputData.get("wahlvortrag_oids").spliterator(), false).map(JsonNode::asLong).toList();
            List<Long> slotOids = StreamSupport.stream(inputData.get("slot_oids").spliterator(), false).map(JsonNode::asLong).toList();
            List<Long> raumOids = StreamSupport.stream(inputData.get("raum_oids").spliterator(), false).map(JsonNode::asLong).toList();

            Map<Long, Teilnehmer> teilnehmerMap = Teilnehmer.find("SELECT t FROM Teilnehmer t JOIN t.veranstaltungen v WHERE v.id = ?1", veranstaltungId).<Teilnehmer>list().stream().collect(toMap(t -> t.id, t -> t));
            Map<Long, EventSlot> slotMap = EventSlot.find("veranstaltung.id", veranstaltungId).<EventSlot>list().stream().collect(toMap(s -> s.id, s -> s));
            Map<Long, Raum> raumMap = Raum.<Raum>listAll().stream().collect(toMap(r -> r.id, r -> r));

            List<ReferentVortragDto> referentPlan = new ArrayList<>();

            List<Pflichtvortrag> pflichtvortraege = Pflichtvortrag.find("veranstaltung.id = ?1 and referent.id = ?2", veranstaltungId, referent.id).list();
            for (Pflichtvortrag pv : pflichtvortraege) {
                List<Teilnehmer> gruppenTeilnehmer = Teilnehmer.find("SELECT t FROM Teilnehmer t JOIN t.veranstaltungen v WHERE v.id = ?1 AND t.gruppe = ?2", veranstaltungId, pv.pflichtgruppe).list();
                List<TeilnehmerSimpleDto> teilnehmerDtos = gruppenTeilnehmer.stream()
                        .map(tn -> new TeilnehmerSimpleDto(tn.id, tn.firstName, tn.lastName, tn.gruppe))
                        .collect(Collectors.toList());
                referentPlan.add(new ReferentVortragDto(pv.titel, pv.pflichtslot.startTime.format(TIME_FORMAT), pv.pflichtraum.name, pv.pflichtraum.gebaeude.name, teilnehmerDtos));
            }

            List<Wahlvortrag> referentenWahlvortraege = Wahlvortrag.find("veranstaltung.id = ?1 and referent.id = ?2", veranstaltungId, referent.id).list();
            for (Wahlvortrag wv : referentenWahlvortraege) {
                int wIdx = wvOids.indexOf(wv.id);
                if (wIdx == -1) {
                    continue;
                }

                for (int iIdx = 0; iIdx < instanzSlot.get(wIdx).size(); iIdx++) {
                    int sIdx = instanzSlot.get(wIdx).get(iIdx).asInt() - 1;
                    if (sIdx < 0) {
                        continue;
                    }

                    int rIdx = instanzRaum.get(wIdx).get(iIdx).asInt() - 1;
                    Long slotId = slotOids.get(sIdx);
                    Long raumId = raumOids.get(rIdx);
                    EventSlot slot = slotMap.get(slotId);
                    Raum raum = raumMap.get(raumId);

                    if (slot != null && raum != null) {
                        List<TeilnehmerSimpleDto> zugewieseneTeilnehmer = new ArrayList<>();
                        for (int pIdx = 0; pIdx < tnOids.size(); pIdx++) {
                            if (besucht.get(pIdx).get(wIdx).get(iIdx).asBoolean()) {
                                Teilnehmer tn = teilnehmerMap.get(tnOids.get(pIdx));
                                if (tn != null) {
                                    zugewieseneTeilnehmer.add(new TeilnehmerSimpleDto(tn.id, tn.firstName, tn.lastName, tn.gruppe));
                                }
                            }
                        }
                        referentPlan.add(new ReferentVortragDto(wv.titel, slot.startTime.format(TIME_FORMAT), raum.name, raum.gebaeude.name, zugewieseneTeilnehmer));
                    }
                }
            }
            return referentPlan.stream()
                    .sorted(Comparator.comparing(ReferentVortragDto::getSlotZeit))
                    .collect(Collectors.toList());

        } catch (Exception e) {
            LOG.error("Fehler beim Erstellen des Referentenplans für " + email, e);
            return Collections.emptyList();
        }
    }

    @Transactional
    public Map<Long, Map<Long, RaumplanEintragDto>> getRaumbelegungsplan(Long veranstaltungId) {
        Planungsergebnis planungsergebnis = Planungsergebnis.find("veranstaltung.id", veranstaltungId).firstResult();
        if (planungsergebnis == null) {
            return Collections.emptyMap();
        }

        try {
            // Explicitly materialize the String from the LOB to avoid issues with deferred access
            String jsonContent = planungsergebnis.jsonErgebnis;
            JsonNode root = objectMapper.readTree(jsonContent);

            JsonNode instanzSlot = root.get("instanz_slot");
            JsonNode instanzRaum = root.get("instanz_raum");
            JsonNode besucht = root.get("besucht");

            if (instanzSlot == null || instanzRaum == null || besucht == null) {
                LOG.warn("MiniZinc-Ergebnis für Veranstaltung " + veranstaltungId + " unvollständig.");
                return Collections.emptyMap();
            }


            List<Teilnehmer> alleTeilnehmer = Teilnehmer.find("SELECT t FROM Teilnehmer t JOIN t.veranstaltungen v WHERE v.id = ?1", veranstaltungId).list();
            List<Vortrag> alleVortraege = Vortrag.find("veranstaltung.id", veranstaltungId).list();
            List<EventSlot> alleSlots = EventSlot.find("veranstaltung.id", veranstaltungId).list();
            List<Raum> alleRaeume = Raum.listAll();

            Map<Long, Teilnehmer> teilnehmerMap = alleTeilnehmer.stream().collect(toMap(t -> t.id, t -> t));
            Map<Long, Vortrag> vortragMap = alleVortraege.stream().collect(toMap(v -> v.id, v -> v));
            Map<Long, EventSlot> slotMap = alleSlots.stream().collect(toMap(s -> s.id, s -> s));
            Map<Long, Raum> raumMap = alleRaeume.stream().collect(toMap(r -> r.id, r -> r));

            JsonNode inputData = root.get("input_data");
            if (inputData == null) {
                LOG.warn("MiniZinc-Ergebnis enthält keine Input-Daten für OIDs.");
                return Collections.emptyMap();
            }

            List<Long> tnOids = StreamSupport.stream(inputData.get("teilnehmer_oids").spliterator(), false).map(JsonNode::asLong).toList();
            List<Long> wvOids = StreamSupport.stream(inputData.get("wahlvortrag_oids").spliterator(), false).map(JsonNode::asLong).toList();
            List<Long> slotOids = StreamSupport.stream(inputData.get("slot_oids").spliterator(), false).map(JsonNode::asLong).toList();
            List<Long> raumOids = StreamSupport.stream(inputData.get("raum_oids").spliterator(), false).map(JsonNode::asLong).toList();

            Map<Long, Map<Long, RaumplanEintragDto>> raumplan = new HashMap<>();

            // Pflichtvorträge hinzufügen
            List<Pflichtvortrag> pflichtvortraege = Pflichtvortrag.find("veranstaltung.id", veranstaltungId).list();
            for (Pflichtvortrag pv : pflichtvortraege) {
                Raum raum = pv.pflichtraum;
                EventSlot slot = pv.pflichtslot;

                List<Teilnehmer> gruppenTeilnehmer = Teilnehmer.find("SELECT t FROM Teilnehmer t JOIN t.veranstaltungen v WHERE v.id = ?1 AND t.gruppe = ?2", veranstaltungId, pv.pflichtgruppe).list();
                List<TeilnehmerSimpleDto> teilnehmerDtos = gruppenTeilnehmer.stream()
                        .map(tn -> new TeilnehmerSimpleDto(tn.id, tn.firstName, tn.lastName, tn.gruppe))
                        .toList();

                RaumplanEintragDto eintrag = new RaumplanEintragDto(
                        slot.id,
                        slot.startTime.format(TIME_FORMAT),
                        pv.titel,
                        ((Vortrag) pv).referent.firstName + " " + ((Vortrag) pv).referent.lastName,
                        "PFLICHT",
                        teilnehmerDtos.size(),
                        teilnehmerDtos
                );
                raumplan.computeIfAbsent(raum.id, k -> new HashMap<>()).put(slot.id, eintrag);
            }


            // Wahlvorträge aus MiniZinc-Ergebnis hinzufügen
            for (int wIdx = 0; wIdx < wvOids.size(); wIdx++) {
                Long vortragId = wvOids.get(wIdx);
                Vortrag vortrag = vortragMap.get(vortragId);
                if (vortrag == null) {
                    continue;
                }

                for (int iIdx = 0; iIdx < instanzSlot.get(wIdx).size(); iIdx++) {
                    int sIdx = instanzSlot.get(wIdx).get(iIdx).asInt() - 1;
                    if (sIdx < 0) {
                        continue;
                    }

                    int rIdx = instanzRaum.get(wIdx).get(iIdx).asInt() - 1;
                    Long slotId = slotOids.get(sIdx);
                    Long raumId = raumOids.get(rIdx);
                    EventSlot slot = slotMap.get(slotId);
                    Raum raum = raumMap.get(raumId);

                    if (slot != null && raum != null) {
                        List<TeilnehmerSimpleDto> zugewieseneTeilnehmer = new ArrayList<>();
                        for (int pIdx = 0; pIdx < tnOids.size(); pIdx++) {
                            if (besucht.get(pIdx).get(wIdx).get(iIdx).asBoolean()) {
                                Teilnehmer tn = teilnehmerMap.get(tnOids.get(pIdx));
                                if (tn != null) {
                                    zugewieseneTeilnehmer.add(new TeilnehmerSimpleDto(tn.id, tn.firstName, tn.lastName, tn.gruppe));
                                }
                            }
                        }
                        RaumplanEintragDto eintrag = new RaumplanEintragDto(
                                slot.id,
                                slot.startTime.format(TIME_FORMAT),
                                vortrag.titel,
                                vortrag.referent.firstName + " " + vortrag.referent.lastName,
                                "WAHL",
                                zugewieseneTeilnehmer.size(),
                                zugewieseneTeilnehmer
                        );
                        raumplan.computeIfAbsent(raum.id, k -> new HashMap<>()).put(slot.id, eintrag);
                    }
                }
            }
            return raumplan;

        } catch (Exception e) {
            LOG.error("Fehler beim Erstellen des Raumbelegungsplans für Veranstaltung " + veranstaltungId, e);
            return Collections.emptyMap();
        }
    }

    @Transactional
    public Map<Long, List<EventSlot>> getFreieSlotsReferenten(Long veranstaltungId) {
        Map<Long, List<EventSlot>> freieSlotsReferenten = new HashMap<>();
        Planungsergebnis planungsergebnis = Planungsergebnis.find("veranstaltung.id", veranstaltungId).firstResult();
        if (planungsergebnis == null) {
            return Collections.emptyMap();
        }

        try {
            JsonNode root = objectMapper.readTree(planungsergebnis.jsonErgebnis);
            JsonNode instanzSlot = root.get("instanz_slot");
            JsonNode wvOidsNode = root.get("input_data").get("wahlvortrag_oids");
            JsonNode slotOidsNode = root.get("input_data").get("slot_oids");

            List<Long> wvOids = StreamSupport.stream(wvOidsNode.spliterator(), false).map(JsonNode::asLong).toList();
            List<Long> slotOids = StreamSupport.stream(slotOidsNode.spliterator(), false).map(JsonNode::asLong).toList();

            List<Referent> alleReferenten = Referent.find("SELECT r FROM Referent r JOIN r.veranstaltungen v WHERE v.id = ?1", veranstaltungId).list();
            List<Vortrag> alleVortraege = Vortrag.find("veranstaltung.id", veranstaltungId).list();
            List<EventSlot> alleSlots = EventSlot.find("veranstaltung.id", veranstaltungId).list();

            Map<Long, Vortrag> vortragMap = alleVortraege.stream().collect(toMap(v -> v.id, v -> v));
            Map<Long, EventSlot> slotMap = alleSlots.stream().collect(toMap(s -> s.id, s -> s));

            for (Referent referent : alleReferenten) {
                Set<Long> belegteSlotIds = new HashSet<>();

                // Pflichtvorträge des Referenten
                List<Pflichtvortrag> pflichtvortraege = Pflichtvortrag.find("veranstaltung.id = ?1 and referent.id = ?2", veranstaltungId, referent.id).list();
                for (Pflichtvortrag pv : pflichtvortraege) {
                    belegteSlotIds.add(pv.pflichtslot.id);
                }

                // Wahlvorträge des Referenten aus dem Planungsergebnis
                List<Wahlvortrag> referentenWahlvortraege = Wahlvortrag.find("veranstaltung.id = ?1 and referent.id = ?2", veranstaltungId, referent.id).list();
                for (Wahlvortrag wv : referentenWahlvortraege) {
                    int wIdx = wvOids.indexOf(wv.id);
                    if (wIdx == -1) {
                        continue;
                    }

                    for (int iIdx = 0; iIdx < instanzSlot.get(wIdx).size(); iIdx++) {
                        int sIdx = instanzSlot.get(wIdx).get(iIdx).asInt() - 1;
                        if (sIdx >= 0 && sIdx < slotOids.size()) {
                            belegteSlotIds.add(slotOids.get(sIdx));
                        }
                    }
                }

                List<EventSlot> freieSlots = alleSlots.stream()
                        .filter(slot -> !belegteSlotIds.contains(slot.id))
                        .sorted(Comparator.comparing(s -> s.startTime))
                        .collect(Collectors.toList());
                freieSlotsReferenten.put(referent.id, freieSlots);
            }

        } catch (Exception e) {
            LOG.error("Fehler beim Ermitteln freier Referenten-Slots für Veranstaltung " + veranstaltungId, e);
        }
        return freieSlotsReferenten;
    }

    @Transactional
    public Map<Long, List<EventSlot>> getFreieSlotsTeilnehmer(Long veranstaltungId) {
        Map<Long, List<EventSlot>> freieSlotsTeilnehmer = new HashMap<>();
        Planungsergebnis planungsergebnis = Planungsergebnis.find("veranstaltung.id", veranstaltungId).firstResult();
        if (planungsergebnis == null) {
            return Collections.emptyMap();
        }

        try {
            JsonNode root = objectMapper.readTree(planungsergebnis.jsonErgebnis);
            JsonNode besucht = root.get("besucht");
            JsonNode tnOidsNode = root.get("input_data").get("teilnehmer_oids");
            JsonNode wvOidsNode = root.get("input_data").get("wahlvortrag_oids");
            JsonNode slotOidsNode = root.get("input_data").get("slot_oids");

            List<Long> tnOids = StreamSupport.stream(tnOidsNode.spliterator(), false).map(JsonNode::asLong).toList();
            List<Long> wvOids = StreamSupport.stream(wvOidsNode.spliterator(), false).map(JsonNode::asLong).toList();
            List<Long> slotOids = StreamSupport.stream(slotOidsNode.spliterator(), false).map(JsonNode::asLong).toList();

            List<Teilnehmer> alleTeilnehmer = Teilnehmer.find("SELECT t FROM Teilnehmer t JOIN t.veranstaltungen v WHERE v.id = ?1", veranstaltungId).list();
            List<EventSlot> alleSlots = EventSlot.find("veranstaltung.id", veranstaltungId).list();

            Map<Long, EventSlot> slotMap = alleSlots.stream().collect(toMap(s -> s.id, s -> s));

            for (Teilnehmer teilnehmer : alleTeilnehmer) {
                Set<Long> belegteSlotIds = new HashSet<>();

                // Pflichtvorträge des Teilnehmers
                List<Pflichtvortrag> pflichtvortraege = Pflichtvortrag.find("veranstaltung.id", veranstaltungId).list();
                for (Pflichtvortrag pv : pflichtvortraege) {
                    if (teilnehmer.gruppe != null && teilnehmer.gruppe.equals(pv.pflichtgruppe)) {
                        belegteSlotIds.add(pv.pflichtslot.id);
                    }
                }

                // Wahlvorträge des Teilnehmers aus dem Planungsergebnis
                int pIdx = tnOids.indexOf(teilnehmer.id);
                if (pIdx != -1) {
                    for (int wIdx = 0; wIdx < wvOids.size(); wIdx++) {
                        for (int iIdx = 0; iIdx < besucht.get(pIdx).get(wIdx).size(); iIdx++) {
                            if (besucht.get(pIdx).get(wIdx).get(iIdx).asBoolean()) {
                                JsonNode instanzSlot = root.get("instanz_slot");
                                int sIdx = instanzSlot.get(wIdx).get(iIdx).asInt() - 1;
                                if (sIdx >= 0 && sIdx < slotOids.size()) {
                                    belegteSlotIds.add(slotOids.get(sIdx));
                                }
                            }
                        }
                    }
                }

                List<EventSlot> freieSlots = alleSlots.stream()
                        .filter(slot -> !belegteSlotIds.contains(slot.id))
                        .sorted(Comparator.comparing(s -> s.startTime))
                        .collect(Collectors.toList());
                freieSlotsTeilnehmer.put(teilnehmer.id, freieSlots);
            }

        } catch (Exception e) {
            LOG.error("Fehler beim Ermitteln freier Teilnehmer-Slots für Veranstaltung " + veranstaltungId, e);
        }
        return freieSlotsTeilnehmer;
    }

    @Transactional
    public byte[] generiereTuerschilderPdf(Long veranstaltungId) throws Exception {
        Veranstaltung veranstaltung = Veranstaltung.findById(veranstaltungId);
        Map<Long, Map<Long, RaumplanEintragDto>> raumplan = getRaumbelegungsplan(veranstaltungId);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4);
        PdfWriter.getInstance(document, baos);
        document.open();

        Font fontTitle = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 24);
        Font fontSubtitle = FontFactory.getFont(FontFactory.HELVETICA, 18, Color.GRAY);
        Font fontTableHeader = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, Color.WHITE);
        Font fontTableCell = FontFactory.getFont(FontFactory.HELVETICA, 11);
        Font fontTeilnehmer = FontFactory.getFont(FontFactory.HELVETICA, 9);


        // Sortiere Räume nach Namen
        List<Raum> alleRaeume = Raum.<Raum>listAll().stream()
                .sorted(Comparator.comparing(r -> r.name))
                .toList();

        for (Raum raum : alleRaeume) {
            Map<Long, RaumplanEintragDto> belegungFuerRaum = raumplan.get(raum.id);
            if (belegungFuerRaum == null || belegungFuerRaum.isEmpty()) {
                continue; // Raum ist nicht belegt, kein Türschild
            }

            // 1. Logo & Header
            if (veranstaltung.logo != null && !veranstaltung.logo.isEmpty()) {
                try {
                    Image logo = Image.getInstance(new URI(veranstaltung.logo).toURL());
                    logo.scaleToFit(100, 100);
                    logo.setAlignment(Element.ALIGN_RIGHT);
                    document.add(logo);
                } catch (Exception e) {
                    LOG.warn("Fehler beim Laden des Logos: " + e.getMessage());
                }
            }

            Paragraph pVeranstaltung = new Paragraph(veranstaltung.name, fontSubtitle);
            document.add(pVeranstaltung);

            Paragraph pRaum = new Paragraph("Raum: " + raum.name, fontTitle);
            pRaum.setSpacingAfter(20);
            document.add(pRaum);

            // 2. Belegungstabelle
            PdfPTable table = new PdfPTable(3);
            table.setWidthPercentage(100);
            table.setWidths(new float[]{20, 50, 30});

            // Header
            String[] headers = {"Zeit", "Vortrag", "Referent"};
            for (String h : headers) {
                PdfPCell cell = new PdfPCell(new Phrase(h, fontTableHeader));
                cell.setBackgroundColor(new Color(79, 70, 229)); // Indigo-600
                cell.setPadding(8);
                table.addCell(cell);
            }

            // Zeilen
            List<EventSlot> sortedSlots = EventSlot.<EventSlot>list("veranstaltung.id", veranstaltungId).stream().toList().stream()
                    .sorted(Comparator.comparing(s -> s.startTime))
                    .toList();

            for (EventSlot slot : sortedSlots) {
                RaumplanEintragDto eintrag = belegungFuerRaum.get(slot.id);
                if (eintrag != null) {
                    table.addCell(new PdfPCell(new Phrase(slot.startTime.format(TIME_FORMAT) + " - " + slot.endTime.format(TIME_FORMAT), fontTableCell)));
                    table.addCell(new PdfPCell(new Phrase(eintrag.vortragTitel, fontTableCell)));
                    table.addCell(new PdfPCell(new Phrase(eintrag.referentName, fontTableCell)));
                } else {
                    // Leere Zeile für unbelegte Slots
                    table.addCell(new PdfPCell(new Phrase(slot.startTime.format(TIME_FORMAT) + " - " + slot.endTime.format(TIME_FORMAT), fontTableCell)));
                    table.addCell(new PdfPCell(new Phrase("Frei", fontTableCell)));
                    table.addCell(new PdfPCell(new Phrase("", fontTableCell)));
                }
            }
            document.add(table);

            // Teilnehmerliste (optional, für jeden Vortrag)
            document.add(new Paragraph("\n")); // Abstand
            for (EventSlot slot : sortedSlots) {
                RaumplanEintragDto eintrag = belegungFuerRaum.get(slot.id);
                if (eintrag != null && eintrag.teilnehmer != null && !eintrag.teilnehmer.isEmpty()) {
                    document.add(new Paragraph(eintrag.vortragTitel + " (" + eintrag.slotZeit + ") - Teilnehmer:", fontTableCell));
                    List<String> teilnehmerNamen = eintrag.teilnehmer.stream()
                            .map(tn -> tn.firstName + " " + tn.lastName + (tn.gruppe != null ? " (" + tn.gruppe + ")" : ""))
                            .toList();
                    for (String tnName : teilnehmerNamen) {
                        document.add(new Paragraph("- " + tnName, fontTeilnehmer));
                    }
                    document.add(new Paragraph("\n")); // Abstand nach Teilnehmerliste
                }
            }

            document.newPage(); // Neues Blatt für den nächsten Raum
        }

        document.close();
        return baos.toByteArray();
    }

    private ZuweisungDto mapToDto(Zuweisung z) {
        return new ZuweisungDto(z.id, z.teilnehmer.lastName, z.vortrag.titel, z.slot.startTime.format(TIME_FORMAT), z.raum.name, z.raum.gebaeude.name);
    }
}