package kreyj.konfplan.presentation.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public class PrioritaetRequest {
    public Long vortragId;
    public int prioWert;
}
