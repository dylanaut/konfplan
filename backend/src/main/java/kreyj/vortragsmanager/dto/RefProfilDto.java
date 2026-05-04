package kreyj.vortragsmanager.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public class RefProfilDto {
    public String email;
    public String firstName;
    public String lastName;
    public String jobRole;
    public String organisation;
    public String slogan;
    public String biography;
}
