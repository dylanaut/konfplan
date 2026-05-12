package kreyj.konfplan.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public class SolverConfigDto {
    public String solver ;
    public int timeout = 120; // in Sekunden
    public int numThreads = 4; // Anzahl paralleler Threads
}
