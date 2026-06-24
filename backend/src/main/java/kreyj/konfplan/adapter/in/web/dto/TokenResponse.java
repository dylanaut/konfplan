package kreyj.konfplan.adapter.in.web.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public class TokenResponse {
    public String token;
    public String role;

    public TokenResponse() {
    }

    public TokenResponse(String token, String role) {
        this.token = token;
        this.role = role;
    }
}
