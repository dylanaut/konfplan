package kreyj.konfplan.presentation.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@RegisterForReflection
@NoArgsConstructor
@AllArgsConstructor
public class RaumDto extends AbstractVersionedDto {
    public String name;
    public int kapazitaet;
    public String etage;
    public Long gebaeudeId;
    public String gebaeudeName;
}
