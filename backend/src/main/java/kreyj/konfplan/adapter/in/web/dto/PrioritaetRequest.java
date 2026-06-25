package kreyj.konfplan.adapter.in.web.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public class PrioritaetRequest {
    public Long vortragId;
    public int prio;
}
