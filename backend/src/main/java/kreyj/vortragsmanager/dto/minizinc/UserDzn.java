package kreyj.vortragsmanager.dto.minizinc;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public record UserDzn(long id, String name,  String gruppe, int[] prios) {
}
