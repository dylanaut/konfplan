package kreyj.konfplan.adapter.in.web.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public class OrganisatorPasswordResetDto {
    public String newPassword;


    public OrganisatorPasswordResetDto() {
    }


    public OrganisatorPasswordResetDto(String newPassword) {
        this.newPassword = newPassword;
    }
}
