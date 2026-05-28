package kreyj.konfplan.persistence;

import java.io.Serializable;
import java.util.Objects;

public class RaumVerfuegbarkeitId implements Serializable {
    private Long raumId;
    private Long veranstaltungId;

    public RaumVerfuegbarkeitId() {
    }

    public RaumVerfuegbarkeitId(Long raumId, Long veranstaltungId) {
        this.raumId = raumId;
        this.veranstaltungId = veranstaltungId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RaumVerfuegbarkeitId that = (RaumVerfuegbarkeitId) o;
        return Objects.equals(raumId, that.raumId) && Objects.equals(veranstaltungId, that.veranstaltungId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(raumId, veranstaltungId);
    }
}