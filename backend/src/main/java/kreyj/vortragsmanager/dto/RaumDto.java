package kreyj.vortragsmanager.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public class RaumDto extends AbstractVersionedDto {
    public String name;

    public int kapazitaet;

    public String etage;
}
