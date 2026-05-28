package kreyj.konfplan.persistence;

import java.io.Serializable;
import java.util.Objects;

public class NutzerVerfuegbarkeitId implements Serializable {
    private Long nutzerId;
    private Long veranstaltungId;

    public NutzerVerfuegbarkeitId() {
    }

    public NutzerVerfuegbarkeitId(Long nutzerId, Long veranstaltungId) {
        this.nutzerId = nutzerId;
        this.veranstaltungId = veranstaltungId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        NutzerVerfuegbarkeitId that = (NutzerVerfuegbarkeitId) o;
        return Objects.equals(nutzerId, that.nutzerId) && Objects.equals(veranstaltungId, that.veranstaltungId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(nutzerId, veranstaltungId);
    }
}