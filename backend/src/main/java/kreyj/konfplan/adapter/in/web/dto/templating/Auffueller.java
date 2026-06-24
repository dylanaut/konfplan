package kreyj.konfplan.adapter.in.web.dto.templating;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection

public record Auffueller(Long teilnehmerOid, Long wvOid, int instanzIdx) {
    public static Auffueller of(Long teilnehmerOid, Long wvOid, int instanzIdx) {
        return new Auffueller(teilnehmerOid, wvOid, instanzIdx);
    }
}
