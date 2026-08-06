package kreyj.konfplan.domain.service;

import io.quarkus.narayana.jta.runtime.TransactionConfiguration;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import kreyj.konfplan.adapter.in.web.dto.ImportResultDto;
import kreyj.konfplan.adapter.in.web.dto.VeranstaltungDto;
import kreyj.konfplan.adapter.in.web.dto.VeranstaltungImportDatasetDto;
import kreyj.konfplan.application.port.in.VeranstaltungImportServiceInterface;
import kreyj.konfplan.domain.exception.CsvImportException;
import kreyj.konfplan.persistence.Referent;
import kreyj.konfplan.persistence.Teilnehmer;
import kreyj.konfplan.persistence.Veranstaltung;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Bündelt einen zusammenhängenden Satz von CSV-Dateien (analog zum DataSet-Verfahren aus
 * {@link kreyj.konfplan.application.devsupport.DevDataInitService}) in einem Server-Verzeichnis
 * und importiert ihn in einem Rutsch zu einer neuen Veranstaltung.
 */
@ApplicationScoped
public class VeranstaltungImportService implements VeranstaltungImportServiceInterface {
    private static final Logger LOG = Logger.getLogger(VeranstaltungImportService.class);

    private static final List<String> MANDATORY_FILES = List.of(
        "veranstaltungen.csv", "organisatoren.csv", "slots.csv", "referenten.csv", "teilnehmer.csv");

    private static final String WAHL_VORTRAEGE_CSV = "wahl_vortraege.csv";
    private static final String PFLICHT_VORTRAEGE_CSV = "pflicht_vortraege.csv";

    private static final List<String> ALL_KNOWN_FILES = List.of(
        "gebaeude.csv", "organisatoren.csv", "veranstaltungen.csv", "slots.csv", "referenten.csv",
        "teilnehmer.csv", WAHL_VORTRAEGE_CSV, PFLICHT_VORTRAEGE_CSV, "tn_prios.csv",
        "raum_verfuegbarkeiten.csv", "teilnehmer_verfuegbarkeiten.csv", "ref_verfuegbarkeiten.csv");

    private static final int MAX_ZIP_ENTRIES = 200;
    private static final long MAX_UNCOMPRESSED_BYTES = 20_000_000L;

    @ConfigProperty(name = "konfplan.veranstaltung-import.base-path", defaultValue = "import/veranstaltungen")
    String basePath;

    private final GebaeudeService gebaeudeService;
    private final AdminService adminService;
    private final VeranstaltungService veranstaltungService;
    private final ReferentService referentService;
    private final TeilnehmerService teilnehmerService;


    public VeranstaltungImportService(GebaeudeService gebaeudeService, AdminService adminService,
                                      VeranstaltungService veranstaltungService, ReferentService referentService,
                                      TeilnehmerService teilnehmerService) {
        this.gebaeudeService = gebaeudeService;
        this.adminService = adminService;
        this.veranstaltungService = veranstaltungService;
        this.referentService = referentService;
        this.teilnehmerService = teilnehmerService;
    }


