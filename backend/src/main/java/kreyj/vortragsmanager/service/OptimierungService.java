package kreyj.vortragsmanager.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import kreyj.vortragsmanager.dto.SolverConfigDto;
import kreyj.vortragsmanager.entity.*;
import org.jboss.logging.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static java.util.stream.Collectors.joining;

@ApplicationScoped
public class OptimierungService {

    private static final Logger LOG = Logger.getLogger(OptimierungService.class);
    private static final String MZN_MODEL_PATH = "src/main/resources/minizinc/vortragsplanung.mzn";
    private static final DateTimeFormatter WEEKDAY_TIME_FORMAT = DateTimeFormatter.ofPattern("EE,HH:mm");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    @Inject
    ObjectMapper objectMapper;

    @Transactional
    public void starteOptimierung(Long veranstaltungId, SolverConfigDto config) throws Exception {
        LOG.info("Starte Optimierung für Veranstaltung: " + veranstaltungId);

        // 1. Daten laden
        List<Teilnehmer> teilnehmer = Teilnehmer.find("SELECT t FROM Teilnehmer t JOIN t.veranstaltungen v WHERE v.id = ?1", veranstaltungId).list();
        List<Pflichtvortrag> pflichtvortraege = Pflichtvortrag.find("veranstaltung.id", veranstaltungId).list();
        List<Wahlvortrag> wahlvortraege = Wahlvortrag.find("veranstaltung.id", veranstaltungId).list();
        List<EventSlot> slots = EventSlot.find("veranstaltung.id", veranstaltungId).list();
        List<Raum> raeume = Raum.listAll();

        // 2. Bestehende Zuweisungen löschen
        Zuweisung.delete("slot.veranstaltung.id", veranstaltungId);

        // 3. Pflichtvorträge vorab zuweisen (für alle Teilnehmer)
        for (Pflichtvortrag pv : pflichtvortraege) {
            for (Teilnehmer t : teilnehmer) {
                Zuweisung z = new Zuweisung();
                z.teilnehmer = t;
                z.vortrag = pv;
                z.slot = pv.pflichtslot;
                z.raum = pv.pflichtraum;
                z.persist();
            }
        }
        LOG.info(pflichtvortraege.size() + " Pflichtvorträge vorab zugewiesen.");

        if (slots.isEmpty() || teilnehmer.isEmpty() || wahlvortraege.isEmpty()) {
            LOG.warn("Keine Wahlvorträge oder Slots vorhanden. Optimierung beendet.");
            return;
        }

        // 4. MiniZinc Datendatei generieren (mit belegten Slots/Räumen durch Pflichtvorträge)
        String dznContent = generiereDzn(teilnehmer, wahlvortraege, slots, raeume, pflichtvortraege);
        LOG.info("MiniZinc Datendatei:\n" + dznContent);

        Path tempDzn = Files.createTempFile("planung_", ".dzn");
        Files.writeString(tempDzn, dznContent, StandardCharsets.UTF_8);

        try {
            // 5. MiniZinc aufrufen
            String resultJson = rufeMiniZincAuf(tempDzn, config.solver, config.timeout, config.numThreads);

            if (resultJson != null && !resultJson.isEmpty() && resultJson.contains("instanz_slot")) {
                // 6. Wahlvortrag-Ergebnisse parsen und persistieren
                speichereErgebnisse(resultJson, teilnehmer, wahlvortraege, slots, raeume);
            } else {
                throw new RuntimeException("MiniZinc konnte keine Lösung für die Wahlvorträge finden.");
            }
        } finally {
            Files.deleteIfExists(tempDzn);
        }
    }

    private String rufeMiniZincAuf(Path dznPath, String solver, int timeoutSeconds, int numThreads) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(
                "minizinc", "--solver", solver, "--json-output",
                "--time-limit", String.valueOf(timeoutSeconds * 1000),
                "--parallel", String.valueOf(numThreads),
                MZN_MODEL_PATH, dznPath.toAbsolutePath().toString()
        );

