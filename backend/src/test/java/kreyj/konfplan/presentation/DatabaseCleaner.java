package kreyj.konfplan.presentation;

import jakarta.transaction.Transactional;
import kreyj.konfplan.persistence.Gebaeude;
import kreyj.konfplan.persistence.Nutzer;
import kreyj.konfplan.persistence.NutzerVerfuegbarkeit;
import kreyj.konfplan.persistence.Planungsergebnis;
import kreyj.konfplan.persistence.Prioritaet;
import kreyj.konfplan.persistence.Raum;
import kreyj.konfplan.persistence.RaumVerfuegbarkeit;
import kreyj.konfplan.persistence.Slot;
import kreyj.konfplan.persistence.Veranstaltung;
import kreyj.konfplan.persistence.Vortrag;
import kreyj.konfplan.persistence.VortragVerfuegbarkeit;
import kreyj.konfplan.persistence.Zuweisung;
import org.junit.jupiter.api.BeforeEach;

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
}
