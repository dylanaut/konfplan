package kreyj.konfplan.domain.service;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotFoundException;
import kreyj.konfplan.adapter.in.web.DatabaseCleaner;
import kreyj.konfplan.domain.exception.BusinessException;
import kreyj.konfplan.persistence.Nachricht;
import kreyj.konfplan.persistence.NachrichtKategorie;
import kreyj.konfplan.persistence.Teilnehmer;
import kreyj.konfplan.persistence.Veranstaltung;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

@QuarkusTest
class NachrichtServiceTest extends DatabaseCleaner {

    @Inject
    NachrichtService nachrichtService;

    private Teilnehmer empfaenger;
    private Teilnehmer andererNutzer;


    @BeforeEach
    @Transactional
    void setUp() {
        empfaenger = new Teilnehmer();
        empfaenger.assignLoginName("nachrichten-empfaenger");
        empfaenger.setEmail("nachrichten-empfaenger@test.de");
        empfaenger.persist();

        andererNutzer = new Teilnehmer();
        andererNutzer.assignLoginName("anderer-nutzer");
        andererNutzer.setEmail("anderer-nutzer@test.de");
        andererNutzer.persist();
    }


    @Test
    @Transactional
    void sendeNachricht_persistiertKorrektEmpfaengerscoped() {
        nachrichtService.sendeNachricht(empfaenger, "Titel", "Inhalt", NachrichtKategorie.VORTRAG_ZURUECKGEZOGEN, 42L, null);

        List<Nachricht> gefunden = nachrichtService.getNachrichtenFuerNutzer("nachrichten-empfaenger");
        assertThat(gefunden).hasSize(1);
        assertThat(gefunden.getFirst().getTitel()).isEqualTo("Titel");
        assertThat(gefunden.getFirst().getInhalt()).isEqualTo("Inhalt");
        assertThat(gefunden.getFirst().getVeranstaltungId()).isEqualTo(42L);
        assertThat(gefunden.getFirst().getGelesenAm()).isNull();

        assertThat(nachrichtService.getNachrichtenFuerNutzer("anderer-nutzer")).isEmpty();
    }


    @Test
    @Transactional
    void getUngeleseneAnzahl_zaehltNurUngelesene() {
        nachrichtService.sendeNachricht(empfaenger, "Eins", "Inhalt", NachrichtKategorie.VORTRAG_ZURUECKGEZOGEN, null, null);
        Nachricht zwei = nachrichtService.sendeNachricht(empfaenger, "Zwei", "Inhalt", NachrichtKategorie.VORTRAG_ZURUECKGEZOGEN, null, null);

        assertThat(nachrichtService.getUngeleseneAnzahl("nachrichten-empfaenger")).isEqualTo(2);

        nachrichtService.markiereAlsGelesen("nachrichten-empfaenger", zwei.getId());

        assertThat(nachrichtService.getUngeleseneAnzahl("nachrichten-empfaenger")).isEqualTo(1);
    }


    @Test
    @Transactional
    void markiereAlsGelesen_setztZeitstempel() {
        Nachricht nachricht = nachrichtService.sendeNachricht(empfaenger, "Titel", "Inhalt", NachrichtKategorie.VORTRAG_ZURUECKGEZOGEN, null, null);

        nachrichtService.markiereAlsGelesen("nachrichten-empfaenger", nachricht.getId());

        Nachricht neuGeladen = Nachricht.findById(nachricht.getId());
        assertThat(neuGeladen.getGelesenAm()).isNotNull();
    }


    @Test
    @Transactional
    void markiereAlsGelesen_fremdeNachricht_wirdAbgelehnt() {
        Nachricht nachricht = nachrichtService.sendeNachricht(empfaenger, "Titel", "Inhalt", NachrichtKategorie.VORTRAG_ZURUECKGEZOGEN, null, null);

        assertThatExceptionOfType(ForbiddenException.class)
            .isThrownBy(() -> nachrichtService.markiereAlsGelesen("anderer-nutzer", nachricht.getId()));

        assertThat(Nachricht.<Nachricht>findById(nachricht.getId()).getGelesenAm()).isNull();
    }


    @Test
    @Transactional
    void markiereAlsGelesen_unbekannteNachricht_wirft404() {
        assertThatExceptionOfType(NotFoundException.class)
            .isThrownBy(() -> nachrichtService.markiereAlsGelesen("nachrichten-empfaenger", -1L));
    }


    @Test
    @Transactional
    void sendeAnAusgewaehlte_sendetAnMitgliederDerVeranstaltungMitAbsender() {
        Veranstaltung veranstaltung = new Veranstaltung();
        veranstaltung.setName("Test-Veranstaltung");
        veranstaltung.setBeginntAm(LocalDateTime.now());
        veranstaltung.persist();
        empfaenger.addVeranstaltung(veranstaltung);

        nachrichtService.sendeAnAusgewaehlte(veranstaltung, List.of(empfaenger.getId()), "Titel", "Inhalt", "otto.organisator");

        List<Nachricht> gefunden = nachrichtService.getNachrichtenFuerNutzer("nachrichten-empfaenger");
        assertThat(gefunden).hasSize(1);
        assertThat(gefunden.getFirst().getAbsender()).isEqualTo("otto.organisator");
        assertThat(gefunden.getFirst().getKategorie()).isEqualTo(NachrichtKategorie.ORGANISATOR_NACHRICHT);
    }


    @Test
    @Transactional
    void sendeAnAusgewaehlte_empfaengerNichtInVeranstaltung_wirdAbgelehnt() {
        Veranstaltung veranstaltung = new Veranstaltung();
        veranstaltung.setName("Test-Veranstaltung");
        veranstaltung.setBeginntAm(LocalDateTime.now());
        veranstaltung.persist();
        // andererNutzer ist bewusst NICHT Mitglied dieser Veranstaltung.

        // Die Validierung aller empfaengerIds laeuft VOR der Sende-Schleife komplett durch (siehe
        // NachrichtService#sendeAnAusgewaehlte) - die Exception allein belegt daher bereits, dass
        // keine Nachricht verschickt wurde. Ein zusaetzlicher Lese-Zugriff danach wuerde hier in
        // derselben (durch die erwartete Exception als rollback-only markierten) Transaktion
        // laufen und fehlschlagen.
        assertThatExceptionOfType(BusinessException.class)
            .isThrownBy(() -> nachrichtService.sendeAnAusgewaehlte(veranstaltung, List.of(andererNutzer.getId()), "Titel", "Inhalt", "otto.organisator"));
    }
}
