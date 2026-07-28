package kreyj.konfplan.domain.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.json.Json;
import jakarta.json.JsonException;
import jakarta.transaction.Transactional;
import kreyj.konfplan.adapter.in.web.dto.SolverConfig;
import kreyj.konfplan.domain.exception.CollisionsException;
import kreyj.konfplan.persistence.NutzerVerfuegbarkeit;
import kreyj.konfplan.persistence.Pflichtvortrag;
import kreyj.konfplan.persistence.Planungsergebnis;
import kreyj.konfplan.persistence.Prioritaet;
import kreyj.konfplan.persistence.ProtokollKategorie;
import kreyj.konfplan.persistence.Raum;
import kreyj.konfplan.persistence.RaumVerfuegbarkeit;
import kreyj.konfplan.persistence.Referent;
import kreyj.konfplan.persistence.Slot;
import kreyj.konfplan.persistence.Teilnehmer;
import kreyj.konfplan.persistence.Veranstaltung;
import kreyj.konfplan.persistence.Wahlvortrag;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.IntStream;

import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toMap;
import static kreyj.konfplan.persistence.NutzerVerfuegbarkeitId.nvId;
import static kreyj.konfplan.persistence.RaumVerfuegbarkeitId.rvId;

@ApplicationScoped
public class PlanErstellungService {
    public static final String LINE_SEP = System.lineSeparator();
    private static final Logger LOG = Logger.getLogger(PlanErstellungService.class);
    private static final String MZN_MODEL_FILE = "konfplan.mzn";
    private static final DateTimeFormatter WEEKDAY_TIME_FORMAT = DateTimeFormatter.ofPattern("EE,HH:mm");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    private final ProtokollService protokollService;
    private final ObjectMapper objectMapper;
    private final AuffuellungService auffuellungService;

    @ConfigProperty(name = "minizinc.path", defaultValue = "/opt/homebrew/bin/minizinc")
    String miniZincPath;

    private volatile Process runningProcess;

    // Status der asynchronen Planerstellung für das /status-Polling der UI.
    @Getter
    private volatile boolean planning;
    private volatile boolean cancelled;
    /**
     * -- GETTER --
     * Fehlermeldung des letzten (asynchronen) Planerstellungs-Laufs, oder
     * , wenn der
     * letzte Lauf erfolgreich war bzw. noch keiner stattfand. Wird zu Beginn jedes Laufs zurückgesetzt.
     */
    @Getter
    private volatile String lastError;


    public PlanErstellungService(ProtokollService protokollService, ObjectMapper objectMapper,
                                 AuffuellungService auffuellungService) {
        this.protokollService = protokollService;
        this.objectMapper = objectMapper;
        this.auffuellungService = auffuellungService;
    }


    @PreDestroy
    public void shutdown() {
        cancel();
    }


    public void erstellePlan(Long veranstaltungId, SolverConfig config, String username) throws Exception {
        erstellePlan(veranstaltungId, config, MZN_MODEL_FILE, username);
    }


