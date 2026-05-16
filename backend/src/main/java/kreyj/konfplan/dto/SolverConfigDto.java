package kreyj.konfplan.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import kreyj.konfplan.service.OptimierungService;

@RegisterForReflection
public class SolverConfigDto {
//    public OptimierungService.SOLVER_TYP solver;
    public String solver;
    public int timeout = 120; // in Sekunden
    public int numThreads = 4; // Anzahl paralleler Threads
    public int maxInstanzen = 2; // Maximale Anzahl der Instanzen pro Wahlvortrag

    public SolverConfigDto() {
    }

    public SolverConfigDto(String solver, int timeout, int numThreads, int maxInstanzen) {
        this.solver = solver;
        this.timeout = timeout;
        this.numThreads = numThreads;
        this.maxInstanzen = maxInstanzen;
    }
}