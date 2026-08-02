package kreyj.konfplan.domain.service;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import kreyj.konfplan.adapter.in.web.DatabaseCleaner;
import kreyj.konfplan.adapter.in.web.dto.VeranstaltungDto;
import kreyj.konfplan.adapter.in.web.dto.VeranstaltungImportDatasetDto;
import kreyj.konfplan.domain.exception.CsvImportException;
import kreyj.konfplan.persistence.Veranstaltung;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

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
    @Transactional
    void importDataset_createsVeranstaltungAndAttachesAllData() throws Exception {
        VeranstaltungDto result = veranstaltungImportService.importDataset("valid-event");

        assertThat(result).isNotNull();
        assertThat(result.id).isNotNull();

        Veranstaltung veranstaltung = Veranstaltung.findById(result.id);
        assertThat(veranstaltung).isNotNull();
        assertThat(veranstaltung.getName()).isEqualTo("Bundle Import Test Event");
        assertThat(veranstaltung.getSlots()).hasSize(3);
        assertThat(veranstaltung.referenten()).hasSize(3);
        assertThat(veranstaltung.teilnehmer()).hasSize(3);
        assertThat(veranstaltung.getWahlvortraege()).hasSize(2);
        assertThat(veranstaltung.getPflichtvortraege()).hasSize(1);
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


    @Test
    @Transactional
    void importFromZip_createsVeranstaltungAndAttachesAllData(@TempDir Path tempDir) throws Exception {
        Path zip = tempDir.resolve("valid-event.zip");
        zipDirectory(validEventDir(), zip, null);

        VeranstaltungDto result = veranstaltungImportService.importFromZip(zip);

        assertThat(result).isNotNull();
        assertThat(result.id).isNotNull();

        Veranstaltung veranstaltung = Veranstaltung.findById(result.id);
        assertThat(veranstaltung).isNotNull();
        assertThat(veranstaltung.getName()).isEqualTo("Bundle Import Test Event");
        assertThat(veranstaltung.getSlots()).hasSize(3);
        assertThat(veranstaltung.referenten()).hasSize(3);
        assertThat(veranstaltung.teilnehmer()).hasSize(3);
        assertThat(veranstaltung.getWahlvortraege()).hasSize(2);
        assertThat(veranstaltung.getPflichtvortraege()).hasSize(1);
    }


    @Test
    void importFromZip_unwrapsSingleTopLevelFolder(@TempDir Path tempDir) throws Exception {
        Path zip = tempDir.resolve("wrapped-valid-event.zip");
        zipDirectory(validEventDir(), zip, "valid-event");

        VeranstaltungDto result = veranstaltungImportService.importFromZip(zip);

        assertThat(result).isNotNull();
        assertThat(result.id).isNotNull();
    }


    @Test
    void importFromZip_rejectsZipMissingMandatoryFiles(@TempDir Path tempDir) throws Exception {
        Path zip = tempDir.resolve("incomplete-event.zip");
        zipDirectory(validEventDir(), zip, null, "slots.csv");

        assertThatThrownBy(() -> veranstaltungImportService.importFromZip(zip))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("slots.csv");
    }


    @Test
    void importFromZip_rejectsZipSlipEntry(@TempDir Path tempDir) throws Exception {
        Path zip = tempDir.resolve("malicious.zip");
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zip))) {
            zos.putNextEntry(new ZipEntry("../evil.csv"));
            zos.write("evil".getBytes());
            zos.closeEntry();
        }

        assertThatThrownBy(() -> veranstaltungImportService.importFromZip(zip))
            .isInstanceOf(IllegalArgumentException.class);
    }


    private Path validEventDir() {
        return Path.of("src/test/resources/veranstaltung_import_bundles/valid-event");
    }


    /**
     * Packt alle Dateien aus {@code sourceDir} (mit Ausnahme von {@code excludeFiles}) in ein ZIP.
     * Ist {@code wrapFolderName} gesetzt, werden die Dateien unter diesem Ordnernamen verschachtelt,
     * um den "ein ZIP aus einem einzelnen Ordner erstellt"-Fall zu simulieren.
     */
    private void zipDirectory(Path sourceDir, Path zipFile, String wrapFolderName, String... excludeFiles) throws IOException {
        List<String> excluded = List.of(excludeFiles);
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zipFile));
             var files = Files.list(sourceDir)) {
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                String fileName = file.getFileName().toString();
                if (excluded.contains(fileName)) {
                    continue;
                }
                String entryName = wrapFolderName != null ? wrapFolderName + "/" + fileName : fileName;
                zos.putNextEntry(new ZipEntry(entryName));
                Files.copy(file, zos);
                zos.closeEntry();
            }
        }
    }
}
