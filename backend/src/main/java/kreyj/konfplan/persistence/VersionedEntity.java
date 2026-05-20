package kreyj.konfplan.persistence;

import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

@MappedSuperclass
@Getter
@Setter
public class VersionedEntity extends IdEntity {
    @Version
    private Long version;
}