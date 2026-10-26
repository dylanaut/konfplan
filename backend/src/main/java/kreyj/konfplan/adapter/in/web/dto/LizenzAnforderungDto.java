package kreyj.konfplan.adapter.in.web.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import kreyj.konfplan.domain.lizenz.LizenzAnforderung;

@RegisterForReflection
public class LizenzAnforderungDto {
    public String instanzId;
    public String publicKey;
    public String thumbprint;

    public static LizenzAnforderungDto from(LizenzAnforderung a) {
        LizenzAnforderungDto dto = new LizenzAnforderungDto();
        dto.instanzId = a.instanzId();
        dto.publicKey = a.publicKey();
        dto.thumbprint = a.thumbprint();
        return dto;
    }
}
