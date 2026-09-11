package kreyj.konfplan.adapter.in.web.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@RegisterForReflection
@NoArgsConstructor
@AllArgsConstructor
public class TeilnehmerUmbuchenAnfrageDto {
    public Long altWahlvortragId;
    public int altInstanzIndex;
    public Long neuWahlvortragId;
    public int neuInstanzIndex;
}
