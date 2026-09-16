package kreyj.konfplan.adapter.in.web.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;

import java.util.List;
import java.util.Map;

@RegisterForReflection
public class OnboardingStatusDto {
    public String loginName;
    public String role;
    public String email;
    /** Gruppenkategorie-Werte nach Kategorie-Name gruppiert (siehe #690) - für eine eigene,
     * sortier- und filterbare Spalte je Gruppenkategorie statt einer flachen Liste. */
    public Map<String, List<String>> gruppenwerteByKategorie;
    public boolean hatEchtesPasswort;

    public OnboardingStatusDto() {
    }

    public OnboardingStatusDto(String loginName, String role, String email,
                                Map<String, List<String>> gruppenwerteByKategorie, boolean hatEchtesPasswort) {
        this.loginName = loginName;
        this.role = role;
        this.email = email;
        this.gruppenwerteByKategorie = gruppenwerteByKategorie;
        this.hatEchtesPasswort = hatEchtesPasswort;
    }
}