    @Override
    public List<VeranstaltungImportDatasetDto> listDatasets() {
        Path base = Paths.get(basePath);
        if (!Files.isDirectory(base)) {
            return List.of();
        }
        try (Stream<Path> dirs = Files.list(base)) {
            return dirs.filter(Files::isDirectory)
                .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                .map(this::describeDataset)
                .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }


    private VeranstaltungImportDatasetDto describeDataset(Path dir) {
        VeranstaltungImportDatasetDto dto = new VeranstaltungImportDatasetDto();
        dto.name = dir.getFileName().toString();
        dto.vorhandeneDateien = ALL_KNOWN_FILES.stream().filter(f -> Files.exists(dir.resolve(f))).toList();
        dto.fehlendeDateien = computeMissingMandatoryFiles(dir);
        dto.auswaehlbar = dto.fehlendeDateien.isEmpty();
        return dto;
    }


    private List<String> computeMissingMandatoryFiles(Path dir) {
        List<String> missing = new ArrayList<>(
            MANDATORY_FILES.stream().filter(f -> !Files.exists(dir.resolve(f))).toList());
        boolean hasVortraege = Files.exists(dir.resolve(WAHL_VORTRAEGE_CSV)) || Files.exists(dir.resolve(PFLICHT_VORTRAEGE_CSV));
        if (!hasVortraege) {
            missing.add(WAHL_VORTRAEGE_CSV + " oder " + PFLICHT_VORTRAEGE_CSV);
        }
        return missing;
    }


    /**
     * Löst den Verzeichnisnamen zu einem Pfad innerhalb von {@link #basePath} auf.
     * Verteidigung gegen Pfad-Traversal: der Name darf keine Pfad-Trenner/".." enthalten
     * und das aufgelöste Verzeichnis muss weiterhin unterhalb des Basisverzeichnisses liegen.
     */
    private Path resolveDatasetDir(String datasetName) {
        if (datasetName == null || datasetName.isBlank()
            || datasetName.contains("..") || datasetName.contains("/") || datasetName.contains("\\")) {
            throw new IllegalArgumentException("Ungültiger Verzeichnisname: " + datasetName);
        }

        Path base = Paths.get(basePath).toAbsolutePath().normalize();
        Path dir = base.resolve(datasetName).normalize();
        if (!dir.startsWith(base) || !Files.isDirectory(dir)) {
            throw new IllegalArgumentException("Unbekanntes Veranstaltungsverzeichnis: " + datasetName);
        }
        return dir;
    }


    // Der gesamte Bündelimport laeuft bewusst in EINER Transaktion (Alles-oder-nichts, siehe
    // Klassenkommentar) - bei groesseren Teilnehmerzahlen sprengt allein das (bewusst langsame)
    // BCrypt-Hashing der Passwoerter leicht den JTA-Default-Timeout von 60s (in %prod real
    // aufgetreten: "ARJUNA016102: The transaction is not active!" bei 632 Teilnehmern). %dev
    // hat dafuer einen eigenen, grosszuegigeren Default-Timeout (siehe application.properties),
    // %prod nicht - daher hier ein expliziter, groszuegiger Timeout nur fuer diese eine
    // Operation statt den globalen Default fuer alle Transaktionen aufzuweichen.
    @TransactionConfiguration(timeout = 300)
    @Transactional(rollbackOn = Exception.class)
    @Override
    public VeranstaltungDto importDataset(String datasetName) throws Exception {
        Path dir = resolveDatasetDir(datasetName);

        // Pflicht-Dateien serverseitig erneut prüfen (Verteidigung gegen TOCTOU zwischen
        // listDatasets()- und importDataset()-Aufruf, falls sich das Verzeichnis inzwischen geändert hat).
        List<String> missing = computeMissingMandatoryFiles(dir);
        if (!missing.isEmpty()) {
            throw new CsvImportException(dir, "Verzeichnis '" + datasetName + "' ist nicht vollständig. Fehlende Datei(en): "
                + String.join(", ", missing));
        }

        return runImport(dir, datasetName);
    }


    /**
     * Extrahiert ein hochgeladenes ZIP in ein temporäres Verzeichnis und importiert den
     * enthaltenen CSV-Satz genauso wie {@link #importDataset(String)}. Das temporäre Verzeichnis
     * wird in jedem Fall (Erfolg wie Fehler) wieder gelöscht.
     */
    @Transactional(rollbackOn = Exception.class)
    @Override
    public VeranstaltungDto importFromZip(Path zipFilePath) throws Exception {
        Path tempDir = Files.createTempDirectory("veranstaltung-import-");
        try {
            extractZip(zipFilePath, tempDir);
            Path effectiveRoot = determineEffectiveRoot(tempDir);

            List<String> missing = computeMissingMandatoryFiles(effectiveRoot);
            if (!missing.isEmpty()) {
                throw new IllegalArgumentException(
                    "ZIP enthält nicht alle Pflicht-Dateien. Fehlend: " + String.join(", ", missing));
            }

            return runImport(effectiveRoot, "ZIP-Upload");
        } finally {
            deleteRecursively(tempDir);
        }
    }


    private VeranstaltungDto runImport(Path dir, String label) throws Exception {
        LOG.info("Starte Verzeichnis-Import für '" + label + "' ...");

        // 1. Gebäude & Räume (optional)
        Path gebaeudeCsv = dir.resolve("gebaeude.csv");
        if (Files.exists(gebaeudeCsv)) {
            gebaeudeService.importGebaeudeWithRaeumeFromCsv(gebaeudeCsv);
        }

        // 2. Organisatoren (Pflicht)
        Path organisatorenCsv = dir.resolve("organisatoren.csv");
        int anzahlAdmins = adminService.importAdminsFromCsv(organisatorenCsv);
        requirePositive(organisatorenCsv, anzahlAdmins, "organisatoren.csv enthielt keine gültigen Admin-Zeilen.");

        // 3. Veranstaltung (Pflicht, genau eine Zeile)
        Path veranstaltungenCsv = dir.resolve("veranstaltungen.csv");
        List<Veranstaltung> created = veranstaltungService.importFromCsvDetailed(veranstaltungenCsv);
        if (created.size() != 1) {
            throw new CsvImportException(veranstaltungenCsv,
                "veranstaltungen.csv muss genau eine gültige Veranstaltung enthalten (gefunden: " + created.size() + ").");
        }
        Veranstaltung veranstaltung = created.get(0);
        Long vid = veranstaltung.getId();

        // 4. Slots (Pflicht)
        Path slotsCsv = dir.resolve("slots.csv");
        int anzahlSlots = adminService.importSlotsFromCsv(slotsCsv, vid);
        requirePositive(slotsCsv, anzahlSlots, "slots.csv enthielt keine gültigen Zeilen.");

        // 5. Referenten (Pflicht)
        Path referentenCsv = dir.resolve("referenten.csv");
        int anzahlReferenten = referentService.importFromCsv(referentenCsv, vid);
        requirePositive(referentenCsv, anzahlReferenten, "referenten.csv enthielt keine gültigen Zeilen.");

        // 6. Teilnehmer (Pflicht)
        Path teilnehmerCsv = dir.resolve("teilnehmer.csv");
        int anzahlTeilnehmer = teilnehmerService.importFromCsv(teilnehmerCsv, vid);
        requirePositive(teilnehmerCsv, anzahlTeilnehmer, "teilnehmer.csv enthielt keine gültigen Zeilen.");

        // 7. Vorträge (Pflicht in Summe: mind. eine der beiden Dateien muss Inhalt liefern)
        int anzahlVortraege = 0;
        Path wahlVortraegeCsv = dir.resolve(WAHL_VORTRAEGE_CSV);
        if (Files.exists(wahlVortraegeCsv)) {
            anzahlVortraege += adminService.importVortraegeFromCsv(wahlVortraegeCsv, vid);
        }
        Path pflichtVortraegeCsv = dir.resolve(PFLICHT_VORTRAEGE_CSV);
        if (Files.exists(pflichtVortraegeCsv)) {
            anzahlVortraege += adminService.importVortraegeFromCsv(pflichtVortraegeCsv, vid);
        }
        if (anzahlVortraege <= 0) {
            throw new CsvImportException(dir,
                "Weder " + WAHL_VORTRAEGE_CSV + " noch " + PFLICHT_VORTRAEGE_CSV + " enthielten gültige Zeilen.");
        }

        // 8. Prioritäten (optional)
        Path tnPriosCsv = dir.resolve("tn_prios.csv");
        if (Files.exists(tnPriosCsv)) {
            adminService.importTeilnehmerWvPriosFromCsv(tnPriosCsv, vid);
        }

        // 9. Verfügbarkeiten (optional) - Zeilenfehler zählen im Alles-oder-nichts-Import als harter Fehler.
        Path raumVerfCsv = dir.resolve("raum_verfuegbarkeiten.csv");
        if (Files.exists(raumVerfCsv)) {
            failIfErrors(raumVerfCsv, adminService.importRaumVerfuegbarkeitenFromCsv(raumVerfCsv, vid));
        }
        Path tnVerfCsv = dir.resolve("teilnehmer_verfuegbarkeiten.csv");
        if (Files.exists(tnVerfCsv)) {
            failIfErrors(tnVerfCsv, adminService.importNutzerVerfuegbarkeitenFromCsv(tnVerfCsv, Teilnehmer.class, vid));
        }
        Path refVerfCsv = dir.resolve("ref_verfuegbarkeiten.csv");
        if (Files.exists(refVerfCsv)) {
            failIfErrors(refVerfCsv, adminService.importNutzerVerfuegbarkeitenFromCsv(refVerfCsv, Referent.class, vid));
        }

        LOG.info("Import für '" + label + "' abgeschlossen: Veranstaltung '"
            + veranstaltung.getName() + "' (id=" + vid + ") angelegt.");

        return VeranstaltungDto.from(veranstaltung);
    }


    /**
     * Extrahiert ein ZIP nach {@code targetDir}. Verteidigung gegen Zip-Slip (Einträge, die
     * außerhalb von {@code targetDir} landen würden) sowie gegen Zip-Bomben (zu viele Einträge
     * oder zu große Gesamtgröße).
     */
    private void extractZip(Path zipFile, Path targetDir) throws IOException {
        int entryCount = 0;
        long totalBytes = 0;
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zipFile))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (++entryCount > MAX_ZIP_ENTRIES) {
                    throw new IllegalArgumentException("ZIP enthält zu viele Einträge (max. " + MAX_ZIP_ENTRIES + ").");
                }

