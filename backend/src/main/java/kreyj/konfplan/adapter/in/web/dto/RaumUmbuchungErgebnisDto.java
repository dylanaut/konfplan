package kreyj.konfplan.adapter.in.web.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@RegisterForReflection
@NoArgsConstructor
@AllArgsConstructor
public class RaumUmbuchungErgebnisDto {
    public String vortragTitel;
    public String alterRaumName;
    public String neuerRaumName;
}
