package kreyj.konfplan.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import kreyj.konfplan.service.OptimierungService;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@RegisterForReflection
@NoArgsConstructor
@AllArgsConstructor
public class SolverConfigDto {
    public String solver;
    public int timeout = 120; // in Sekunden
    public int numThreads = 4; // Anzahl paralleler Threads
    public int maxInstanzen = 2; // Maximale Anzahl der Instanzen pro Wahlvortrag
}