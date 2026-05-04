package kreyj.vortragsmanager.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import java.util.List;
import java.util.ArrayList;

@RegisterForReflection
public class UserDto extends AbstractVersionedDto {
    public String email;
    public String firstName;
    public String lastName;
    public String role;
    public boolean isActive;
    public List<Long> veranstaltungIds = new ArrayList<>();

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