    @Transactional
    public void erstellePlan(Long veranstaltungId, SolverConfig config, String modelName, String username) throws Exception {
        Veranstaltung veranstaltung = Veranstaltung.findById(veranstaltungId);
        assert veranstaltung != null;
        String vName = veranstaltung.getName();

        LOG.info("Starte Planerstellung für Veranstaltung: " + vName);

        URL modelUrl = getClass().getClassLoader().getResource("minizinc/" + modelName);
        if (null == modelUrl) {
            throw new FileNotFoundException("MiniZinc model not found: " + modelName);
        }

        werfeBeiKollisionen(veranstaltung, veranstaltungId, username);

        planning = true;
        cancelled = false;
        lastError = null;
        protokollService.log(ProtokollKategorie.PLANUNG, "Planerstellung gestartet",
            "Planerstellung für '" + vName + "' mit Solver '" + config.getSolver() + "' von " + username + " gestartet.", veranstaltungId, veranstaltungId, username);

        try {
            List<Teilnehmer> teilnehmer = veranstaltung.teilnehmer();
            List<Wahlvortrag> wahlvortraege = veranstaltung.getWahlvortraege();
            Set<Slot> slots = veranstaltung.getSlots();
            List<Raum> raeume = veranstaltung.getRaeume();

            if (slots.isEmpty() || teilnehmer.isEmpty() || wahlvortraege.isEmpty()) {
                LOG.warn("Keine Wahlvorträge, Slots oder Teilnehmer vorhanden. Planerstellung wird nicht gestartet.");
                String message = "Voraussetzungen (Teilnehmer, Slots, Wahlvorträge) nicht erfüllt.";
                protokollService.log(ProtokollKategorie.PLANUNG, "Planerstellung abgebrochen", message, veranstaltungId, veranstaltungId, username);
                lastError = message;
                return;
            }

            String dznContent = generiereDzn(veranstaltung, teilnehmer, wahlvortraege, slots, raeume,
                config.getMaxInstanzen(), config.getMaxWvsProTn());
            Path tempDzn = Files.createTempFile("planung_", ".dzn");
            Files.writeString(tempDzn, dznContent, StandardCharsets.UTF_8);
            LOG.info("MiniZinc Datendatei:\n" + dznContent);

            try {
                String resultJson = rufeMiniZincAuf(Paths.get(modelUrl.toURI()), tempDzn, config);

                if (resultJson.contains("instanz_slot") && isValidJson(resultJson)) {
                    speicherePlanungsergebnis(veranstaltung, resultJson, config);
                    protokollService.log(ProtokollKategorie.PLANUNG, "Planerstellung erfolgreich",
                        "Planerstellung für '" + vName + "' abgeschlossen. Ergebnis wurde gespeichert.", veranstaltungId, veranstaltungId, username);
                } else {
                    String message = "MiniZinc konnte keine Lösung finden.";
                    protokollService.log(ProtokollKategorie.PLANUNG, "Planerstellung fehlgeschlagen", message, veranstaltungId, veranstaltungId, username);
                    if (!cancelled) {
                        lastError = message;
                    }
                }
            } finally {
                Files.deleteIfExists(tempDzn);
            }
        } catch (Exception e) {
            protokollService.log(ProtokollKategorie.PLANUNG, "Fehler bei Planerstellung", e.getMessage(), veranstaltungId, veranstaltungId, username);
            if (!cancelled) {
                lastError = (e.getMessage() != null) ? e.getMessage() : e.toString();
            }
            throw e;
        } finally {
            planning = false;
        }
    }


    /**
     * Synchrone Vorbedingungs-Prüfung für die UI: Wirft eine {@link CollisionsException}, wenn die
     * Eingangsdaten Kollisionen enthalten. Wird vor dem asynchronen Start der Planerstellung aufgerufen,
     * damit die Fehlermeldung in der HTTP-Antwort (BusinessExceptionMapper -> 400) zurückkommt.
     */
    @Transactional
    public void pruefeKollisionenOrThrow(Long veranstaltungId, String username) {
        Veranstaltung veranstaltung = Veranstaltung.findById(veranstaltungId);
        assert veranstaltung != null;
        werfeBeiKollisionen(veranstaltung, veranstaltungId, username);
    }


    private void werfeBeiKollisionen(Veranstaltung veranstaltung, Long veranstaltungId, String username) {
        List<Kollision> kollisionen = pruefeKollisionen(veranstaltung);
        if (kollisionen.isEmpty()) {
            return;
        }
        String vName = veranstaltung.getName();
        String details = kollisionen.stream().map(Kollision::nachricht).collect(joining(LINE_SEP));
        String message = "Inkonsistente Daten für Planerstellung in '" + vName + "':" + LINE_SEP + details;
        LOG.warn(message);
        protokollService.log(ProtokollKategorie.PLANUNG, "Planerstellung abgebrochen",
            "Planerstellung für '" + vName + "' abgebrochen:" + LINE_SEP + details,
            veranstaltungId, veranstaltungId, username);
        throw new CollisionsException(message);
    }