                Path resolved = targetDir.resolve(entry.getName()).normalize();
                if (!resolved.startsWith(targetDir)) {
                    throw new IllegalArgumentException("ZIP enthält einen ungültigen Eintrag: " + entry.getName());
                }

                if (entry.isDirectory()) {
                    Files.createDirectories(resolved);
                    continue;
                }

                Files.createDirectories(resolved.getParent());
                try (var out = Files.newOutputStream(resolved)) {
                    byte[] buffer = new byte[8192];
                    int read;
                    while ((read = zis.read(buffer)) != -1) {
                        totalBytes += read;
                        if (totalBytes > MAX_UNCOMPRESSED_BYTES) {
                            throw new IllegalArgumentException(
                                "ZIP-Inhalt ist zu groß (max. " + (MAX_UNCOMPRESSED_BYTES / 1_000_000) + " MB entpackt).");
                        }
                        out.write(buffer, 0, read);
                    }
                }
            }
        }
    }


    /**
     * Falls das entpackte Verzeichnis keine der bekannten CSV-Dateien direkt enthält, aber genau
     * ein Unterverzeichnis (typischer Fall: das ZIP wurde aus einem einzelnen Ordner erstellt),
     * wird eine Ebene tiefer abgestiegen.
     */
    private Path determineEffectiveRoot(Path extractedDir) throws IOException {
        boolean hasKnownFileDirectly = ALL_KNOWN_FILES.stream().anyMatch(f -> Files.exists(extractedDir.resolve(f)));
        if (hasKnownFileDirectly) {
            return extractedDir;
        }

        try (Stream<Path> entries = Files.list(extractedDir)) {
            List<Path> topLevel = entries.toList();
            if (topLevel.size() == 1 && Files.isDirectory(topLevel.get(0))) {
                return topLevel.get(0);
            }
        }
        return extractedDir;
    }


    private void deleteRecursively(Path dir) {
        try (Stream<Path> paths = Files.walk(dir)) {
            paths.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.delete(p);
                } catch (IOException e) {
                    LOG.warn("Konnte temporäre Import-Datei nicht löschen: " + p, e);
                }
            });
        } catch (IOException e) {
            LOG.warn("Konnte temporäres Import-Verzeichnis nicht vollständig aufräumen: " + dir, e);
        }
    }


    private void requirePositive(Path file, int count, String message) {
        if (count <= 0) {
            throw new CsvImportException(file, message);
        }
    }


    private void failIfErrors(Path file, ImportResultDto result) {
        if (result.fehler != null && !result.fehler.isEmpty()) {
            throw new CsvImportException(file,
                "Fehler beim Import von " + file.getFileName() + ": " + String.join("; ", result.fehler));
        }
    }
}
