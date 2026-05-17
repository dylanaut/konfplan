package kreyj.konfplan.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.json.Json;
import jakarta.json.JsonException;
import jakarta.transaction.Transactional;
import kreyj.konfplan.dto.SolverConfigDto;
import kreyj.konfplan.persistence.*;
import org.jboss.logging.Logger;

import java.io.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static java.util.stream.Collectors.joining;

@ApplicationScoped
public class OptimierungService {
    private static final Logger LOG = Logger.getLogger(OptimierungService.class);
    private static final String MZN_MODEL_FILE = "konfplan.mzn";
    private static final DateTimeFormatter WEEKDAY_TIME_FORMAT = DateTimeFormatter.ofPattern("EE,HH:mm");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    @Inject
    ProtokollService protokollService;

    @Inject
    ObjectMapper objectMapper;

    public void starteOptimierung(Long veranstaltungId, SolverConfigDto config) throws Exception {
        starteOptimierung(veranstaltungId, config, MZN_MODEL_FILE);
    }

    @Transactional
    public void starteOptimierung(Long veranstaltungId, SolverConfigDto config, String modelName) throws Exception {
        LOG.info("Starte Optimierung für Veranstaltung: " + veranstaltungId);

        URL modelUrl = getClass().getClassLoader().getResource("minizinc/" + modelName);
        if (modelUrl == null) {
            throw new FileNotFoundException("MiniZinc model not found: " + modelName);
        }

        Veranstaltung veranstaltung = Veranstaltung.findById(veranstaltungId);
        String vName = veranstaltung != null ? veranstaltung.name : "ID: " + veranstaltungId;

        protokollService.log(ProtokollKategorie.PLANUNG, "Optimierung gestartet",
                "Optimierung für '" + vName + "' mit Solver '" + config.solver + "' gestartet.", veranstaltungId);

        try {
            List<Teilnehmer> teilnehmer = Teilnehmer.find("SELECT t FROM Teilnehmer t JOIN t.veranstaltungen v WHERE v.id = ?1", veranstaltungId).list();
            List<Pflichtvortrag> pflichtvortraege = Pflichtvortrag.find("veranstaltung.id", veranstaltungId).list();
            List<Wahlvortrag> wahlvortraege = Wahlvortrag.find("veranstaltung.id", veranstaltungId).list();
            List<EventSlot> slots = EventSlot.find("veranstaltung.id", veranstaltungId).list();
            List<Raum> raeume = Raum.listAll();

            if (slots.isEmpty() || teilnehmer.isEmpty() || wahlvortraege.isEmpty()) {
                LOG.warn("Keine Wahlvorträge, Slots oder Teilnehmer vorhanden. Optimierung wird nicht gestartet.");
                protokollService.log(ProtokollKategorie.PLANUNG, "Optimierung abgebrochen", "Voraussetzungen (Teilnehmer, Slots, Wahlvorträge) nicht erfüllt.", veranstaltungId);
                return;
            }

            String dznContent = generiereDzn(teilnehmer, wahlvortraege, slots, raeume,
                    pflichtvortraege, config.maxInstanzen);
            Path tempDzn = Files.createTempFile("planung_", ".dzn");
            Files.writeString(tempDzn, dznContent, StandardCharsets.UTF_8);
            LOG.info("MiniZinc Datendatei:\n" + dznContent);

            try {
                String resultJson = rufeMiniZincAuf(Paths.get(modelUrl.toURI()), tempDzn, config.solver, config.timeout, config.numThreads);

                if (resultJson.contains("instanz_slot") && isValidJson(resultJson)) {
                    speicherePlanungsergebnis(veranstaltung, resultJson, config);
                    protokollService.log(ProtokollKategorie.PLANUNG, "Optimierung erfolgreich",
                            "Optimierung für '" + vName + "' abgeschlossen. Ergebnis wurde gespeichert.", veranstaltungId);
                } else {
                    protokollService.log(ProtokollKategorie.PLANUNG, "Optimierung fehlgeschlagen", "MiniZinc konnte keine Lösung finden.", veranstaltungId);
                }
            } finally {
                Files.deleteIfExists(tempDzn);
            }
        } catch (Exception e) {
            protokollService.log(ProtokollKategorie.PLANUNG, "Fehler bei Optimierung", e.getMessage(), veranstaltungId);
            throw e;
        }
    }

    String rufeMiniZincAuf(Path modelPath, Path dznPath, String solver,
                           int timeoutSeconds, int numThreads) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>(Arrays.asList(
                "minizinc", "--solver", solver,
                "--time-limit", String.valueOf(timeoutSeconds * 1000),
                "--parallel", String.valueOf(numThreads)
        ));
        // Für Optimierungsprobleme sorgt dieses Flag dafür, dass Zwischenlösungen ausgegeben werden.
        command.add("--intermediate");
        command.add(modelPath.toAbsolutePath().toString());
        command.add(dznPath.toAbsolutePath().toString());

        ProcessBuilder pb = new ProcessBuilder(command);

