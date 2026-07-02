package kreyj.konfplan.persistence;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.SequenceGenerator;
import lombok.Getter;
import lombok.Setter;

import java.util.Objects;

@MappedSuperclass
@Getter
@Setter
public class IdEntity extends PanacheEntityBase {
    @Id
    @SequenceGenerator(name = "idSequence", sequenceName = "id_sequence")
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "idSequence")
    private Long id;

    @Override
    public String toString() {
        return this.getClass().getSimpleName() + "<" + id + ">";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (null == o || getClass() != o.getClass()) {
            return false;
        }
        IdEntity that = (IdEntity) o;
        // For persisted entities, compare by ID.
        // For unpersisted entities (null == id), they are only equal if they are the same instance (handled by this == o).
        // This prevents two different new entities from being considered equal based on null ID.
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        // For persisted entities, hash by ID.
        // For unpersisted entities (null == id), use super.hashCode() (object identity).
        // This ensures consistency with equals: if equals returns true, hashCode must be the same.
        // Since equals returns false for different unpersisted entities, different hash codes are fine.
        return id != null ? Objects.hash(id) : super.hashCode();
    }
}
