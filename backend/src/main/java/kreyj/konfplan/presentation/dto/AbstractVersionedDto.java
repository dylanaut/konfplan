package kreyj.konfplan.presentation.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
abstract class AbstractVersionedDto extends AbstractIdDto {
    /**
     * Initialisierung mit 0L würde andeuten, dass zugehörige Entität schon angelegt wurde.
     */
    public Long version = null;
}