    List<Kollision> pruefeKollisionen(Veranstaltung veranstaltung) {
        List<Kollision> kollisionen = new ArrayList<>();

        List<Teilnehmer> teilnehmer = veranstaltung.teilnehmer();
        List<Pflichtvortrag> pflichtvortraege = veranstaltung.getPflichtvortraege();

        // Teilnehmer dürfen für ihren Pflichtslot nicht mehr als verfügbar markiert sein.
        for (Teilnehmer tn : teilnehmer) {
            NutzerVerfuegbarkeit nv = NutzerVerfuegbarkeit.findById(nvId(tn, veranstaltung));
            if (null == nv) {
                continue;
            }
            for (Pflichtvortrag pv : pflichtvortraege) {
                Slot pflichtslot = pv.getPflichtslot();
                if (tn.gehoertZuGruppe(pv.getPflichtgruppe())
                    && nv.getVerfuegbareSlotIds().contains(pflichtslot.getId())) {
                    String tnName = StringUtils.strip(tn.getFullName());
                    if (StringUtils.isBlank(tnName)) {
                        tnName = tn.getEmail();
                    }
                    kollisionen.add(new Kollision(Kollision.Typ.TEILNEHMER_VERFUEGBARKEIT,
                        "Verfügbarkeits-Kollision: " + tnName + " (" + pv.getPflichtgruppe()
                            + ") hat Pflichtvortrag in Slot " + pflichtslot.getDescription()
                            + ". Bitte seine 'Verfügbaren Slots' anpassen."));
                }
            }
        }

        // Raum/Slot-Kollisionen zwischen Pflicht- und Wahlvorträgen erkennen.
        for (Pflichtvortrag pv : pflichtvortraege) {
            Raum pflichtraum = pv.getPflichtraum();
            Slot pflichtslot = pv.getPflichtslot();

            RaumVerfuegbarkeit rv = RaumVerfuegbarkeit.findById(rvId(pflichtraum, veranstaltung));
            if (null != rv && rv.getVerfuegbareSlotIds().contains(pflichtslot.getId())) {
                kollisionen.add(new Kollision(Kollision.Typ.RAUM_SLOT,
                    "Pflichtvortrag '" + pv.getTitel() + "' ist reserviert für Raum '" + pflichtraum.getName()
                        + "' und Slot " + pflichtslot.getDescription()
                        + ". Dieser Raum steht gleichzeitig für Wahlvorträge zur Verfügung, vgl. Verfügbare Slots: "
                        + rv.getVerfuegbareSlotIds().stream()
                        .map(id -> (Slot) Slot.findById(id))
                        .map(Slot::getDescription)
                        .collect(joining(", "))
                        + "."));
            }
        }

        // Referenten-Verfügbarkeit für ihren eigenen Pflichtvortrag prüfen.
        for (Pflichtvortrag pv : pflichtvortraege) {
            Referent referent = pv.getReferent();
            Slot pflichtslot = pv.getPflichtslot();
            NutzerVerfuegbarkeit nv = NutzerVerfuegbarkeit.findById(nvId(referent, veranstaltung));
            if (null != nv && nv.getVerfuegbareSlotIds().contains(pflichtslot.getId())) {
                kollisionen.add(new Kollision(Kollision.Typ.REFERENT_VERFUEGBARKEIT,
                    "Verfügbarkeits-Kollision: Referent " + referent.getFullName() + " hat Pflichtvortrag '"
                        + pv.getTitel() + "' in Slot " + pflichtslot.getDescription()
                    + ", ist dafür aber weiterhin als verfügbar markiert. Bitte seine 'Verfügbaren Slots' anpassen."));
            }
        }

        return kollisionen;
    }


