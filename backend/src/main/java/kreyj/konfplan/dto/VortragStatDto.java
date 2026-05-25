package kreyj.konfplan.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@RegisterForReflection
@NoArgsConstructor
@AllArgsConstructor
public class VortragStatDto {
    public String titel; // Umbenannt von title
    public long countPrio1;
    public long countPrio2;
    public long countPrio3;
    public long countTop3;
    public long totalVotes;
}
