package kreyj.konfplan.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Objects;
import java.util.Set;

import static kreyj.konfplan.persistence.NutzerVerfuegbarkeitId.nvId;

@Entity
@NoArgsConstructor
@Getter
@Table(name = "NutzerVerfuegbarkeit")
@IdClass(NutzerVerfuegbarkeitId.class)
public class NutzerVerfuegbarkeit extends VeranstaltungsVerfuegbarkeit {
    @Id
    @Column(name = "nutzer_id")
    private Long nutzerId;

    public NutzerVerfuegbarkeit(Nutzer nutzer, Veranstaltung veranstaltung, Set<Long> verfuegbareSlotIds) {
        this(nutzer.getId(), veranstaltung.getId(), verfuegbareSlotIds);
    }

    public NutzerVerfuegbarkeit(Long nutzerId, Long veranstaltungId, Set<Long> verfuegbareSlotIds) {
        super(veranstaltungId, verfuegbareSlotIds);
        Objects.requireNonNull(nutzerId, "nutzerId darf nicht NULL sein");
        this.nutzerId = nutzerId;
    }


// -------------------------------------------------------------------
// Helper methods
// -------------------------------------------------------------------

    public static boolean alleNutzerVerfuegbar(List<Teilnehmer> teilnehmer, Long slotId, Long veranstaltungId) {
        if (teilnehmer.isEmpty()) {
            return true;
        }
        List<Long> teilnehmerIds = teilnehmer.stream().map(IdEntity::getId).toList();
        List<NutzerVerfuegbarkeit> verfuegbarkeiten = find("nutzerId in ?1 and veranstaltungId = ?2",
                teilnehmerIds, veranstaltungId).list();

        for (NutzerVerfuegbarkeit verfuegbarkeit : verfuegbarkeiten) {
            if (!verfuegbarkeit.getVerfuegbareSlotIds().contains(slotId)) {
                return false;
            }
        }
        return true;
    }

    public static boolean isNutzerVerfuegbar(Teilnehmer teilnehmer, Slot slot, Veranstaltung veranstaltung) {
        Objects.requireNonNull(teilnehmer, "teilnehmer darf nicht NULL sein");
        Objects.requireNonNull(slot, "slot darf nicht NULL sein");
        Objects.requireNonNull(veranstaltung, "veranstaltung darf nicht NULL sein");

        NutzerVerfuegbarkeit verfuegbarkeit = findById(nvId(teilnehmer, veranstaltung));

        return verfuegbarkeit.getVerfuegbareSlotIds().contains(slot.getId());
    }
}