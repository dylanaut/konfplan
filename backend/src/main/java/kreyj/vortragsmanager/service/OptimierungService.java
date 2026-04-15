package kreyj.vortragsmanager.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import kreyj.vortragsmanager.dto.SolverConfigDto;
import kreyj.vortragsmanager.entity.*;
import org.jboss.logging.Logger;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@ApplicationScoped
public class OptimierungService {

    private static final Logger LOG = Logger.getLogger(OptimierungService.class);
    private static final String MZN_MODEL_PATH = "src/main/resources/minizinc/vortragsplanung.mzn";
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    @Inject
    ObjectMapper objectMapper;

    @Transactional
    public void starteOptimierung(Long veranstaltungId, SolverConfigDto config) throws Exception {
        LOG.info("Starte Optimierung für Veranstaltung: " + veranstaltungId);

        // 1. Daten laden
        List<Teilnehmer> teilnehmer = Teilnehmer.find("veranstaltung.id", veranstaltungId).list();
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
        Path tempDzn = Files.createTempFile("planung_", ".dzn");
        Files.writeString(tempDzn, dznContent);

        try {
            // 5. MiniZinc aufrufen
            String resultJson = rufeMiniZincAuf(tempDzn, config.solver, config.timeout);

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

    private String rufeMiniZincAuf(Path dznPath, String solver, int timeoutSeconds) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(
                "minizinc", "--solver", solver, "--json-output",
                "--time-limit", String.valueOf(timeoutSeconds * 1000),
                MZN_MODEL_PATH, dznPath.toAbsolutePath().toString()
        );

        pb.redirectErrorStream(true);
        Process process = pb.start();

        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().startsWith("{")) output.append(line);
                LOG.debug("MZN: " + line);
            }
        }

        process.waitFor(timeoutSeconds + 5, TimeUnit.SECONDS);
        return output.toString();
    }

    private String generiereDzn(List<Teilnehmer> teilnehmer, List<Wahlvortrag> vortraege, List<EventSlot> slots, List<Raum> raeume, List<Pflichtvortrag> pflichtvortraege) {
        StringBuilder sb = new StringBuilder();
        sb.append("n_slots = ").append(slots.size()).append(";\n");
        sb.append("n_raeume = ").append(raeume.size()).append(";\n");
        sb.append("n_wahlvortraege = ").append(vortraege.size()).append(";\n");
        sb.append("n_personen = ").append(teilnehmer.size()).append(";\n");
        sb.append("max_instanzen = 3;\n");
        sb.append("opt_referent_raumtreue = true;\n\n");

        // Slot & Raum Daten (Records)
        sb.append("s_daten = [").append(slots.stream().map(s -> String.format("(id: %d, tag: \"\", start: \"%s\", ende: \"%s\")", slots.indexOf(s)+1, s.startTime.format(TIME_FORMAT), s.endTime.format(TIME_FORMAT))).collect(Collectors.joining(", "))).append("];\n");
        sb.append("r_daten = [").append(raeume.stream().map(r -> String.format("(name: \"%s\", kapazitaet: %d, etage: \"%s\")", r.name, r.kapazitaet, r.etage != null ? r.etage : "")).collect(Collectors.joining(", "))).append("];\n\n");

        // Referenten
        Map<Long, Integer> refMap = new HashMap<>();
        int refCounter = 1;
        for (Wahlvortrag v : vortraege) { if (!refMap.containsKey(v.referent.id)) refMap.put(v.referent.id, refCounter++); }

        // Vorträge
        sb.append("w_daten = [").append(vortraege.stream().map(v -> {
            String sIds = v.wahlSlots.stream().map(s -> String.valueOf(slots.indexOf(s) + 1)).collect(Collectors.joining(", "));
            return String.format("(name: \"%s\", referent_id: %d, moegliche_slot_ids: [%s])", v.titel, refMap.get(v.referent.id), sIds);
        }).collect(Collectors.joining(", "))).append("];\n\n");

        // Personen
        sb.append("p_daten = [").append(teilnehmer.stream().map(t -> String.format("(name: \"%s\", klasse: \"%s\", prios: [])", t.lastName, t.gruppe)).collect(Collectors.joining(", "))).append("];\n\n");

        // Prioritäten Matrix
        sb.append("p_prioritaeten = [|\n");
        for (Teilnehmer t : teilnehmer) {
            for (Wahlvortrag v : vortraege) {
                Prioritaet p = Prioritaet.find("teilnehmer = ?1 and vortrag = ?2", t, v).firstResult();
                sb.append(p != null ? p.prioWert : 0).append(", ");
            }
            sb.append("\n|");
        }
        sb.append("];\n\n");

        // Verfügbarkeiten (Berücksichtigung der Pflichtvorträge)
        sb.append("tn_verfuegbar = [|\n");
        for (Teilnehmer t : teilnehmer) {
            for (EventSlot s : slots) {
                boolean belegtByPflicht = pflichtvortraege.stream().anyMatch(pv -> pv.pflichtslot.id.equals(s.id));
                boolean generellOk = t.verfuegbareSlots.contains(s);
                sb.append(!belegtByPflicht && generellOk ? "true" : "false").append(", ");
            }
            sb.append("\n|");
        }
        sb.append("];\n\n");

        sb.append("raum_verfuegbar = [|\n");
        for (Raum r : raeume) {
            for (EventSlot s : slots) {
                boolean belegtByPflicht = pflichtvortraege.stream().anyMatch(pv -> pv.pflichtslot.id.equals(s.id) && pv.pflichtraum.id.equals(r.id));
                boolean generellOk = r.verfuegbareSlots.contains(s);
                sb.append(!belegtByPflicht && generellOk ? "true" : "false").append(", ");
            }
            sb.append("\n|");
        }
        sb.append("];\n");

        return sb.toString();
    }

    private void speichereErgebnisse(String json, List<Teilnehmer> teilnehmer, List<Wahlvortrag> vortraege, List<EventSlot> slots, List<Raum> raeume) throws Exception {
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
