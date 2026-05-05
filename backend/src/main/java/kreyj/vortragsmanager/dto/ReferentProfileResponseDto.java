package kreyj.vortragsmanager.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public class ReferentProfileResponseDto {
    public Long id;
    public String email;
    public String firstName;
    public String lastName;
    public String jobRole;
    public String organisation;
    public String slogan;
    public String biography;
    public String role; // To indicate the user's role
}
