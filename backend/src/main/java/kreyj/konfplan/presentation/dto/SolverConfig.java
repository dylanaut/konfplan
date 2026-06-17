package kreyj.konfplan.presentation.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@RegisterForReflection
@NoArgsConstructor
@AllArgsConstructor
public class SolverConfig {
    public String solver;
    public int timeout = 120; // in Sekunden
    public int numThreads = 4; // Anzahl paralleler Threads
    public int maxInstanzen = 2; // Maximale Anzahl der Instanzen pro Wahlvortrag
    public boolean auffuellen = true; // Maximale Anzahl der Instanzen pro Wahlvortrag


    public SolverConfig(int timeout, int numThreads, int maxInstanzen) {
        this("cp-sat", timeout, numThreads, maxInstanzen, true);
    }
    public SolverConfig(int timeout, int numThreads, int maxInstanzen, boolean auffuellen) {
        this("cp-sat", timeout, numThreads, maxInstanzen, auffuellen);
    }
}