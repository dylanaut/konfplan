package kreyj.konfplan.adapter.in.web.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@RegisterForReflection
@NoArgsConstructor
@AllArgsConstructor
public class RaumUmbuchungOptionDto {
    public Long raumId;
    public String raumName;
    public String raumGebaeudeKuerzel;
    public int kapazitaet;
}
