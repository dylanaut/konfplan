package kreyj.vortragsmanager.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public class EmailChangeRequestDto {
    public String newEmail;
    public String currentPassword;
}
