package kreyj.konfplan.persistence;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;

import java.util.Objects;

@MappedSuperclass
public class IdEntity extends PanacheEntityBase {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(columnDefinition = "INTEGER") // Explizite Definition für den Validator
    public Long id;

    @Override
    public String toString() {
        return this.getClass().getSimpleName() + "<" + id + ">";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        VersionedEntity that = (VersionedEntity) o;
        // For persisted entities, compare by ID.
        // For unpersisted entities (id == null), they are only equal if they are the same instance (handled by this == o).
        // This prevents two different new entities from being considered equal based on null ID.
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        // For persisted entities, hash by ID.
        // For unpersisted entities (id == null), use super.hashCode() (object identity).
        // This ensures consistency with equals: if equals returns true, hashCode must be the same.
        // Since equals returns false for different unpersisted entities, different hash codes are fine.
        return id != null ? Objects.hash(id) : super.hashCode();
    }
}
