package kreyj.vortragsmanager.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.util.Objects; // Import Objects for Objects.equals

@MappedSuperclass
public class VersionedEntity extends IdEntity {
    @Version
    public Long version;
}
