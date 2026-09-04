package kreyj.konfplan.domain.service;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import kreyj.konfplan.adapter.in.web.DatabaseCleaner;
import kreyj.konfplan.persistence.Nachricht;
import kreyj.konfplan.persistence.Organisator;
import kreyj.konfplan.persistence.Prioritaet;
import kreyj.konfplan.persistence.Referent;
import kreyj.konfplan.persistence.Teilnehmer;
import kreyj.konfplan.persistence.Veranstaltung;
import kreyj.konfplan.persistence.Wahlvortrag;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Deckt insbesondere die Benachrichtigungs-Logik ab, die beim Zurückziehen eines Wahlvortrags
 * mit bereits vergebenen Prioritäten ausgelöst wird (siehe NachrichtService).
 *
 * WICHTIG: @BeforeEach und @Test laufen in getrennten Transaktionen - die in setUp() erzeugten
 * Entities sind zu Beginn jeder Testmethode bereits DETACHED. Jede Testmethode muss sie daher
 * über findById() neu (und damit managed) laden, statt die Felder direkt weiterzuverwenden.
 */
@QuarkusTest
class ReferentServiceTest extends DatabaseCleaner {

    @Inject
    ReferentService referentService;

    private Long referentId;
    private Long veranstaltungId;
    private Long organisatorId;


    @BeforeEach
    @Transactional
    void setUp() {
        Referent referent = new Referent();
        referent.assignLoginName("referent.zurueckziehend");
        referent.setEmail("referent.zurueckziehend@test.de");
        referent.persist();
        referentId = referent.getId();

        Organisator organisator = new Organisator();
        organisator.assignLoginName("organisator.benachrichtigt");
        organisator.setEmail("organisator.benachrichtigt@test.de");
        organisator.persist();
        organisatorId = organisator.getId();

        Veranstaltung veranstaltung = new Veranstaltung();
        veranstaltung.setName("Test-Event");
        // Bereits in der Vergangenheit begonnen, damit ReferentService keinen echten Mailversand
        // ueber MailService#sendVortragsRegistrierung ausloest (belanglos fuer diesen Test).
        veranstaltung.setBeginntAm(LocalDateTime.now().minusDays(1));
        veranstaltung.persist();
        veranstaltungId = veranstaltung.getId();
        organisator.addVeranstaltung(veranstaltung);
    }


    private Wahlvortrag persistierterWahlvortrag() {
        Wahlvortrag wv = new Wahlvortrag();
        wv.setTitel("Testvortrag");
        wv.setReferent(Referent.findById(referentId));
        wv.setVeranstaltung(Veranstaltung.findById(veranstaltungId));
        wv.persistAndFlush();
        return wv;
    }


    private Teilnehmer persistierterTeilnehmer(String loginName) {
        Teilnehmer t = new Teilnehmer();
        t.assignLoginName(loginName);
        t.setEmail(loginName + "@test.de");
        t.persist();
        return t;
    }


    @Test
    @Transactional
    void deleteVortrag_mitPositivenPrioritaeten_benachrichtigtOrganisatorenUndBetroffeneTeilnehmer() {
        Wahlvortrag wv = persistierterWahlvortrag();
        Teilnehmer t1 = persistierterTeilnehmer("teilnehmer.eins");
        Teilnehmer t2 = persistierterTeilnehmer("teilnehmer.zwei");
        Teilnehmer ohnePraeferenz = persistierterTeilnehmer("teilnehmer.drei");
        new Prioritaet(t1, wv, 5).persistAndFlush();
        new Prioritaet(t2, wv, 8).persistAndFlush();
        new Prioritaet(ohnePraeferenz, wv, 0).persistAndFlush();

        boolean deleted = referentService.deleteVortrag("referent.zurueckziehend", wv.getId());

        assertThat(deleted).isTrue();

        Organisator organisator = Organisator.findById(organisatorId);
        List<Nachricht> organisatorNachrichten = Nachricht.findFuerEmpfaenger(organisator);
        assertThat(organisatorNachrichten).hasSize(1);
        assertThat(organisatorNachrichten.getFirst().getInhalt()).contains("2 Teilnehmer");

        assertThat(Nachricht.findFuerEmpfaenger(t1)).hasSize(1);
        assertThat(Nachricht.findFuerEmpfaenger(t2)).hasSize(1);
        // prioWert = 0 ("keine Präferenz") zählt nicht als Anmeldung und wird nicht benachrichtigt.
        assertThat(Nachricht.findFuerEmpfaenger(ohnePraeferenz)).isEmpty();
    }


    @Test
    @Transactional
    void deleteVortrag_ohneJedePrioritaet_benachrichtigtNurOrganisatorenOhneBetroffeneZahl() {
        Wahlvortrag wv = persistierterWahlvortrag();

        referentService.deleteVortrag("referent.zurueckziehend", wv.getId());

        Organisator organisator = Organisator.findById(organisatorId);
        List<Nachricht> organisatorNachrichten = Nachricht.findFuerEmpfaenger(organisator);
        assertThat(organisatorNachrichten).hasSize(1);
        assertThat(organisatorNachrichten.getFirst().getInhalt()).contains("0 Teilnehmer");
    }


    @Test
    @Transactional
    void meldeVortragFuerVeranstaltungAb_mitPositivenPrioritaeten_benachrichtigtBetroffeneTeilnehmer() {
        Wahlvortrag wv = persistierterWahlvortrag();
        Teilnehmer t1 = persistierterTeilnehmer("teilnehmer.abmeldung");
        new Prioritaet(t1, wv, 3).persistAndFlush();

        referentService.meldeVortragFuerVeranstaltungAb("referent.zurueckziehend", wv.getId(), veranstaltungId);

        assertThat(Nachricht.findFuerEmpfaenger(t1)).hasSize(1);
        assertThat(Nachricht.findFuerEmpfaenger(Organisator.findById(organisatorId))).hasSize(1);
    }
}
