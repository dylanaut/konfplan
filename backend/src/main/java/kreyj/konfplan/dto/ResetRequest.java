package kreyj.konfplan.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public class ResetRequest {
    public String token;
    public String newPassword;

    public ResetRequest() {
    }

    public ResetRequest(String token, String newPassword) {
        this.token = token;
        this.newPassword = newPassword;
    }
}