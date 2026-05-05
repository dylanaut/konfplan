package kreyj.vortragsmanager.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public class ReferentProfileDto {
    public Long id;
    public String email;
    public String firstName;
    public String lastName;
    public String organisation;
    public String jobRole;
    public String slogan;
    public String biography;
}
