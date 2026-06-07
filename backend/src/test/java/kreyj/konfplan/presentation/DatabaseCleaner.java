package kreyj.konfplan.presentation;

import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.transaction.Transactional;
import kreyj.konfplan.persistence.Gebaeude;
import kreyj.konfplan.persistence.Nutzer;
import kreyj.konfplan.persistence.NutzerVerfuegbarkeit;
import kreyj.konfplan.persistence.Planungsergebnis;
import kreyj.konfplan.persistence.Prioritaet;
import kreyj.konfplan.persistence.Raum;
import kreyj.konfplan.persistence.RaumVerfuegbarkeit;
import kreyj.konfplan.persistence.Referent;
import kreyj.konfplan.persistence.Slot;
import kreyj.konfplan.persistence.Teilnehmer;
import kreyj.konfplan.persistence.Veranstaltung;
import kreyj.konfplan.persistence.Vortrag;
import kreyj.konfplan.persistence.VortragVerfuegbarkeit;
import kreyj.konfplan.persistence.Zuweisung;
import kreyj.konfplan.presentation.dto.VortragDto;
import org.junit.jupiter.api.BeforeEach;

import static kreyj.konfplan.persistence.NutzerVerfuegbarkeitId.nvIdL;
import static kreyj.konfplan.persistence.RaumVerfuegbarkeitId.rvIdL;

public abstract class DatabaseCleaner {
    @BeforeEach
    @Transactional
    public void cleanDatabase() {
        Zuweisung.deleteAll();
        NutzerVerfuegbarkeit.deleteAll();
        RaumVerfuegbarkeit.deleteAll();
        VortragVerfuegbarkeit.deleteAll();
        Prioritaet.deleteAll();
        Vortrag.deleteAll();
        Nutzer.deleteAll();
        Slot.deleteAll();
        Planungsergebnis.deleteAll();
        Veranstaltung.deleteAll();
        Raum.deleteAll();
        Gebaeude.deleteAll();
    }


    // -------------------------------------------------------------------
    // Helper methods
    // -------------------------------------------------------------------

    public static VortragDto pvDto(String titel, Referent referent, String gruppe, Raum raum, Slot slot,
                                   Veranstaltung veranstaltung) {
        VortragDto dto = new VortragDto();
        dto.istPflicht = true;
        dto.titel = titel;
        dto.referentId = referent.getId();
        dto.pflichtGruppe = gruppe;
        dto.pflichtRaumId = raum.getId();
        dto.pflichtSlotId = slot.getId();
        dto.veranstaltungId = veranstaltung.getId();

        return dto;
    }

    public static boolean isTeilnehmerVerfuegbar(Teilnehmer tn, Slot slot, Veranstaltung veranstaltung) {
        return isTeilnehmerVerfuegbar(tn.getId(), slot.getId(), veranstaltung.getId());
    }

    public static boolean isTeilnehmerVerfuegbar(Long tnId, Long slotId, Long veranstaltungId) {
        final boolean[] resultArr = {false};

        QuarkusTransaction.requiringNew().run(() -> {
            NutzerVerfuegbarkeit nv = NutzerVerfuegbarkeit.findById(nvIdL(tnId, veranstaltungId));

            resultArr[0] = nv.isVerfuegbar(slotId);
        });

        return resultArr[0];
    }

    public static boolean isRaumVerfuegbar(Raum raum, Slot slot, Veranstaltung veranstaltung) {
        return isRaumVerfuegbar(raum.getId(), slot.getId(), veranstaltung.getId());
    }

    public static boolean isRaumVerfuegbar(Long raumId, Long slotId, Long veranstaltungId) {
        final boolean[] resultArr = {false};

        QuarkusTransaction.requiringNew().run(() -> {
            RaumVerfuegbarkeit rv = RaumVerfuegbarkeit.findById(rvIdL(raumId, veranstaltungId));

            resultArr[0] = rv.isVerfuegbar(slotId);
        });

        return resultArr[0];
    }
}