    public String rufeMiniZincAuf(Path modelPath, Path dznPath, SolverConfig solverConfig) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>(Arrays.asList(
            miniZincPath, "--solver", solverConfig.getSolver(),
            "--time-limit", String.valueOf(solverConfig.getTimeout() * 1000),
            "--parallel", String.valueOf(solverConfig.getNumThreads())
        ));
        command.add("--intermediate");
        command.add(modelPath.toAbsolutePath().toString());
        command.add(dznPath.toAbsolutePath().toString());

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);

        String lastJsonSolution = "";
        StringBuilder fullLog = new StringBuilder();

        try {
            runningProcess = pb.start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(runningProcess.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    fullLog.append(line).append(LINE_SEP);
                    if (line.trim().startsWith("{") && isValidJson(line)) {
                        lastJsonSolution = line;
                        LOG.debugf("Zwischenlösung gefunden: %s", line);
                    }
                }
            }

            int exitCode = runningProcess.waitFor();
            LOG.info("MiniZinc-Prozess beendet mit Exit-Code: " + exitCode);

        } finally {
            runningProcess = null;
        }

        String output = fullLog.toString();
        LOG.info("Vollständige MiniZinc-Ausgabe:\n" + output);

        if (output.contains("=====UNSATISFIABLE=====")) {
            throw new MinizincException(MinizincException.MZ_Exception.UNSATISFIABLE);
        }

        if (output.contains("Error:")) {
            throw new MinizincException(MinizincException.MZ_Exception.INVOCATION_ERROR, output);
        }

        if (lastJsonSolution.isEmpty() && output.contains("=====UNKNOWN=====")) {
            throw new MinizincException(MinizincException.MZ_Exception.TIMEOUT,
                "In der vorgegebenen Zeit (" + solverConfig.getTimeout() + " Sek.) konnte kein Ergebnis berechnet werden.");
        }

        if (!lastJsonSolution.isEmpty()) {
            return lastJsonSolution;
        }

        return "";
    }


    public void cancel() {
        // Markiert den Lauf als abgebrochen, damit die nachlaufende erstellePlan-Logik
        // keinen irreführenden lastError (z.B. "keine Lösung") mehr setzt.
        cancelled = true;
        lastError = null;
        if (runningProcess != null && runningProcess.isAlive()) {
            runningProcess.destroyForcibly();
            LOG.info("MiniZinc-Prozess wurde abgebrochen.");
        }
    }


    private String generiereDzn(Veranstaltung veranstaltung, List<Teilnehmer> teilnehmer, List<Wahlvortrag> wahlvortraege,
                                Set<Slot> slots, List<Raum> raeume,
                                int maxInstanzen, int maxWvsProTn) {
        StringBuilder sb = new StringBuilder();
        sb.append("%Generiert am: ").append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))).append(",Version: 1.0;\n");
        sb.append("max_instanzen = ").append(maxInstanzen).append(";\n");
        sb.append("max_wvs_pro_tn = ").append(maxWvsProTn).append(";\n");
        sb.append("opt_referent_raumtreue = true;\n\n");

        appendSlots(slots, sb);
        appendRaeume(raeume, sb);
        appendTeilnehmer(teilnehmer, sb);
        appendWahlvortraege(wahlvortraege, slots, sb);
        appendTnPrios(teilnehmer, wahlvortraege, sb);
        appendTnVerfuegbarkeiten(veranstaltung, teilnehmer, slots, sb);
        appendReferentVerfuegbarkeiten(wahlvortraege, veranstaltung, slots, sb);
        appendRaumVerfuegbarkeiten(veranstaltung, raeume, slots, sb);
        appendEntityOids(teilnehmer, wahlvortraege, slots, raeume, sb);

        return sb.toString();
    }


    private static void appendEntityOids(List<Teilnehmer> teilnehmer, List<Wahlvortrag> wahlvortraege, Set<Slot> slots, List<Raum> raeume, StringBuilder sb) {
        sb.append("teilnehmer_oids = [").append(teilnehmer.stream().map(t -> String.valueOf(t.getId())).collect(joining(","))).append("];\n");
        sb.append("wahlvortrag_oids = [").append(wahlvortraege.stream().map(v -> String.valueOf(v.getId())).collect(joining(","))).append("];\n");
        sb.append("slot_oids = [").append(slots.stream().map(s -> String.valueOf(s.getId())).collect(joining(","))).append("];\n");
        sb.append("raum_oids = [").append(raeume.stream().map(r -> String.valueOf(r.getId())).collect(joining(","))).append("];\n");
    }


    private static void appendSlots(Set<Slot> slots, StringBuilder sb) {
        final int nSlots = slots.size();
        sb.append("n_slots = ").append(nSlots).append(";\n");
        sb.append("slots = [");
        int sIdx = 0;
        for (Slot s : slots) {
            sb.append(String.format("\n(oid: %d)", s.getId()));
            if (++sIdx < nSlots) {
                sb.append(",");
            } else {
                sb.append(" ");
            }
            sb.append(String.format(" %% %d: %s - %s", sIdx, s.getStartTime().format(WEEKDAY_TIME_FORMAT), s.getEndTime().format(TIME_FORMAT)));
        }
        sb.append("\n];\n\n");
    }


    private static void appendRaeume(List<Raum> raeume, StringBuilder sb) {
        final int nRaeume = raeume.size();
        sb.append("n_raeume = ").append(nRaeume).append(";\n");
        sb.append("raeume = [");

        int rIdx = 0;
        for (Raum raum : raeume) {
            sb.append(String.format("\n(oid: %d, kapazitaet: %d)", raum.getId(), raum.getKapazitaet()));
            if (++rIdx < nRaeume) {
                sb.append(",");
            } else {
                sb.append(" ");
            }
            sb.append(String.format(" %% %d: %s, %d", rIdx, raum.getName(), raum.getKapazitaet()));
        }
        sb.append("\n];\n\n");
    }


    private static void appendTeilnehmer(List<Teilnehmer> teilnehmer, StringBuilder sb) {
        final int nTNs = teilnehmer.size();
        sb.append("n_teilnehmer = ").append(nTNs).append(";\n");
        sb.append("teilnehmer = [");

        int pIdx = 0;
        for (Teilnehmer tn : teilnehmer) {
            sb.append(String.format("\n(oid: %d)", tn.getId()));
            if (++pIdx < nTNs) {
                sb.append(",");
            } else {
                sb.append(" ");
            }
            sb.append(String.format(" %% %d: %s %s", pIdx, tn.getEmail(), tn.getGruppen()));
        }

        sb.append("\n];\n\n");
    }


    private static void appendWahlvortraege(List<Wahlvortrag> wahlvortraege, Set<Slot> slots, StringBuilder sb) {
        final int nWVs = wahlvortraege.size();
        sb.append("n_wahlvortraege = ").append(nWVs).append(";\n");
        Map<Long, Integer> refMap = new HashMap<>();
        int refCounter = 1;
        for (Wahlvortrag v : wahlvortraege) {
            if (!refMap.containsKey(v.getReferent().getId())) {
                refMap.put(v.getReferent().getId(), refCounter++);
            }
        }

        int wvIdx = 0;
        sb.append("wahlvortraege = [");
        String constantSlotIds = IntStream.rangeClosed(1, slots.size()).mapToObj(String::valueOf).collect(joining(","));

        for (Wahlvortrag wv : wahlvortraege) {
            sb.append("\n");
            sb.append(String.format("(oid: %d, referent_id: %d, belegbare_slots: [%s])", wv.getId(), refMap.get(wv.getReferent().getId()), constantSlotIds));
            if (++wvIdx < nWVs) {
                sb.append(",");
            } else {
                sb.append(" ");
            }
            sb.append(String.format(" %% %d: %s", wvIdx, wv.getTitel().substring(0, Math.min(25, wv.getTitel().length()))));
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
                sb.append(p != null ? p.getPrioWert() : 0);
                if (++wvIdx < wvSize) {
                    sb.append(",");
                } else {
                    sb.append(" |");
                }
            }
            if (++tnIdx == tnSize) {
                sb.append("];");
            }
            sb.append(String.format(" %% %d: %s", tn.getId(), tn.getEmail()));
        }
        sb.append("\n\n");
    }


    private static void appendTnVerfuegbarkeiten(Veranstaltung veranstaltung, List<Teilnehmer> teilnehmer, Set<Slot> slots,
                                                 StringBuilder sb) {
        sb.append("%In welchen Slots jeder Teilnehmer für Wahlvorträge einer Veranstaltung verfügbar ist:\n");
        int tnSize = teilnehmer.size();
        int slotSize = slots.size();
        int tnIdx = 0;
        Map<Long, NutzerVerfuegbarkeit> nvMap =
            NutzerVerfuegbarkeit.<NutzerVerfuegbarkeit>list("veranstaltungId = ?1", veranstaltung.getId())
                .stream().collect(toMap(NutzerVerfuegbarkeit::getNutzerId, Function.identity()));
        sb.append("tn_verfuegbar = [| %% Slot 1..").append(slots.size());
        for (Teilnehmer tn : teilnehmer) {
            sb.append("\n");
            int sIdx = 0;
            NutzerVerfuegbarkeit nv = nvMap.get(tn.getId());
            Set<Long> verfSlotIds = nv.getVerfuegbareSlotIds();
            for (Slot s : slots) {
                sb.append(verfSlotIds.contains(s.getId()) ? "true" : "false");
                if (++sIdx < slotSize) {
                    sb.append(",");
                } else {
                    sb.append(" |");
                }
            }
            if (++tnIdx == tnSize) {
                sb.append("];");
            }
            sb.append(String.format(" %% %d: %s", tn.getId(), tn.getEmail()));
        }
        sb.append("\n\n");
    }


    private static void appendRaumVerfuegbarkeiten(Veranstaltung veranstaltung, List<Raum> raeume, Set<Slot> slots, StringBuilder sb) {
        sb.append("%In welchen Slots Räume für Wahlvorträge einplanbar ist:\n");
        int raeumeSize = raeume.size();
        int slotSize = slots.size();
        Map<Long, RaumVerfuegbarkeit> rvMap =
            RaumVerfuegbarkeit.<RaumVerfuegbarkeit>list("veranstaltungId = ?1", veranstaltung.getId())
                .stream().collect(toMap(RaumVerfuegbarkeit::getRaumId, Function.identity()));

        List<RaumVerfuegbarkeit> alleRVs = RaumVerfuegbarkeit.listAll();

        int raumIdx = 0;
        sb.append("raum_belegbar = [| %% Slot 1..").append(slots.size());
        for (Raum raum : raeume) {
            sb.append("\n");
            RaumVerfuegbarkeit rv = rvMap.get(raum.getId());
            Set<Long> verfSlotIds = rv.getVerfuegbareSlotIds();
            int sIdx = 0;
            for (Slot s : slots) {
                sb.append(verfSlotIds.contains(s.getId()) ? "true" : "false");
                if (++sIdx < slotSize) {
                    sb.append(",");
                } else {
                    sb.append(" |");
                }
            }
            if (++raumIdx == raeumeSize) {
                sb.append("];");
            }
            sb.append(String.format(" %% %d: %s", raum.getId(), raum.getName()));
        }
        sb.append("\n\n");
    }


    private static void appendReferentVerfuegbarkeiten(List<Wahlvortrag> wahlvortraege, Veranstaltung veranstaltung,
                                                       Set<Slot> slots, StringBuilder sb) {
        sb.append("%In welchen Slots der Referent eines Wahlvortrags verfügbar ist (Pflichtvortrag-Kollisionen):\n");
        // Reihenfolge MUSS mit appendWahlvortraege's refMap übereinstimmen: dieselbe 'wahlvortraege'-Liste,
        // distinct() auf geordnetem Stream erhält First-Encounter-Reihenfolge -> identische 1-basierte Indizes.
        List<Referent> referenten = wahlvortraege.stream().map(Wahlvortrag::getReferent).distinct().toList();
        int refSize = referenten.size();
        int slotSize = slots.size();
        Map<Long, NutzerVerfuegbarkeit> nvMap =
            NutzerVerfuegbarkeit.<NutzerVerfuegbarkeit>list("veranstaltungId = ?1", veranstaltung.getId())
                .stream().collect(toMap(NutzerVerfuegbarkeit::getNutzerId, Function.identity()));

        int refIdx = 0;
        sb.append("referent_verfuegbar = [| %% Slot 1..").append(slots.size());
        for (Referent referent : referenten) {
            sb.append("\n");
            Set<Long> verfSlotIds = nvMap.get(referent.getId()).getVerfuegbareSlotIds();
            int sIdx = 0;
            for (Slot s : slots) {
                sb.append(verfSlotIds.contains(s.getId()) ? "true" : "false");
                if (++sIdx < slotSize) {
                    sb.append(",");
                } else {
                    sb.append(" |");
                }
            }
            if (++refIdx == refSize) {
                sb.append("];");
            }
            sb.append(String.format(" %% %d: %s", refIdx, referent.getFullName()));
        }
        sb.append("\n\n");
    }


    @Transactional
    public void speicherePlanungsergebnis(Veranstaltung veranstaltung, String jsonErgebnis, SolverConfig config) {
        Planungsergebnis ergebnis = Planungsergebnis.find("veranstaltung", veranstaltung).firstResult();
        if (null == ergebnis) {
            ergebnis = new Planungsergebnis();
            ergebnis.setVeranstaltung(veranstaltung);
        }

        try {
            ObjectNode root = (ObjectNode) objectMapper.readTree(jsonErgebnis);
            int tnSize = root.get("teilnehmer_oids").size();
            int wvSize = root.get("wahlvortrag_oids").size();

            fixflatArrays(root, tnSize, wvSize, config.getMaxInstanzen());

            Planungsergebnis.MinizincResult result =
                objectMapper.treeToValue(root, Planungsergebnis.MinizincResult.class);

            if (config.isAuffuellen()) {
                auffuellungService.fuelleAuf(veranstaltung, result, config.getMaxWvsProTn());
            }

            String fixedJson = result.toJson();
            LOG.info("###" + fixedJson);

            ergebnis.setJsonErgebnis(fixedJson);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }

        ergebnis.setSolverConfig(config);
        ergebnis.persistAndFlush();

        LOG.info("Planungsergebnis für Veranstaltung '" + veranstaltung.getName() + "' wurde gespeichert/aktualisiert.");
    }


    public static boolean isValidJson(String json) {
        if (StringUtils.isBlank(json)) {
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
        if (null == flat || !flat.isArray()) {
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
        if (null == flat || !flat.isArray()) {
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


    private void fixflatArrays(ObjectNode root, int nTNs, int nWVs, int maxInstanzen) {
        restructure2DArray(root, "instanz_slot", nWVs, maxInstanzen);
        restructure2DArray(root, "instanz_raum", nWVs, maxInstanzen);
        restructure3DArray(root, "besucht", nTNs, nWVs, maxInstanzen);
    }
}
