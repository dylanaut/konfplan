package kreyj.vortragsmanager.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public class UserDto extends VersionedDto {
    public String email;
    public String firstName;
    public String lastName;
    public String role;
    public boolean isActive;
    public Long veranstaltungId;

    // Referent-spezifisch
    public String biography;
    public String jobRole;
    public String organisation;
    public String slogan;

    // Teilnehmer-spezifisch
    public String gruppe;

    public UserDto() {
    }
}
