package kreyj.konfplan.presentation.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.util.Set;

@RegisterForReflection
@NoArgsConstructor
@AllArgsConstructor
public class TeilnehmerSimpleDto {
    public Long id;
    public String firstName;
    public String lastName;
    public Set<String> gruppen;
}
