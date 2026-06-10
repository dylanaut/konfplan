package kreyj.konfplan.application.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
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
import kreyj.konfplan.presentation.dto.PlanQualitaetDto;
import kreyj.konfplan.presentation.dto.RaumBelegungUebersichtDto;
import kreyj.konfplan.presentation.dto.RaumplanEintragDto;
import kreyj.konfplan.presentation.dto.ReferentVortragDto;
import kreyj.konfplan.presentation.dto.TeilnehmerSimpleDto;
import kreyj.konfplan.presentation.dto.ZuweisungDto;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.jboss.logging.Logger;

import java.awt.*;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.StreamSupport;

import static java.util.stream.Collectors.toMap;
import static kreyj.konfplan.presentation.dto.RaumBelegungUebersichtDto.VORTRAG_TITEL_FREI;
import static kreyj.konfplan.presentation.dto.RaumBelegungUebersichtDto.VORTRAG_TYP_FREI;

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
        Planungsergebnis planungsergebnis = Planungsergebnis.find("veranstaltung = ?1", veranstaltung).firstResult();
        if (planungsergebnis == null) {
            return Collections.emptyList();
        }

        try {
            JsonNode root = objectMapper.readTree(planungsergebnis.getJsonErgebnis());
            JsonNode instanzSlot = root.get("instanz_slot");
            JsonNode instanzRaum = root.get("instanz_raum");
            JsonNode besucht = root.get("besucht");

            if (instanzSlot == null || instanzRaum == null || besucht == null) {
                LOG.warn("MiniZinc-Ergebnis für Veranstaltung " + veranstaltung.getName() + " unvollständig.");
                return Collections.emptyList();
            }

            List<Teilnehmer> alleTeilnehmer = veranstaltung.teilnehmer();
            Set<Vortrag> alleVortraege = veranstaltung.getVortraege();
            Set<Slot> alleSlots = veranstaltung.getSlots();
            List<Raum> alleRaeume = Raum.listAll();

            Map<Long, Teilnehmer> teilnehmerMap = alleTeilnehmer.stream().collect(toMap(IdEntity::getId, t -> t));
            Map<Long, Vortrag> vortragMap = alleVortraege.stream().collect(toMap(IdEntity::getId, v -> v));
            Map<Long, Slot> slotMap = alleSlots.stream().collect(toMap(IdEntity::getId, s -> s));
            Map<Long, Raum> raumMap = alleRaeume.stream().collect(toMap(IdEntity::getId, r -> r));

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

            for (Pflichtvortrag pv : veranstaltung.getPflichtvortraege()) {
                for (Teilnehmer tn : alleTeilnehmer) {
                    zuweisungen.add(new ZuweisungDto(
                            tn.getLastName(),
                            pv.getTitel(),
                            pv.getPflichtslot().getStartTime().format(TIME_FORMAT),
                            pv.getPflichtraum().getName(),
                            pv.getPflichtraum().getGebaeude().getName()
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

                                Slot slot = slotMap.get(slotId);
                                Raum raum = raumMap.get(raumId);

                                if (slot != null && raum != null) {
                                    zuweisungen.add(new ZuweisungDto(
                                            teilnehmer.getLastName(),
                                            vortrag.getTitel(),
                                            slot.getStartTime().format(TIME_FORMAT),
                                            raum.getName(),
                                            raum.getGebaeude().getName()
                                    ));
                                }
                            }
                        }
                    }
                }
            }
            return zuweisungen;

        } catch (Exception e) {
            LOG.error("Fehler beim Parsen des Planungsergebnisses für Veranstaltung " + veranstaltung.getName(), e);
            return Collections.emptyList();
        }
    }

    @Transactional
    public List<RaumBelegungUebersichtDto> getDetaillierterPlan(Veranstaltung veranstaltung) {
        Map<Long, Map<Long, RaumplanEintragDto>> raumplan = getRaumbelegungsplan(veranstaltung);
        List<RaumBelegungUebersichtDto> detaillierterPlan = new ArrayList<>();

        List<Slot> sortedSlots = veranstaltung.getSlots().stream().sorted(Comparator.comparing(Slot::getStartTime)).toList();
        List<Raum> sortedRaeume =
                veranstaltung.getRaeume().stream()
                        .sorted(Comparator.comparing((Raum r) -> r.getGebaeude().getName())
                                .thenComparing(Raum::getName))
                        .toList();

        for (Slot slot : sortedSlots) {
            for (Raum raum : sortedRaeume) {
                RaumplanEintragDto eintrag = null;
                if (raumplan.containsKey(raum.getId())) {
                    eintrag = raumplan.get(raum.getId()).get(slot.getId());
                }

                if (eintrag != null) {
                    List<String> tnNamen = eintrag.teilnehmer != null ?
                            eintrag.teilnehmer.stream().map(t -> t.firstName + " " + t.lastName).toList() :
                            new ArrayList<>();

                    detaillierterPlan.add(new RaumBelegungUebersichtDto(
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
                    detaillierterPlan.add(new RaumBelegungUebersichtDto(
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
        return detaillierterPlan;
    }

    @Transactional
    public PlanQualitaetDto getPlanQualitaet(Veranstaltung veranstaltung) {
        Planungsergebnis planungsergebnis = Planungsergebnis.find("veranstaltung = ?1", veranstaltung).firstResult();
        if (planungsergebnis == null) {
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
    public List<ZuweisungDto> getPlanFuerTeilnehmer(String email, Long veranstaltungId) {
        Teilnehmer teilnehmer = Teilnehmer.find("email", email).firstResult();
        if (teilnehmer == null) {
            return Collections.emptyList();
        }

        Planungsergebnis planungsergebnis = Planungsergebnis.find("veranstaltung.id = ?1", veranstaltungId).firstResult();
        if (planungsergebnis == null) {
            return Collections.emptyList();
        }

        try {
            JsonNode root = objectMapper.readTree(planungsergebnis.getJsonErgebnis());
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

            Map<Long, Vortrag> vortragMap = Vortrag.find("veranstaltung.id = ?1", veranstaltungId).<Vortrag>list().stream().collect(toMap(IdEntity::getId, v -> v));
            Map<Long, Slot> slotMap = Slot.find("veranstaltung.id = ?1", veranstaltungId).<Slot>list().stream().collect(toMap(IdEntity::getId, s -> s));
            Map<Long, Raum> raumMap = Raum.<Raum>listAll().stream().collect(toMap(IdEntity::getId, r -> r));

            int pIdx = tnOids.indexOf(teilnehmer.getId());
            if (pIdx == -1) {
                return Collections.emptyList();
            }

            List<ZuweisungDto> zuweisungen = new ArrayList<>();

            List<Pflichtvortrag> pflichtvortraege = Pflichtvortrag.find("veranstaltung.id = ?1", veranstaltungId).list();
            for (Pflichtvortrag pv : pflichtvortraege) {
                Set<String> tnGruppe = teilnehmer.getGruppen();
                if (tnGruppe != null && tnGruppe.contains(pv.getPflichtgruppe())) {
                    zuweisungen.add(new ZuweisungDto(
                            teilnehmer.getLastName(),
                            pv.getTitel(),
                            pv.getPflichtslot().getStartTime().format(TIME_FORMAT),
                            pv.getPflichtraum().getName(),
                            pv.getPflichtraum().getGebaeude().getName()
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

                            Slot slot = slotMap.get(slotId);
                            Raum raum = raumMap.get(raumId);

                            if (slot != null && raum != null) {
                                zuweisungen.add(new ZuweisungDto(
                                        teilnehmer.getLastName(),
                                        vortrag.getTitel(),
                                        slot.getStartTime().format(TIME_FORMAT),
                                        raum.getName(),
                                        raum.getGebaeude().getName()
                                ));
                            }
                        }
                    }
                }
            }
            return zuweisungen.stream()
                    .sorted(Comparator.comparing(ZuweisungDto::getSlotZeit))
                    .toList();

        } catch (Exception e) {
            LOG.error("Fehler beim Erstellen des Teilnehmerplans für " + email, e);
            return Collections.emptyList();
        }
    }

    @Transactional
    public List<ReferentVortragDto> getPlanFuerReferent(String email, Veranstaltung veranstaltung) {
        Referent referent = Referent.find("email", email).firstResult();
        if (referent == null) {
            return Collections.emptyList();
        }

        Planungsergebnis planungsergebnis = Planungsergebnis.find("veranstaltung = ?1", veranstaltung).firstResult();
        if (planungsergebnis == null) {
            return Collections.emptyList();
        }

        try {
            JsonNode root = objectMapper.readTree(planungsergebnis.getJsonErgebnis());
            JsonNode inputData = root.get("input_data");
            JsonNode instanzSlot = root.get("instanz_slot");
            JsonNode instanzRaum = root.get("instanz_raum");
            JsonNode besucht = root.get("besucht");

            if (inputData == null || instanzSlot == null || instanzRaum == null || besucht == null) {
                LOG.warn("MiniZinc-Ergebnis für Veranstaltung " + veranstaltung.getName() + " unvollständig.");
                return Collections.emptyList();
            }

            List<Long> tnOids = StreamSupport.stream(inputData.get("teilnehmer_oids").spliterator(), false).map(JsonNode::asLong).toList();
            List<Long> wvOids = StreamSupport.stream(inputData.get("wahlvortrag_oids").spliterator(), false).map(JsonNode::asLong).toList();
            List<Long> slotOids = StreamSupport.stream(inputData.get("slot_oids").spliterator(), false).map(JsonNode::asLong).toList();
            List<Long> raumOids = StreamSupport.stream(inputData.get("raum_oids").spliterator(), false).map(JsonNode::asLong).toList();

            Map<Long, Teilnehmer> teilnehmerMap =
                    veranstaltung.teilnehmer().stream().collect(toMap(IdEntity::getId, t -> t));
            Map<Long, Slot> slotMap = veranstaltung.getSlots().stream().collect(toMap(IdEntity::getId, s -> s));
            Map<Long, Raum> raumMap = Raum.<Raum>listAll().stream().collect(toMap(IdEntity::getId, r -> r));

            List<ReferentVortragDto> referentPlan = new ArrayList<>();

            List<Pflichtvortrag> pflichtvortraege = Pflichtvortrag.find("veranstaltung = ?1 and referent = ?2", veranstaltung, referent).list();
            for (Pflichtvortrag pv : pflichtvortraege) {
                List<Teilnehmer> gruppenTeilnehmer = Teilnehmer.getGruppenTeilnehmer(pv.getPflichtgruppe(), veranstaltung.getId());
                List<TeilnehmerSimpleDto> teilnehmerDtos = gruppenTeilnehmer.stream()
                        .map(tn -> new TeilnehmerSimpleDto(tn.getId(), tn.getFirstName(), tn.getLastName(), tn.getGruppen()))
                        .toList();
                referentPlan.add(new ReferentVortragDto(pv.getTitel(), pv.getPflichtslot().getStartTime().format(TIME_FORMAT), pv.getPflichtraum().getName(), pv.getPflichtraum().getGebaeude().getName(), teilnehmerDtos));
            }

            List<Wahlvortrag> referentenWahlvortraege = Wahlvortrag.find("veranstaltung = ?1 and referent = ?2", veranstaltung, referent).list();
            for (Wahlvortrag wv : referentenWahlvortraege) {
                int wIdx = wvOids.indexOf(wv.getId());
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
                    Slot slot = slotMap.get(slotId);
                    Raum raum = raumMap.get(raumId);

                    if (slot != null && raum != null) {
                        List<TeilnehmerSimpleDto> zugewieseneTeilnehmer = new ArrayList<>();
                        for (int pIdx = 0; pIdx < tnOids.size(); pIdx++) {
                            if (besucht.get(pIdx).get(wIdx).get(iIdx).asBoolean()) {
                                Teilnehmer tn = teilnehmerMap.get(tnOids.get(pIdx));
                                if (tn != null) {
                                    zugewieseneTeilnehmer.add(new TeilnehmerSimpleDto(tn.getId(), tn.getFirstName(),
                                            tn.getLastName(), tn.getGruppen()));
                                }
                            }
                        }
                        referentPlan.add(new ReferentVortragDto(wv.getTitel(), slot.getStartTime().format(TIME_FORMAT), raum.getName(), raum.getGebaeude().getName(), zugewieseneTeilnehmer));
                    }
                }
            }
            return referentPlan.stream()
                    .sorted(Comparator.comparing(ReferentVortragDto::getSlotZeit))
                    .toList();

        } catch (Exception e) {
            LOG.error("Fehler beim Erstellen des Referentenplans für " + email, e);
            return Collections.emptyList();
        }
    }

    @Transactional
    public Map<Long, Map<Long, RaumplanEintragDto>> getRaumbelegungsplan(Veranstaltung veranstaltung) {
        Planungsergebnis planungsergebnis = Planungsergebnis.find("veranstaltung = ?1", veranstaltung).firstResult();
        if (planungsergebnis == null) {
            return Collections.emptyMap();
        }

        try {
            // Explicitly materialize the String from the LOB to avoid issues with deferred access
            String jsonContent = planungsergebnis.getJsonErgebnis();
            JsonNode root = objectMapper.readTree(jsonContent);

            JsonNode instanzSlot = root.get("instanz_slot");
            JsonNode instanzRaum = root.get("instanz_raum");
            JsonNode besucht = root.get("besucht");

            if (instanzSlot == null || instanzRaum == null || besucht == null) {
                LOG.warn("MiniZinc-Ergebnis für Veranstaltung " + veranstaltung.getName() + " unvollständig.");
                return Collections.emptyMap();
            }


            List<Teilnehmer> alleTeilnehmer = veranstaltung.teilnehmer();
            Set<Vortrag> alleVortraege = veranstaltung.getVortraege();
            Set<Slot> alleSlots = veranstaltung.getSlots();
            List<Raum> alleRaeume = veranstaltung.getRaeume();

            Map<Long, Teilnehmer> teilnehmerMap = alleTeilnehmer.stream().collect(toMap(IdEntity::getId, t -> t));
            Map<Long, Vortrag> vortragMap = alleVortraege.stream().collect(toMap(IdEntity::getId, v -> v));
            Map<Long, Slot> slotMap = alleSlots.stream().collect(toMap(IdEntity::getId, s -> s));
            Map<Long, Raum> raumMap = alleRaeume.stream().collect(toMap(IdEntity::getId, r -> r));

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
            for (Pflichtvortrag pv : veranstaltung.getPflichtvortraege()) {
                Raum raum = pv.getPflichtraum();
                Slot slot = pv.getPflichtslot();

                List<Teilnehmer> gruppenTeilnehmer = Teilnehmer.getGruppenTeilnehmer(pv.getPflichtgruppe(), veranstaltung.getId());
                List<TeilnehmerSimpleDto> teilnehmerDtos = gruppenTeilnehmer.stream()
                        .map(tn -> new TeilnehmerSimpleDto(tn.getId(), tn.getFirstName(), tn.getLastName(), tn.getGruppen()))
                        .toList();

                RaumplanEintragDto eintrag = new RaumplanEintragDto(
                        slot.getId(),
                        slot.getStartTime().format(TIME_FORMAT),
                        pv.getTitel(),
                        pv.getReferent().getFirstName() + " " + pv.getReferent().getLastName(),
                        "PFLICHT",
                        teilnehmerDtos.size(),
                        teilnehmerDtos
                );
                raumplan.computeIfAbsent(raum.getId(), k -> new HashMap<>()).put(slot.getId(), eintrag);
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
                    Slot slot = slotMap.get(slotId);
                    Raum raum = raumMap.get(raumId);

                    if (slot != null && raum != null) {
                        List<TeilnehmerSimpleDto> zugewieseneTeilnehmer = new ArrayList<>();
                        for (int pIdx = 0; pIdx < tnOids.size(); pIdx++) {
                            if (besucht.get(pIdx).get(wIdx).get(iIdx).asBoolean()) {
                                Teilnehmer tn = teilnehmerMap.get(tnOids.get(pIdx));
                                if (tn != null) {
                                    zugewieseneTeilnehmer.add(new TeilnehmerSimpleDto(tn.getId(), tn.getFirstName(),
                                            tn.getLastName(), tn.getGruppen()));
                                }
                            }
                        }
                        RaumplanEintragDto eintrag = new RaumplanEintragDto(
                                slot.getId(),
                                slot.getStartTime().format(TIME_FORMAT),
                                vortrag.getTitel(),
                                vortrag.getReferent().getFirstName() + " " + vortrag.getReferent().getLastName(),
                                "WAHL",
                                zugewieseneTeilnehmer.size(),
                                zugewieseneTeilnehmer
                        );
                        raumplan.computeIfAbsent(raum.getId(), k -> new HashMap<>()).put(slot.getId(), eintrag);
                    }
                }
            }
            return raumplan;

        } catch (Exception e) {
            LOG.error("Fehler beim Erstellen des Raumbelegungsplans für Veranstaltung " + veranstaltung.getName(), e);
            return Collections.emptyMap();
        }
    }

    @Transactional
    public Map<Long, List<Slot>> getFreieSlotsReferenten(Long veranstaltungId) {
        Map<Long, List<Slot>> freieSlotsReferenten = new HashMap<>();
        Planungsergebnis planungsergebnis = Planungsergebnis.find("veranstaltung.id = ?1", veranstaltungId).firstResult();
        if (planungsergebnis == null) {
            return Collections.emptyMap();
        }

        try {
            JsonNode root = objectMapper.readTree(planungsergebnis.getJsonErgebnis());
            JsonNode instanzSlot = root.get("instanz_slot");
            JsonNode wvOidsNode = root.get("input_data").get("wahlvortrag_oids");
            JsonNode slotOidsNode = root.get("input_data").get("slot_oids");

            List<Long> wvOids = StreamSupport.stream(wvOidsNode.spliterator(), false).map(JsonNode::asLong).toList();
            List<Long> slotOids = StreamSupport.stream(slotOidsNode.spliterator(), false).map(JsonNode::asLong).toList();

            List<Referent> alleReferenten = Referent.find("SELECT r FROM Referent r JOIN r.veranstaltungen v WHERE v.id = ?1", veranstaltungId).list();
            List<Vortrag> alleVortraege = Vortrag.find("veranstaltung.id = ?1", veranstaltungId).list();
            List<Slot> alleSlots = Slot.find("veranstaltung.id = ?1", veranstaltungId).list();

            Map<Long, Vortrag> vortragMap = alleVortraege.stream().collect(toMap(IdEntity::getId, v -> v));
            Map<Long, Slot> slotMap = alleSlots.stream().collect(toMap(IdEntity::getId, s -> s));

            for (Referent referent : alleReferenten) {
                Set<Long> belegteSlotIds = new HashSet<>();

                // Pflichtvorträge des Referenten
                List<Pflichtvortrag> pflichtvortraege = Pflichtvortrag.find("veranstaltung.id = ?1 and referent.id = ?2", veranstaltungId, referent.getId()).list();
                for (Pflichtvortrag pv : pflichtvortraege) {
                    belegteSlotIds.add(pv.getPflichtslot().getId());
                }

                // Wahlvorträge des Referenten aus dem Planungsergebnis
                List<Wahlvortrag> referentenWahlvortraege = Wahlvortrag.find("veranstaltung.id = ?1 and referent.id = ?2", veranstaltungId, referent.getId()).list();
                for (Wahlvortrag wv : referentenWahlvortraege) {
                    int wIdx = wvOids.indexOf(wv.getId());
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

                List<Slot> freieSlots = alleSlots.stream()
                        .filter(slot -> !belegteSlotIds.contains(slot.getId()))
                        .sorted(Comparator.comparing(Slot::getStartTime))
                        .toList();
                freieSlotsReferenten.put(referent.getId(), freieSlots);
            }

        } catch (Exception e) {
            LOG.error("Fehler beim Ermitteln freier Referenten-Slots für Veranstaltung " + veranstaltungId, e);
        }
        return freieSlotsReferenten;
    }

    @Transactional
    public Map<Long, List<Slot>> getFreieSlotsTeilnehmer(Veranstaltung veranstaltung) {
        Map<Long, List<Slot>> freieSlotsTeilnehmer = new HashMap<>();
        Planungsergebnis planungsergebnis = Planungsergebnis.find("veranstaltung = ?1", veranstaltung).firstResult();
        if (planungsergebnis == null) {
            return Collections.emptyMap();
        }

        try {
            JsonNode root = objectMapper.readTree(planungsergebnis.getJsonErgebnis());
            JsonNode besucht = root.get("besucht");
            JsonNode tnOidsNode = root.get("input_data").get("teilnehmer_oids");
            JsonNode wvOidsNode = root.get("input_data").get("wahlvortrag_oids");
            JsonNode slotOidsNode = root.get("input_data").get("slot_oids");

            List<Long> tnOids = StreamSupport.stream(tnOidsNode.spliterator(), false).map(JsonNode::asLong).toList();
            List<Long> wvOids = StreamSupport.stream(wvOidsNode.spliterator(), false).map(JsonNode::asLong).toList();
            List<Long> slotOids = StreamSupport.stream(slotOidsNode.spliterator(), false).map(JsonNode::asLong).toList();

            List<Teilnehmer> alleTeilnehmer = veranstaltung.teilnehmer();
            Set<Slot> alleSlots = veranstaltung.getSlots();

            List<Pflichtvortrag> pflichtvortraege = veranstaltung.getPflichtvortraege();

            for (Teilnehmer teilnehmer : alleTeilnehmer) {
                Set<Long> belegteSlotIds = new HashSet<>();

                // Pflichtvorträge des Teilnehmers
                for (Pflichtvortrag pv : pflichtvortraege) {
                    Set<String> tnGruppe = teilnehmer.getGruppen();
                    if (tnGruppe != null && tnGruppe.contains(pv.getPflichtgruppe())) {
                        belegteSlotIds.add(pv.getPflichtslot().getId());
                    }
                }

                // Wahlvorträge des Teilnehmers aus dem Planungsergebnis
                int pIdx = tnOids.indexOf(teilnehmer.getId());
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

                List<Slot> freieSlots = alleSlots.stream()
                        .filter(slot -> !belegteSlotIds.contains(slot.getId()))
                        .sorted(Comparator.comparing(Slot::getStartTime))
                        .toList();
                freieSlotsTeilnehmer.put(teilnehmer.getId(), freieSlots);
            }

        } catch (Exception e) {
            LOG.error("Fehler beim Ermitteln freier Teilnehmer-Slots für Veranstaltung " + veranstaltung.getName(), e);
        }
        return freieSlotsTeilnehmer;
    }

    // Helper class for Qute template
    @Getter
    @AllArgsConstructor
    public static class RaumschildDaten {
        private Veranstaltung veranstaltung;
        private Raum raum;
        private Map<Long, RaumplanEintragDto> belegungFuerRaum;
        private List<Slot> sortedSlots;
        private Map<String, String> vortragColors;
    }

    @Transactional
    public byte[] generiereTuerschilderPdf(Veranstaltung veranstaltung) {
        Map<Long, Map<Long, RaumplanEintragDto>> raumplan = getRaumbelegungsplan(veranstaltung);

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
                .sorted(Comparator.comparing(Raum::getName))
                .toList();

        for (Raum raum : alleRaeume) {
            Map<Long, RaumplanEintragDto> belegungFuerRaum = raumplan.get(raum.getId());
            if (belegungFuerRaum == null || belegungFuerRaum.isEmpty()) {
                continue; // Raum ist nicht belegt, kein Türschild
            }

            // 1. Logo & Header
            if (veranstaltung.getLogo() != null && !veranstaltung.getLogo().isEmpty()) {
                try {
                    Image logo = Image.getInstance(new URI(veranstaltung.getLogo()).toURL());
                    logo.scaleToFit(100, 100);
                    logo.setAlignment(Element.ALIGN_RIGHT);
                    document.add(logo);
                } catch (Exception e) {
                    LOG.warn("Fehler beim Laden des Logos: " + e.getMessage());
                }
            }

            Paragraph pVeranstaltung = new Paragraph(veranstaltung.getName(), fontSubtitle);
            document.add(pVeranstaltung);

            Paragraph pRaum = new Paragraph("Raum: " + raum.getName(), fontTitle);
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
            List<Slot> sortedSlots = veranstaltung.getSlots().stream()
                    .sorted(Comparator.comparing(Slot::getStartTime))
                    .toList();

            for (Slot slot : sortedSlots) {
                RaumplanEintragDto eintrag = belegungFuerRaum.get(slot.getId());
                if (eintrag != null) {
                    table.addCell(new PdfPCell(new Phrase(slot.getStartTime().format(TIME_FORMAT) + " - " + slot.getEndTime().format(TIME_FORMAT), fontTableCell)));
                    table.addCell(new PdfPCell(new Phrase(eintrag.vortragTitel, fontTableCell)));
                    table.addCell(new PdfPCell(new Phrase(eintrag.referentName, fontTableCell)));
                } else {
                    // Leere Zeile für unbelegte Slots
                    table.addCell(new PdfPCell(new Phrase(slot.getStartTime().format(TIME_FORMAT) + " - " + slot.getEndTime().format(TIME_FORMAT), fontTableCell)));
                    table.addCell(new PdfPCell(new Phrase("Frei", fontTableCell)));
                    table.addCell(new PdfPCell(new Phrase("", fontTableCell)));
                }
            }
            document.add(table);

            // Teilnehmerliste (optional, für jeden Vortrag)
            document.add(new Paragraph("\n")); // Abstand
            for (Slot slot : sortedSlots) {
                RaumplanEintragDto eintrag = belegungFuerRaum.get(slot.getId());
                if (eintrag != null && eintrag.teilnehmer != null && !eintrag.teilnehmer.isEmpty()) {
                    document.add(new Paragraph(eintrag.vortragTitel + " (" + eintrag.slotZeit + ") - Teilnehmer:", fontTableCell));
                    List<String> teilnehmerNamen = eintrag.teilnehmer.stream()
                            .map(tn -> tn.firstName + " " + tn.lastName + (tn.gruppen != null ? " (" + tn.gruppen + ")" : ""))
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

    @Transactional
    public byte[] generiereAlleRaumschilderPdf(Veranstaltung veranstaltung) {
        Map<Long, Map<Long, RaumplanEintragDto>> raumplan = getRaumbelegungsplan(veranstaltung);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4);
        PdfWriter.getInstance(document, baos);
        document.open();

        Font fontTitle = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 24);
        Font fontSubtitle = FontFactory.getFont(FontFactory.HELVETICA, 18, Color.GRAY);
        Font fontTableHeader = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, Color.WHITE);
        Font fontTableCell = FontFactory.getFont(FontFactory.HELVETICA, 11);
        Font fontTeilnehmer = FontFactory.getFont(FontFactory.HELVETICA, 9);

        // Lade nur die Räume, die zu den Gebäuden dieser Veranstaltung gehören
        List<Raum> alleRaeumeDerVeranstaltung = veranstaltung.getRaeume();

        List<Slot> sortedSlots = veranstaltung.getSlots().stream()
                .sorted(Comparator.comparing(Slot::getStartTime))
                .toList();

        // Helper für farbliche Kodierung der Vorträge
        Map<String, String> vortragColors = new HashMap<>();
        String colorCodeFrei = "#f5f5f5";
        String[] colorPalette = {"#1A5276", "#1E6B3C", "#C0392B", "#D4820A", "#6E2F7A", "#4A4A4A", "#B7470A", "#7D6608", "#5D6D7E", "#C2185B", "#00838F", "#5C4033"};
        int colorIndex = 0;

        for (Raum raum : alleRaeumeDerVeranstaltung) {
            Map<Long, RaumplanEintragDto> belegungFuerRaum = raumplan.getOrDefault(raum.getId(), Collections.emptyMap());

            // 1. Logo & Header
            if (veranstaltung.getLogo() != null && !veranstaltung.getLogo().isEmpty()) {
                try {
                    Image logo = Image.getInstance(new URI(veranstaltung.getLogo()).toURL());
                    logo.scaleToFit(100, 100);
                    logo.setAlignment(Element.ALIGN_RIGHT);
                    document.add(logo);
                } catch (Exception e) {
                    LOG.warn("Fehler beim Laden des Logos: " + e.getMessage());
                }
            }

            Paragraph pVeranstaltung = new Paragraph(veranstaltung.getName(), fontSubtitle);
            document.add(pVeranstaltung);

            Paragraph pRaum = new Paragraph("Raum: " + raum.getName(), fontTitle);
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
            for (Slot slot : sortedSlots) {
                RaumplanEintragDto eintrag = belegungFuerRaum.get(slot.getId());
                if (eintrag != null) {
                    // Farbe für Vortragstitel im PDF
                    if (!vortragColors.containsKey(eintrag.vortragTitel)) {
                        vortragColors.put(eintrag.vortragTitel, colorPalette[colorIndex % colorPalette.length]);
                        colorIndex++;
                    }
                    Color vortragBgColor = Color.decode(vortragColors.get(eintrag.vortragTitel));

                    PdfPCell vortragCell = new PdfPCell(new Phrase(eintrag.vortragTitel, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, vortragBgColor)));
                    vortragCell.setBackgroundColor(vortragBgColor.brighter()); // Leichterer Hintergrund
                    vortragCell.setPadding(5);

                    table.addCell(new PdfPCell(new Phrase(slot.getStartTime().format(TIME_FORMAT) + " - " + slot.getEndTime().format(TIME_FORMAT), fontTableCell)));
                    table.addCell(vortragCell);
                    table.addCell(new PdfPCell(new Phrase(eintrag.referentName, fontTableCell)));
                } else {
                    // Leere Zeile für unbelegte Slots
                    table.addCell(new PdfPCell(new Phrase(slot.getStartTime().format(TIME_FORMAT) + " - " + slot.getEndTime().format(TIME_FORMAT), fontTableCell)));
                    table.addCell(new PdfPCell(new Phrase("Frei", fontTableCell)));
                    table.addCell(new PdfPCell(new Phrase("", fontTableCell)));
                }
            }
            document.add(table);

            // Teilnehmerliste (optional, für jeden Vortrag)
            document.add(new Paragraph("\n")); // Abstand
            for (Slot slot : sortedSlots) {
                RaumplanEintragDto eintrag = belegungFuerRaum.get(slot.getId());
                if (eintrag != null && eintrag.teilnehmer != null && !eintrag.teilnehmer.isEmpty()) {
                    document.add(new Paragraph(eintrag.vortragTitel + " (" + eintrag.slotZeit + ") - Teilnehmer:", fontTableCell));
                    List<String> teilnehmerNamen = eintrag.teilnehmer.stream()
                            .map(tn -> tn.firstName + " " + tn.lastName + (tn.gruppen != null ? " (" + tn.gruppen + ")" : ""))
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
}