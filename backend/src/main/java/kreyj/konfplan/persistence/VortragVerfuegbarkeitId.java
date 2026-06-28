package kreyj.konfplan.persistence;

import java.io.Serializable;
import java.util.Objects;

public class VortragVerfuegbarkeitId implements Serializable {
    private Long vortragId;
    private Long veranstaltungId;

    public VortragVerfuegbarkeitId() {
    }


    public VortragVerfuegbarkeitId(Long vortragId, Long veranstaltungId) {
        Objects.requireNonNull(vortragId);
        Objects.requireNonNull(veranstaltungId);

        this.vortragId = vortragId;
        this.veranstaltungId = veranstaltungId;
    }


    public static VortragVerfuegbarkeitId vvId(Vortrag vortrag, Veranstaltung veranstaltung) {
        Objects.requireNonNull(vortrag);
        Objects.requireNonNull(veranstaltung);

        return vvIdL(vortrag.getId(), veranstaltung.getId());
    }

    public static VortragVerfuegbarkeitId vvIdL(Long vortragId, Long veranstaltungId) {
        Objects.requireNonNull(vortragId);
        Objects.requireNonNull(veranstaltungId);

        return new VortragVerfuegbarkeitId(vortragId, veranstaltungId);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (null == o || getClass() != o.getClass()) {
            return false;
        }
        VortragVerfuegbarkeitId that = (VortragVerfuegbarkeitId) o;
        return Objects.equals(vortragId, that.vortragId) && Objects.equals(veranstaltungId, that.veranstaltungId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(vortragId, veranstaltungId);
    }
}
