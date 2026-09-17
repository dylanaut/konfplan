package kreyj.konfplan.adapter.in.web.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public class VortragTitelDto {
    public Long id;
    public String titel;

    public VortragTitelDto(Long id, String titel) {
        this.id = id;
        this.titel = titel;
    }
}
