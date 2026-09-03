package kreyj.konfplan.adapter.in.web.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public class RoleChangeDto {
    public String role;


    public RoleChangeDto() {
    }


    public RoleChangeDto(String role) {
        this.role = role;
    }
}
