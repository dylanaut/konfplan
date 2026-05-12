package kreyj.konfplan.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import kreyj.konfplan.dto.SolverConfigDto;
import kreyj.konfplan.persistence.*;
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
import java.util.stream.Collectors;
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

    @Inject
    ProtokollService protokollService;

    @Transactional
    public void starteOptimierung(Long veranstaltungId, SolverConfigDto config) throws Exception {
        LOG.info("Starte Optimierung für Veranstaltung: " + veranstaltungId);
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

            String dznContent = generiereDzn(teilnehmer, wahlvortraege, slots, raeume, pflichtvortraege);
            LOG.info("MiniZinc Datendatei generiert.");
            LOG.trace("DZN Content:\n" + dznContent);

            Path tempDzn = Files.createTempFile("planung_", ".dzn");
            Files.writeString(tempDzn, dznContent, StandardCharsets.UTF_8);

            try {
                String resultJson = rufeMiniZincAuf(tempDzn, config.solver, config.timeout, config.numThreads);

                if (resultJson != null && !resultJson.isEmpty() && resultJson.contains("instanz_slot")) {
                    speicherePlanungsergebnis(veranstaltung, resultJson, config);
                    protokollService.log(ProtokollKategorie.PLANUNG, "Optimierung erfolgreich",
                            "Optimierung für '" + vName + "' abgeschlossen. Ergebnis wurde gespeichert.", veranstaltungId);
                } else {
                    protokollService.log(ProtokollKategorie.PLANUNG, "Optimierung fehlgeschlagen", "MiniZinc konnte keine Lösung finden.", veranstaltungId);
                    throw new RuntimeException("MiniZinc konnte keine Lösung für die Wahlvorträge finden.");
                }
            } finally {
                Files.deleteIfExists(tempDzn);
            }
        } catch (Exception e) {
            protokollService.log(ProtokollKategorie.PLANUNG, "Fehler bei Optimierung", e.getMessage(), veranstaltungId);
            throw e;
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
        appendOidArrays(teilnehmer, wahlvortraege, slots, raeume, sb);

        return sb.toString();
    }

    private void appendOidArrays(List<Teilnehmer> teilnehmer, List<Wahlvortrag> wahlvortraege, List<EventSlot> slots, List<Raum> raeume, StringBuilder sb) {
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
            String slot_ids = constantSlotIds;
            sb.append(String.format("(oid: %d, referent_id: %d, belegbare_slots: [%s])", wv.id, refMap.get(wv.referent.id), slot_ids));
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
        sb.append("%In welchen Slots jeder Teilnehmer für Wahlvorträge planbar ist:\n");
        int tnSize = teilnehmer.size();
        int slotSize = slots.size();
        int tnIdx = 0;
        sb.append("tn_verfuegbar = [| %% Slot 1..").append(slots.size());
        for (Teilnehmer tn : teilnehmer) {
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
        ergebnis.jsonErgebnis = jsonErgebnis;
        ergebnis.solver = config.solver;
        ergebnis.timeout = config.timeout;
        ergebnis.persist();
        LOG.info("Planungsergebnis für Veranstaltung '" + veranstaltung.name + "' wurde gespeichert/aktualisiert.");
    }
}
