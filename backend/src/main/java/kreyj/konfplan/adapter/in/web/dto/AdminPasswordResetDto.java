package kreyj.konfplan.adapter.in.web.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public class AdminPasswordResetDto {
    public String newPassword;


    public AdminPasswordResetDto() {
    }


    public AdminPasswordResetDto(String newPassword) {
        this.newPassword = newPassword;
    }
}
