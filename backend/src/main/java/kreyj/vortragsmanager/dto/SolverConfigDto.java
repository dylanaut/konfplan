package kreyj.vortragsmanager.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public class SolverConfigDto {
    public String solver;
    public int timeout; // in Sekunden
}
