package kreyj.konfplan.adapter.in.web.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;

import java.util.List;

@RegisterForReflection
public class OnboardingStatusDto {
    public String loginName;
    public String role;
    public String email;
    public List<String> gruppen;
    public boolean hatEchtesPasswort;

    public OnboardingStatusDto() {
    }

    public OnboardingStatusDto(String loginName, String role, String email, List<String> gruppen, boolean hatEchtesPasswort) {
        this.loginName = loginName;
        this.role = role;
        this.email = email;
        this.gruppen = gruppen;
        this.hatEchtesPasswort = hatEchtesPasswort;
    }
}
