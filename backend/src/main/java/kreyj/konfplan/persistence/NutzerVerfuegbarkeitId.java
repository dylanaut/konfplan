package kreyj.konfplan.persistence;

import lombok.Getter;

import java.io.Serializable;
import java.util.Objects;

@Getter
public class NutzerVerfuegbarkeitId implements Serializable {
    private Long nutzerId;
    private Long veranstaltungId;

    public NutzerVerfuegbarkeitId() {
    }

    public NutzerVerfuegbarkeitId(Long nutzerId, Long veranstaltungId) {
        Objects.requireNonNull(nutzerId);
        Objects.requireNonNull(veranstaltungId);

        this.nutzerId = nutzerId;
        this.veranstaltungId = veranstaltungId;
    }


    public static NutzerVerfuegbarkeitId nvId(Nutzer nutzer, Veranstaltung veranstaltung) {
        Objects.requireNonNull(nutzer);
        Objects.requireNonNull(veranstaltung);

        return nvIdL(nutzer.getId(), veranstaltung.getId());
    }

    public static NutzerVerfuegbarkeitId nvIdL(Long nutzerId, Long veranstaltungId) {
        Objects.requireNonNull(nutzerId);
        Objects.requireNonNull(veranstaltungId);

        return new NutzerVerfuegbarkeitId(nutzerId, veranstaltungId);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (null == o || getClass() != o.getClass()) {
            return false;
        }
        NutzerVerfuegbarkeitId that = (NutzerVerfuegbarkeitId) o;
        return Objects.equals(nutzerId, that.nutzerId) && Objects.equals(veranstaltungId, that.veranstaltungId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(nutzerId, veranstaltungId);
    }
}
