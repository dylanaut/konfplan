package kreyj.konfplan.adapter.in.web.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public class ZusatzrolleAnfrageDto {
    public String role;


    public ZusatzrolleAnfrageDto() {
    }


    public ZusatzrolleAnfrageDto(String role) {
        this.role = role;
    }
}
