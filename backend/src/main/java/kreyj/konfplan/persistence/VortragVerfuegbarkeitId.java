package kreyj.konfplan.persistence;

import java.io.Serializable;
import java.util.Objects;

public class VortragVerfuegbarkeitId implements Serializable {
    private Long vortragId;
    private Long veranstaltungId;

    public VortragVerfuegbarkeitId() {
    }

    public VortragVerfuegbarkeitId(Long vortragId, Long veranstaltungId) {
        this.vortragId = vortragId;
        this.veranstaltungId = veranstaltungId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        VortragVerfuegbarkeitId that = (VortragVerfuegbarkeitId) o;
        return Objects.equals(vortragId, that.vortragId) && Objects.equals(veranstaltungId, that.veranstaltungId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(vortragId, veranstaltungId);
    }
}