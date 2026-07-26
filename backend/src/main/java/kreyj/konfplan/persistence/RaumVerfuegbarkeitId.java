package kreyj.konfplan.persistence;

import java.io.Serializable;
import java.util.Objects;

public class RaumVerfuegbarkeitId implements Serializable {
    private Long raumId;
    private Long veranstaltungId;

    public RaumVerfuegbarkeitId() {
    }


    public RaumVerfuegbarkeitId(Long raumId, Long veranstaltungId) {
        Objects.requireNonNull(raumId);
        Objects.requireNonNull(veranstaltungId);

        this.raumId = raumId;
        this.veranstaltungId = veranstaltungId;
    }


    public static RaumVerfuegbarkeitId rvIdL(Long raumId, Long veranstaltungId) {
        Objects.requireNonNull(raumId);
        Objects.requireNonNull(veranstaltungId);

        return new RaumVerfuegbarkeitId(raumId, veranstaltungId);
    }


    public static RaumVerfuegbarkeitId rvId(Raum raum, Veranstaltung veranstaltung) {
        Objects.requireNonNull(raum);
        Objects.requireNonNull(veranstaltung);

        return rvIdL(raum.getId(), veranstaltung.getId());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (null == o || getClass() != o.getClass()) {
            return false;
        }
        RaumVerfuegbarkeitId that = (RaumVerfuegbarkeitId) o;
        return Objects.equals(raumId, that.raumId) && Objects.equals(veranstaltungId, that.veranstaltungId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(raumId, veranstaltungId);
    }
}
