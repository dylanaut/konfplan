package kreyj.konfplan.resource;

import jakarta.transaction.Transactional;
import kreyj.konfplan.persistence.*;
import org.junit.jupiter.api.BeforeEach;

public abstract class ResourceTestBase {

    @BeforeEach
    @Transactional
    public void cleanDatabase() {
        Zuweisung.deleteAll();
        NutzerVerfuegbarkeit.deleteAll();
        RaumVerfuegbarkeit.deleteAll();
        Prioritaet.deleteAll();
        Vortrag.deleteAll();
        Nutzer.deleteAll();
        Raum.deleteAll();
        Gebaeude.deleteAll();
        Slot.deleteAll();
        Veranstaltung.deleteAll();
    }
}