        pb.redirectErrorStream(true);
        Process process = pb.start();

        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().startsWith("{")) {
                    output.append(line);
                }
                LOG.debug("MZN: " + line);
            }
        }

        process.waitFor(timeoutSeconds + 5, TimeUnit.SECONDS);
        return output.toString();
    }

    private String generiereDzn(List<Teilnehmer> teilnehmer, List<Wahlvortrag> wahlvortraege, List<EventSlot> slots, List<Raum> raeume, List<Pflichtvortrag> pflichtvortraege) {
        StringBuilder sb = new StringBuilder();
        sb.append("max_instanzen = 6;\n");
        sb.append("opt_referent_raumtreue = true;\n\n");

        appendSlots(slots, sb);
        appendRaeume(raeume, sb);
        appendTeilnehmer(teilnehmer, sb);
        appendWahlvortraege(wahlvortraege, slots, sb);
        appendTnPrios(teilnehmer, wahlvortraege, sb);
        appendTnVerfuegbarkeiten(teilnehmer, slots, pflichtvortraege, sb);
        appendRaumVerfuegbarkeiten(raeume, slots, pflichtvortraege, sb);

        return sb.toString();
    }

    private static void appendSlots(List<EventSlot> slots, StringBuilder sb) {
        final int nSlots = slots.size();
        sb.append("n_slots = ").append(nSlots).append(";\n");
        sb.append("slots = [");
        int sIdx = 0;
        for (EventSlot s : slots) {
            sb.append(String.format("\n(oid: %d)", s.id));
            if (++sIdx < nSlots) {
                sb.append(",");
            }
            sb.append(String.format(" %% %d: %s - %s", sIdx, s.startTime.format(WEEKDAY_TIME_FORMAT), s.endTime.format(TIME_FORMAT)));
        }
        sb.append("\n];\n\n");
    }

    private static void appendRaeume(List<Raum> raeume, StringBuilder sb) {
        final int nRaeume = raeume.size();
        sb.append("n_raeume = ").append(nRaeume).append(";\n");
        sb.append("raeume = [");

        int rIdx = 0;
        for (Raum raum : raeume) {
            sb.append(String.format("\n(oid: %d, kapazitaet: %d)", raum.id, raum.kapazitaet));
            if (++rIdx < nRaeume) {
                sb.append(",");
            }
            sb.append(String.format(" %% %d: %s, %d", rIdx, raum.name, raum.kapazitaet));
        }
        sb.append("\n];\n\n");
    }

    private static void appendTeilnehmer(List<Teilnehmer> teilnehmer, StringBuilder sb) {
        final int nTNs = teilnehmer.size();
        sb.append("n_teilnehmer = ").append(nTNs).append(";\n");
        sb.append("teilnehmer = [");

        int pIdx = 0;
        for (Teilnehmer tn : teilnehmer) {
            sb.append(String.format("\n(oid: %d)", tn.id));
            if (++pIdx < nTNs) {
                sb.append(",");
            }
            sb.append(String.format(" %% %d: %s (%s)", pIdx, tn.email, tn.gruppe));
        }

        sb.append("\n];\n\n");
    }

    private static void appendWahlvortraege(List<Wahlvortrag> wahlvortraege, List<EventSlot> slots, StringBuilder sb) {
        final int nWVs = wahlvortraege.size();
        sb.append("n_wahlvortraege = ").append(nWVs).append(";\n");
        // Referenten lookup
        Map<Long, Integer> refMap = new HashMap<>();
        int refCounter = 1;
        for (Wahlvortrag v : wahlvortraege) {
            if (!refMap.containsKey(v.referent.id)) {
                refMap.put(v.referent.id, refCounter++);
            }
        }

        // Vorträge
        int wvIdx = 0;
        sb.append("wahlvortraege = [");

        // TODO remove
        String constantSlotIds = IntStream.rangeClosed(1, slots.size()).mapToObj(String::valueOf).collect(joining(","));

        for (Wahlvortrag wv : wahlvortraege) {
            sb.append("\n");
            String slot_ids /*= wv.wahlSlots.stream()
                    .map(slot -> String.valueOf(slots.indexOf(slot) + 1))
                    .collect(joining(","))*/;
            slot_ids = constantSlotIds; // TODO entfernen

            sb.append(String.format("(oid: %d, referent_id: %d, belegbare_slots: [%s])", wv.id, refMap.get(wv.referent.id), slot_ids));

            if (++wvIdx < nWVs) {
                sb.append(",");
            }

            sb.append(String.format(" %% %d: %s", wvIdx, wv.titel.substring(0, Math.min(25, wv.titel.length()))));
        }

        sb.append("\n];\n\n");
    }


    private static void appendTnPrios(List<Teilnehmer> teilnehmer, List<Wahlvortrag> wahlvortraege, StringBuilder sb) {
        // Prioritäten Matrix
        sb.append("prioritaeten = [|");

        int wvSize = wahlvortraege.size();
        int tnSize = teilnehmer.size();
        int tnIdx = 0;
        for (Teilnehmer tn : teilnehmer) {
            sb.append("\n");
            int wvIdx = 0;
            for (Wahlvortrag v : wahlvortraege) {
                Prioritaet p = Prioritaet.find("teilnehmer = ?1 and vortrag = ?2", tn, v).firstResult();
                sb.append(p != null ? p.prioWert : wvIdx + 1);
                if (++wvIdx < wvSize) {
                    sb.append(",");
                } else {
                    sb.append(" |");
                }
            }
            if (++tnIdx == tnSize) {
                sb.append("];");
            }
            sb.append(String.format(" %% %d: %s", tn.id, tn.email));
        }

        sb.append("\n\n");
    }

    private static void appendTnVerfuegbarkeiten(List<Teilnehmer> teilnehmer, List<EventSlot> slots, List<Pflichtvortrag> pflichtvortraege, StringBuilder sb) {
        // Teilnehmer-Verfügbarkeiten (Berücksichtigung der Pflichtvorträge)
        sb.append("%In welchen Slots jeder Teilnehmer für Wahlvorträge planbar ist:\n");

        int tnSize = teilnehmer.size();
        int slotSize = slots.size();
        int tnIdx = 0;
        sb.append("tn_verfuegbar = [| %% Slot 1..").append(slots.size());

        for (Teilnehmer tn : teilnehmer) {
            sb.append("\n");
            int sIdx = 0;
            for (EventSlot s : slots) {
                boolean belegtByPflicht = pflichtvortraege.stream().anyMatch(pv -> pv.pflichtslot.id.equals(s.id));
                boolean generellOk = tn.verfuegbareSlots.contains(s);
//                sb.append(!belegtByPflicht && generellOk ? "true" : "false");
                sb.append("true"); // TODO entfernen
                if (++sIdx < slotSize) {
                    sb.append(",");
                } else {
                    sb.append(" |");
                }
            }

            if (++tnIdx == tnSize) {
                sb.append("];");
            }

            sb.append(String.format(" %% %d: %s", tn.id, tn.email));
        }

        sb.append("\n\n");
    }


    private static void appendRaumVerfuegbarkeiten(List<Raum> raeume, List<EventSlot> slots, List<Pflichtvortrag> pflichtvortraege, StringBuilder sb) {
        // Raum-Verfügbarkeiten für Wahlvorträge (Berücksichtigung der Pflichtvorträge)
        sb.append("%In welchen Slots Räume für Wahlvorträge planbar ist:\n");

        int raeumeSize = raeume.size();
        int slotSize = slots.size();
        int raumIdx = 0;
        sb.append("raum_belegbar = [| %% Slot 1..").append(slots.size());
        for (Raum raum : raeume) {
            sb.append("\n");
            int sIdx = 0;
            for (EventSlot s : slots) {
                boolean belegtByPflicht = pflichtvortraege.stream()
                        .anyMatch(pv -> pv.pflichtslot.id.equals(s.id) && pv.pflichtraum.id.equals(raum.id));
                boolean generellOk = raum.verfuegbareSlots.contains(s);
//                sb.append(!belegtByPflicht && generellOk ? "true" : "false");
                sb.append("true"); // TODO entfernen
                if (++sIdx < slotSize) {
                    sb.append(",");
                } else {
                    sb.append(" |");
                }
            }

            if (++raumIdx == raeumeSize) {
                sb.append("];");
            }

            sb.append(String.format(" %% %d: %s", raum.id, raum.name));
        }

        sb.append("\n\n");
    }

    private void speichereErgebnisse(String json, List<Teilnehmer> teilnehmer, List<Wahlvortrag> vortraege, List<EventSlot> slots, List<Raum> raeume) throws Exception {
        LOG.info("Ergebnisse:\n" + json);
        JsonNode root = objectMapper.readTree(json);
        JsonNode instanzSlot = root.get("instanz_slot");
        JsonNode instanzRaum = root.get("instanz_raum");
        JsonNode besucht = root.get("besucht");

        for (int pIdx = 0; pIdx < teilnehmer.size(); pIdx++) {
            for (int wIdx = 0; wIdx < vortraege.size(); wIdx++) {
                for (int iIdx = 0; iIdx < instanzSlot.get(0).size(); iIdx++) {
                    if (besucht.get(pIdx).get(wIdx).get(iIdx).asBoolean()) {
                        int sIdx = instanzSlot.get(wIdx).get(iIdx).asInt() - 1;
                        int rIdx = instanzRaum.get(wIdx).get(iIdx).asInt() - 1;
                        if (sIdx >= 0 && rIdx >= 0) {
                            Zuweisung z = new Zuweisung();
                            z.teilnehmer = teilnehmer.get(pIdx);
                            z.vortrag = vortraege.get(wIdx);
                            z.slot = slots.get(sIdx);
                            z.raum = raeume.get(rIdx);
                            z.persist();
                        }
                    }
                }
            }
        }
    }
}
