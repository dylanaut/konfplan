package kreyj.vortragsmanager.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
abstract class AbstractVersionedDto extends AbstractIdDto {
    public Long version;
}
