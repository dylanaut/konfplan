package kreyj.konfplan.persistence;

import jakarta.persistence.*;

@MappedSuperclass
public class VersionedEntity extends IdEntity {
    @Version
    public Long version;
}