        pb.redirectErrorStream(true);
        Process process = pb.start();

        String lastJsonSolution = "";
        StringBuilder fullLog = new StringBuilder();
        String delimiter = System.lineSeparator();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                fullLog.append(line).append(delimiter);
                // Eine einfache Prüfung, ob die Zeile ein JSON-Objekt sein könnte.
                // Unsere Modelle sind so konfiguriert, dass sie JSON in einer einzigen Zeile ausgeben.
                if (line.trim().startsWith("{") && isValidJson(line)) {
                    lastJsonSolution = line;
                    LOG.debugf("Zwischenlösung gefunden: %s", line);
                }
            }
        }

        // Warten, bis der Prozess beendet ist und den Exit-Code abrufen.
        // Der Prozess wird durch das --time-limit von selbst beendet.
        int exitCode = process.waitFor();
        LOG.info("MiniZinc-Prozess beendet mit Exit-Code: " + exitCode);

        String output = fullLog.toString();
        LOG.info("Vollständige MiniZinc-Ausgabe:\n" + output);

        // Fehlerbehandlung basierend auf der vollständigen Ausgabe.
        if (output.contains("=====UNSATISFIABLE=====")) {
            throw new MinizincException(MinizincException.MZ_Exception.UNSATISFIABLE);
        }

        if (output.contains("Error:")) {
            // Fängt MiniZinc-Modellfehler, nicht gefundene Solver usw. ab.
            throw new MinizincException(MinizincException.MZ_Exception.INVOCATION_ERROR, output);
        }

        // Wenn wir eine Lösung haben, geben wir sie zurück. Dies ist der Erfolgsfall.
        if (!lastJsonSolution.isEmpty()) {
            return lastJsonSolution;
        }

        // Gibt einen leeren String zurück, wenn keine Lösung gefunden wurde, aber kein expliziter Fehler aufgetreten ist.
        return "";
    }

    private String generiereDzn(List<Teilnehmer> teilnehmer, List<Wahlvortrag> wahlvortraege,
                                List<EventSlot> slots, List<Raum> raeume, List<Pflichtvortrag> pflichtvortraege,
                                int maxInstanzen) {
        StringBuilder sb = new StringBuilder();
        sb.append("%Generiert am: ").append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))).append(",Version: 1.0;\n");
        sb.append("max_instanzen = ").append(maxInstanzen).append(";\n");
        sb.append("opt_referent_raumtreue = true;\n\n");

        appendSlots(slots, sb);
        appendRaeume(raeume, sb);
        appendTeilnehmer(teilnehmer, sb);
        appendWahlvortraege(wahlvortraege, slots, sb);
        appendTnPrios(teilnehmer, wahlvortraege, sb);
        appendTnVerfuegbarkeiten(teilnehmer, slots, pflichtvortraege, sb);
        appendRaumVerfuegbarkeiten(raeume, slots, pflichtvortraege, sb);
        appendOidArrays(teilnehmer, wahlvortraege, slots, raeume, sb);

        return sb.toString();
    }

    private static void appendOidArrays(List<Teilnehmer> teilnehmer, List<Wahlvortrag> wahlvortraege, List<EventSlot> slots, List<Raum> raeume, StringBuilder sb) {
        sb.append("teilnehmer_oids = [").append(teilnehmer.stream().map(t -> String.valueOf(t.id)).collect(joining(","))).append("];\n");
        sb.append("wahlvortrag_oids = [").append(wahlvortraege.stream().map(v -> String.valueOf(v.id)).collect(joining(","))).append("];\n");
        sb.append("slot_oids = [").append(slots.stream().map(s -> String.valueOf(s.id)).collect(joining(","))).append("];\n");
        sb.append("raum_oids = [").append(raeume.stream().map(r -> String.valueOf(r.id)).collect(joining(","))).append("];\n");
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
        Map<Long, Integer> refMap = new HashMap<>();
        int refCounter = 1;
        for (Wahlvortrag v : wahlvortraege) {
            if (!refMap.containsKey(v.referent.id)) {
                refMap.put(v.referent.id, refCounter++);
            }
        }

        int wvIdx = 0;
        sb.append("wahlvortraege = [");
        String constantSlotIds = IntStream.rangeClosed(1, slots.size()).mapToObj(String::valueOf).collect(joining(","));

        for (Wahlvortrag wv : wahlvortraege) {
            sb.append("\n");
            sb.append(String.format("(oid: %d, referent_id: %d, belegbare_slots: [%s])", wv.id, refMap.get(wv.referent.id), constantSlotIds));
            if (++wvIdx < nWVs) {
                sb.append(",");
            }
            sb.append(String.format(" %% %d: %s", wvIdx, wv.titel.substring(0, Math.min(25, wv.titel.length()))));
        }
        sb.append("\n];\n\n");
    }

    private static void appendTnPrios(List<Teilnehmer> teilnehmer, List<Wahlvortrag> wahlvortraege, StringBuilder sb) {
        sb.append("prioritaeten = [|");
        int wvSize = wahlvortraege.size();
        int tnSize = teilnehmer.size();
        int tnIdx = 0;
        for (Teilnehmer tn : teilnehmer) {
            sb.append("\n");
            int wvIdx = 0;
            for (Wahlvortrag v : wahlvortraege) {
                Prioritaet p = Prioritaet.find("teilnehmer = ?1 and vortrag = ?2", tn, v).firstResult();
                sb.append(p != null ? p.prioWert : 0);
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
        sb.append("%In welchen Slots jeder Teilnehmer für Wahlvorträge planbar ist:\n");
        int tnSize = teilnehmer.size();
        int slotSize = slots.size();
        int tnIdx = 0;
        sb.append("tn_verfuegbar = [| %% Slot 1..").append(slots.size());
        for (Teilnehmer tn : teilnehmer) {
            sb.append("\n");
            int sIdx = 0;
            Set<Long> verfSlotIds = tn.getVerfuegbareSlots().stream().map(s -> s.id).collect(Collectors.toSet());
            for (EventSlot s : slots) {
                sb.append(verfSlotIds.contains(s.id) ? "true" : "false");
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
        sb.append("%In welchen Slots Räume für Wahlvorträge planbar ist:\n");
        int raeumeSize = raeume.size();
        int slotSize = slots.size();
        int raumIdx = 0;
        sb.append("raum_belegbar = [| %% Slot 1..").append(slots.size());
        for (Raum raum : raeume) {
            sb.append("\n");
            int sIdx = 0;
            for (EventSlot s : slots) {
                sb.append("true");
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

    @Transactional
    public void speicherePlanungsergebnis(Veranstaltung veranstaltung, String jsonErgebnis, SolverConfigDto config) {
        Planungsergebnis ergebnis = Planungsergebnis.find("veranstaltung", veranstaltung).firstResult();
        if (ergebnis == null) {
            ergebnis = new Planungsergebnis();
            ergebnis.veranstaltung = veranstaltung;
        }

        try {
            ObjectNode root = (ObjectNode) objectMapper.readTree(jsonErgebnis);
            JsonNode inputData = root.get("input_data");
            int tnSize = inputData.get("teilnehmer_oids").size();
            int wvSize = inputData.get("wahlvortrag_oids").size();

            fixflatArrays(root, tnSize, wvSize, config.maxInstanzen);

            String fixedJson = objectMapper.writeValueAsString(root);
            LOG.info("Result (fixed):\n" + fixedJson);
            ergebnis.jsonErgebnis = fixedJson;
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }

        ergebnis.solver = config.solver;
        ergebnis.timeout = config.timeout;
        ergebnis.persist();
        LOG.info("Planungsergebnis für Veranstaltung '" + veranstaltung.name + "' wurde gespeichert/aktualisiert.");
    }

    public static boolean isValidJson(String json) {
        if (json == null || json.isBlank()) {
            return false;
        }
        try (var reader = Json.createReader(new StringReader(json))) {
            reader.readObject();
            return true;
        } catch (JsonException | ClassCastException e) {
            return false;
        }
    }

    // -------------------------------------------------------------------
    // Helper methods
    // -------------------------------------------------------------------

    public void restructure2DArray(ObjectNode root, String fieldName,
                                   int rows, int cols) {
        JsonNode flat = root.get(fieldName);
        if (flat == null || !flat.isArray()) {
            return;
        }

        // Flaches Array → 2D Array
        ArrayNode matrix = objectMapper.createArrayNode();

        for (int i = 0; i < rows; i++) {
            ArrayNode row = objectMapper.createArrayNode();
            for (int j = 0; j < cols; j++) {
                row.add(flat.get(i * cols + j));
            }
            matrix.add(row);
        }

        root.set(fieldName, matrix);
    }

    public void restructure3DArray(ObjectNode root, String fieldName,
                                   int dim1, int dim2, int dim3) {
        JsonNode flat = root.get(fieldName);
        if (flat == null || !flat.isArray()) {
            return;
        }

        // Flaches Array → 3D Array [dim1][dim2][dim3]
        ArrayNode matrix3D = objectMapper.createArrayNode();

        for (int i = 0; i < dim1; i++) {
            ArrayNode matrix2D = objectMapper.createArrayNode();
            for (int j = 0; j < dim2; j++) {
                ArrayNode row = objectMapper.createArrayNode();
                for (int k = 0; k < dim3; k++) {
                    int flatIndex = i * (dim2 * dim3) + j * dim3 + k;
                    row.add(flat.get(flatIndex));
                }
                matrix2D.add(row);
            }
            matrix3D.add(matrix2D);
        }

        root.set(fieldName, matrix3D);
    }

    private JsonNode fixflatArrays(ObjectNode root, int nTNs, int nWVs, int maxInstanzen) {
        restructure2DArray(root, "instanz_slot", nWVs, maxInstanzen);
        restructure2DArray(root, "instanz_raum", nWVs, maxInstanzen);
        restructure3DArray(root, "besucht", nTNs, nWVs, maxInstanzen);

        return root;
    }
}
