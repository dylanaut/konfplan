package kreyj.konfplan.adapter.in.web.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * DTO für den Login-Vorgang.
 */
@RegisterForReflection
public class LoginRequest {
    public String email;
    public String password;

    // Standard-Konstruktor für Jackson (JSON-Deserialisierung)
    public LoginRequest() {
    }

    public LoginRequest(String email, String password) {
        this.email = email;
        this.password = password;
    }
}
