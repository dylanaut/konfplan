package kreyj.konfplan.domain.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import kreyj.konfplan.adapter.in.web.DatabaseCleaner;
import kreyj.konfplan.adapter.in.web.dto.templating.BelegungDetail;
import kreyj.konfplan.adapter.in.web.dto.templating.Stundenplan;
import kreyj.konfplan.persistence.Gebaeude;
import kreyj.konfplan.persistence.Gebaeudetyp;
import kreyj.konfplan.persistence.Planungsergebnis;
import kreyj.konfplan.persistence.Raum;
import kreyj.konfplan.persistence.Referent;
import kreyj.konfplan.persistence.Slot;
import kreyj.konfplan.persistence.Teilnehmer;
import kreyj.konfplan.persistence.Veranstaltung;
import kreyj.konfplan.persistence.Wahlvortrag;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regressionstest für einen Produktions-NPE: {@code result.teilnehmer_oids}/{@code besucht} eines
 * bereits gespeicherten Planungsergebnisses sind eine EINGEFRORENE Momentaufnahme zum
 * Erstellungszeitpunkt. Wird ein Teilnehmer danach aus der Veranstaltung entfernt, enthält
 * {@code veranstaltung.teilnehmer()} (die Basis für DashboardData#teilnehmer) ihn nicht mehr -
 * jeder direkte {@code dd.teilnehmer.get(oid)}-Zugriff ohne Null-Check crasht dann den Report.
 */
@QuarkusTest
class DashboardServiceTest extends DatabaseCleaner {

    @Inject
    DashboardService dashboardService;

    @Inject
    ObjectMapper objectMapper;

    private Long schuleId;
    private Long raumGrossId;


    @BeforeEach
    @Transactional
    void setup() {
        Gebaeude schule = new Gebaeude("Test Schule", "Testort", "Teststrasse", "4711", Gebaeudetyp.SCHULE);
        schule.persist();

        Raum raumGross = new Raum("Raum Groß", 10);
        raumGross.persist();
        schule.addRaum(raumGross);

        schuleId = schule.getId();
        raumGrossId = raumGross.getId();
    }


    private Veranstaltung neueVeranstaltung(String name) {
        Veranstaltung veranstaltung = new Veranstaltung();
        veranstaltung.setName(name);
        veranstaltung.setBeginntAm(LocalDateTime.now());
        veranstaltung.addGebaeude(Gebaeude.<Gebaeude>findById(schuleId));
        veranstaltung.persist();

        Referent referent = new Referent();
        referent.assignLoginName("referent-" + name + "@test.com");
        referent.setEmail("referent-" + name + "@test.com");
        referent.setFirstName("Max");
        referent.setLastName("Mustermann");
        referent.persist();
        referent.addVeranstaltung(veranstaltung);

        return veranstaltung;
    }


    private Slot neuerSlot(Veranstaltung veranstaltung, int stundenOffset) {
        Slot slot = new Slot("Slot", LocalDateTime.now().plusHours(stundenOffset),
            LocalDateTime.now().plusHours(stundenOffset + 1), veranstaltung);
        slot.persist();
        veranstaltung.addSlot(slot);
        return slot;
    }


    private Wahlvortrag neuerWahlvortrag(Veranstaltung veranstaltung, String titel) {
        Referent referent = Referent.find("email", "referent-" + veranstaltung.getName() + "@test.com").firstResult();
        Wahlvortrag wv = new Wahlvortrag();
        wv.setTitel(titel);
        wv.setReferent(referent);
        wv.setVeranstaltung(veranstaltung);
        wv.persist();
        return wv;
    }


    private Teilnehmer neuerTeilnehmer(Veranstaltung veranstaltung, String email) {
        Teilnehmer tn = new Teilnehmer();
        tn.assignLoginName(email);
        tn.setEmail(email);
        tn.setFirstName(email);
        tn.setLastName("Test");
        tn.addGruppe("A");
        tn.persist();
        tn.addVeranstaltung(veranstaltung);
        return tn;
    }


    private Planungsergebnis.MinizincResult ergebnis(long[] tnOids, long[] wvOids, long[] slotOids, long[] raumOids,
                                                      int[][] instanzSlot, int[][] instanzRaum, boolean[][][] besucht) {
        Planungsergebnis.MinizincResult result = new Planungsergebnis.MinizincResult();
        result.teilnehmer_oids = tnOids;
        result.wahlvortrag_oids = wvOids;
        result.slot_oids = slotOids;
        result.raum_oids = raumOids;
        result.instanz_slot = instanzSlot;
        result.instanz_raum = instanzRaum;
        result.besucht = besucht;
        return result;
    }


    private Planungsergebnis persistiereVeroeffentlichtesErgebnis(Veranstaltung veranstaltung, Planungsergebnis.MinizincResult result) {
        Planungsergebnis ergebnis = new Planungsergebnis();
        ergebnis.setVeranstaltung(veranstaltung);
        ergebnis.setJsonErgebnis(result.toJson(objectMapper));
        ergebnis.setErsteller("test-organisator");
        ergebnis.setErstelltAm(LocalDateTime.now());
        ergebnis.setPubliziert(true);
        ergebnis.persist();
        return ergebnis;
    }


    @Test
    @Transactional
    void stundenplan_ueberstehtNachtraeglichAusDerVeranstaltungEntferntenTeilnehmer() {
        Veranstaltung veranstaltung = neueVeranstaltung("Dashboard-Test-1");
        Slot slot1 = neuerSlot(veranstaltung, 1);
        Wahlvortrag wv1 = neuerWahlvortrag(veranstaltung, "Wahlvortrag 1");
        Teilnehmer bleibt = neuerTeilnehmer(veranstaltung, "bleibt@test.com");
        Teilnehmer entfernt = neuerTeilnehmer(veranstaltung, "entfernt@test.com");

        persistiereVeroeffentlichtesErgebnis(veranstaltung, ergebnis(
            new long[]{bleibt.getId(), entfernt.getId()},
            new long[]{wv1.getId()},
            new long[]{slot1.getId()},
            new long[]{raumGrossId},
            new int[][]{{1}},
            new int[][]{{1}},
            new boolean[][][]{{{true}}, {{true}}}));

        // Simuliert: Organisator entfernt den Teilnehmer nachträglich aus der Veranstaltung,
        // ohne den Plan neu zu berechnen - genau das löste den Produktions-NPE aus.
        entfernt.removeVeranstaltung(veranstaltung);

        // Ruft direkt auf (statt assertThatCode) - ein uncaught Exception hier lässt den Test
        // ohnehin fehlschlagen, und wir brauchen den Rückgabewert für die weiteren Assertions.
        Stundenplan plan = dashboardService.getStundenplan(veranstaltung);

        BelegungDetail belegung = plan.getBelegungDetails().values().iterator().next();
        assertThat(belegung.teilnehmer).containsExactly(bleibt.getFullName() + " (A)");
        assertThat(belegung.anzahl).isEqualTo(1);
    }
}
