package kreyj.konfplan.domain.service;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.narayana.jta.runtime.TransactionConfiguration;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import kreyj.konfplan.adapter.in.web.DatabaseCleaner;
import kreyj.konfplan.adapter.in.web.dto.VeranstaltungDto;
import kreyj.konfplan.adapter.in.web.dto.VeranstaltungImportDatasetDto;
import kreyj.konfplan.domain.exception.CsvImportException;
import kreyj.konfplan.persistence.Veranstaltung;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@QuarkusTest
class VeranstaltungImportServiceTest extends DatabaseCleaner {

    @Inject
    VeranstaltungImportService veranstaltungImportService;


    @Test
    void listDatasets_flagsSelectableBasedOnMandatoryFiles() {
        List<VeranstaltungImportDatasetDto> datasets = veranstaltungImportService.listDatasets();
        Map<String, VeranstaltungImportDatasetDto> byName = datasets.stream()
            .collect(java.util.stream.Collectors.toMap(d -> d.name, d -> d));

        assertThat(byName).containsKeys("valid-event", "missing-slots", "broken-vortraege");

        VeranstaltungImportDatasetDto validEvent = byName.get("valid-event");
        assertThat(validEvent.auswaehlbar).isTrue();
        assertThat(validEvent.fehlendeDateien).isEmpty();

        VeranstaltungImportDatasetDto missingSlots = byName.get("missing-slots");
        assertThat(missingSlots.auswaehlbar).isFalse();
        assertThat(missingSlots.fehlendeDateien).contains("slots.csv");

        // broken-vortraege enthält alle Pflicht-Dateien (auch wahl_/pflicht_vortraege.csv sind
        // vorhanden, nur inhaltlich leer) - die Dateipräsenz-Prüfung allein lässt es daher zu.
        VeranstaltungImportDatasetDto brokenVortraege = byName.get("broken-vortraege");
        assertThat(brokenVortraege.auswaehlbar).isTrue();
    }


    @Test
    void importDataset_createsVeranstaltungAndAttachesAllData() throws Exception {
        // Bewusst OHNE @Transactional auf der Testmethode: importDataset() setzt per
        // @TransactionConfiguration einen erhöhten Timeout, was Narayana nur am Einstiegspunkt
        // einer Transaktion erlaubt (siehe Kommentar dort) - mit einer bereits laufenden
        // Transaktion (wie es @Transactional auf dieser Methode erzeugen würde) entspräche das
        // nicht dem echten Aufrufpfad aus VeranstaltungImportResource (dort ebenfalls ohne
        // vorher offene Transaktion) und würfe "can only be done at the entry level".
        VeranstaltungDto result = veranstaltungImportService.importDataset("valid-event");

        assertThat(result).isNotNull();
        assertThat(result.id).isNotNull();

        QuarkusTransaction.requiringNew().run(() -> {
            Veranstaltung veranstaltung = Veranstaltung.findById(result.id);
            assertThat(veranstaltung).isNotNull();
            assertThat(veranstaltung.getName()).isEqualTo("Bundle Import Test Event");
            assertThat(veranstaltung.getSlots()).hasSize(3);
            assertThat(veranstaltung.referenten()).hasSize(3);
            assertThat(veranstaltung.teilnehmer()).hasSize(3);
            assertThat(veranstaltung.getWahlvortraege()).hasSize(2);
            assertThat(veranstaltung.getPflichtvortraege()).hasSize(1);
        });
    }


    @Test
    void importDataset_rollsBackEverythingOnFailure() {
        long countBefore = Veranstaltung.count();

        assertThatThrownBy(() -> veranstaltungImportService.importDataset("broken-vortraege"))
            .isInstanceOf(CsvImportException.class);

        assertThat(Veranstaltung.count())
            .describedAs("Ein fehlgeschlagener Verzeichnis-Import darf keine Veranstaltung hinterlassen")
            .isEqualTo(countBefore);
    }


    @Test
    void importDataset_rejectsPathTraversal() {
        assertThatThrownBy(() -> veranstaltungImportService.importDataset("../../etc"))
            .isInstanceOf(IllegalArgumentException.class);
    }


    // Regression: der gesamte Bündelimport läuft in einer Transaktion (Alles-oder-nichts); bei
    // vielen Teilnehmern sprengt allein das BCrypt-Hashing der Passwörter den JTA-Default-
    // Timeout von 60s, was in %prod (kein 3-Minuten-Override wie in %dev) real zu
    // "ARJUNA016102: The transaction is not active!" führte (gegen echtes Postgres verifiziert,
    // 632 Teilnehmer). Ein echter End-to-End-Test mit so vielen Zeilen wäre für die Testsuite zu
    // langsam - dieser Test prüft daher direkt, dass der großzügigere Timeout konfiguriert ist,
    // statt auf den globalen Default zu vertrauen.
    @Test
    void importDataset_hasExtendedTransactionTimeoutForLargeDatasets() throws NoSuchMethodException {
        Method method = VeranstaltungImportService.class.getMethod("importDataset", String.class);
        TransactionConfiguration config = method.getAnnotation(TransactionConfiguration.class);

        assertThat(config)
            .describedAs("importDataset() muss einen expliziten, grosszuegigen Transaktions-Timeout haben, "
                + "da %prod (anders als %dev) keinen erhoehten globalen Default-Timeout hat")
            .isNotNull();
        assertThat(config.timeout()).isGreaterThanOrEqualTo(180);
    }
}
