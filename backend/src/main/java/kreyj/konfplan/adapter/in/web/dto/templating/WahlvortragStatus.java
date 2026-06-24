package kreyj.konfplan.adapter.in.web.dto.templating;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public record WahlvortragStatus(
        String status, // "+", "-", "f", "0"
        int prio,
        Integer instanz
) {
}
