package kreyj.vortragsmanager.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import kreyj.vortragsmanager.dto.SolverConfigDto;
import kreyj.vortragsmanager.entity.*;
import org.jboss.logging.Logger;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

@ApplicationScoped
public class OptimierungService {

    private static final Logger LOG = Logger.getLogger(OptimierungService.class);

    private static final String MZN_MODEL_PATH = "src/main/resources/minizinc/vortragsplanung.mzn";

    @Transactional
    public void starteOptimierung(Long veranstaltungId, SolverConfigDto config) throws Exception {
        LOG.info(String.format("Optimierung fuer Veranstaltung %d mit Solver %s (Timeout %ds)", 
                veranstaltungId, config.solver, config.timeout));

        // 1. Daten laden
        List<Teilnehmer> teilnehmer = Teilnehmer.find("veranstaltung.id", veranstaltungId).list();
        List<Vortrag> vortraege = Vortrag.find("veranstaltung.id", veranstaltungId).list();
        List<EventSlot> slots = EventSlot.find("veranstaltung.id", veranstaltungId).list();
        List<Raum> raeume = Raum.listAll();

        // 2. MiniZinc Datendatei generieren
        String dznContent = generiereDzn(teilnehmer, vortraege, slots, raeume);
        Path tempDzn = Files.createTempFile("planung_", ".dzn");
        Files.writeString(tempDzn, dznContent);

        try {
            // 3. MiniZinc aufrufen
            String resultJson = rufeMiniZincAuf(tempDzn, config.solver, config.timeout);

            if (resultJson != null && !resultJson.isEmpty()) {
                Zuweisung.delete("vortrag.veranstaltung.id", veranstaltungId);
                speichereErgebnisse(resultJson, teilnehmer, vortraege, slots, raeume);
            } else {
                throw new RuntimeException("MiniZinc konnte keine Lösung berechnen.");
            }

        } finally {
            Files.deleteIfExists(tempDzn);
        }
    }

    private String rufeMiniZincAuf(Path dznPath, String solver, int timeoutSeconds) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(
                "minizinc",
                "--solver", solver,
                "--json-output",
                "--time-limit", String.valueOf(timeoutSeconds * 1000), // MiniZinc erwartet Millisekunden
                MZN_MODEL_PATH,
                dznPath.toAbsolutePath().toString()
        );

        pb.redirectErrorStream(true);
        Process process = pb.start();

        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        }

        // Wir warten etwas länger als das MiniZinc-Zeitlimit, um sicherzugehen
        boolean finished = process.waitFor(timeoutSeconds + 10, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new RuntimeException("MiniZinc-Prozess ist in einen harten Timeout gelaufen.");
        }

        if (process.exitValue() != 0 && output.toString().isEmpty()) {
            return null;
        }

        return output.toString();
    }

    private String generiereDzn(List<Teilnehmer> teilnehmer, List<Vortrag> vortraege, List<EventSlot> slots, List<Raum> raeume) {
        StringBuilder sb = new StringBuilder();
        sb.append("n_teilnehmer = ").append(teilnehmer.size()).append(";\n");
        sb.append("n_vortraege = ").append(vortraege.size()).append(";\n");
        sb.append("n_slots = ").append(slots.size()).append(";\n");
        sb.append("n_raeume = ").append(raeume.size()).append(";\n");
        return sb.toString();
    }

    private void speichereErgebnisse(String json, List<Teilnehmer> teilnehmer, List<Vortrag> vortraege, List<EventSlot> slots, List<Raum> raeume) {
        LOG.info("Ergebnis-Parsing gestartet.");
        // TODO: Implementierung des JSON-Parsings
    }
}
