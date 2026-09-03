package kreyj.konfplan.adapter.in.web.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public class OnboardingStatusDto {
    public String loginName;
    public String role;
    public String email;
    public boolean hatEchtesPasswort;

    public OnboardingStatusDto() {
    }

    public OnboardingStatusDto(String loginName, String role, String email, boolean hatEchtesPasswort) {
        this.loginName = loginName;
        this.role = role;
        this.email = email;
        this.hatEchtesPasswort = hatEchtesPasswort;
    }
}
