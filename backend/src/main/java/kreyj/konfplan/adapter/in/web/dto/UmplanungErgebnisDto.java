package kreyj.konfplan.adapter.in.web.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.util.List;

@RegisterForReflection
@NoArgsConstructor
@AllArgsConstructor
public class UmplanungErgebnisDto {
    public List<UmverteilterTeilnehmerDto> umverteilt;
    public List<String> nichtPlatziert;


    @RegisterForReflection
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UmverteilterTeilnehmerDto {
        public String teilnehmerName;
        public String neuerVortragTitel;
    }
}
