package kreyj.vortragsmanager.resource;

import jakarta.transaction.Transactional;
import kreyj.vortragsmanager.entity.*;
import org.junit.jupiter.api.BeforeEach;

public abstract class ResourceTestBase {

    @BeforeEach
    @Transactional
    public void cleanDatabase() {
        Zuweisung.deleteAll();
        Verfuegbarkeit.deleteAll();
        RaumVerfuegbarkeit.deleteAll();
        Prioritaet.deleteAll();
        Vortrag.deleteAll();
        Nutzer.deleteAll();
        Raum.deleteAll();
        Gebaeude.deleteAll();
        EventSlot.deleteAll();
        Veranstaltung.deleteAll();
    }
}
