package kreyj.vortragsmanager.dto.minizinc;

import io.quarkus.runtime.annotations.RegisterForReflection;

import java.time.LocalDateTime;

@RegisterForReflection
public record SlotDzn(Long id, String name, LocalDateTime startTime, LocalDateTime endTime) {
}
