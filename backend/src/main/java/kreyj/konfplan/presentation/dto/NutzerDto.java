package kreyj.konfplan.presentation.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;

import java.util.ArrayList;
import java.util.List;

@RegisterForReflection
public class NutzerDto extends AbstractVersionedDto {
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
    public List<VortragPrioDto> prioritaeten = new ArrayList<>();
}
