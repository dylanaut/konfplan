package kreyj.konfplan.domain.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.json.Json;
import jakarta.json.JsonException;
import jakarta.transaction.Transactional;
import kreyj.konfplan.adapter.in.web.dto.PlanExportMetadata;
import kreyj.konfplan.adapter.in.web.dto.SolverConfig;
import kreyj.konfplan.domain.exception.BusinessException;
import kreyj.konfplan.domain.exception.CollisionsException;
import kreyj.konfplan.persistence.IdEntity;
import kreyj.konfplan.persistence.NutzerVerfuegbarkeit;
import kreyj.konfplan.persistence.Pflichtvortrag;
import kreyj.konfplan.persistence.Planungsergebnis;
import kreyj.konfplan.persistence.ProtokollKategorie;
import kreyj.konfplan.persistence.Raum;
import kreyj.konfplan.persistence.RaumVerfuegbarkeit;
import kreyj.konfplan.persistence.Referent;
import kreyj.konfplan.persistence.Slot;
import kreyj.konfplan.persistence.Teilnehmer;
import kreyj.konfplan.persistence.Veranstaltung;
import kreyj.konfplan.persistence.Wahlvortrag;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import org.jspecify.annotations.NonNull;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.IntStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toMap;

@ApplicationScoped
public class PlanErstellungService {
    public static final String LINE_SEP = System.lineSeparator();
    private static final Logger LOG = Logger.getLogger(PlanErstellungService.class);
    private static final String MZN_MODEL_FILE = "konfplan.mzn";
    private static final String MZN_SOLVER = "cp-sat";
    private static final DateTimeFormatter WEEKDAY_TIME_FORMAT = DateTimeFormatter.ofPattern("EE,HH:mm");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    private final ProtokollService protokollService;
    private final ObjectMapper objectMapper;
    private final AuffuellungService auffuellungService;
    private final PrioritaetService prioService;

    @ConfigProperty(name = "minizinc.path", defaultValue = "/opt/homebrew/bin/minizinc")
    String miniZincPath;

    private volatile Process runningProcess;

    // Status der asynchronen Planerstellung für das /status-Polling der UI.
    @Getter
    private volatile boolean planning;
    /**
     * Welcher Teilschritt der Planerstellung gerade läuft. Der vom Organisator konfigurierte
     * Timeout (siehe PlanungTab.vue) gilt ausschließlich für {@link Phase#BERECHNUNG} - die
     * DB-lastigen Phasen davor/danach sind über {@link #cancel()} nicht unterbrechbar, daher
     * blendet die UI den Abbrechen-Button außerhalb von BERECHNUNG als deaktiviert ein.
     */
    public enum Phase {VORBEREITUNG, BERECHNUNG, PERSISTIERUNG}

    @Getter
    private volatile Phase phase;
    private volatile boolean cancelled;
    /**
     * -- GETTER --
     * Fehlermeldung des letzten (asynchronen) Planerstellungs-Laufs, oder
     * , wenn der
     * letzte Lauf erfolgreich war bzw. noch keiner stattfand. Wird zu Beginn jedes Laufs zurückgesetzt.
     */
    @Getter
    private volatile String lastError;


    public PlanErstellungService(ProtokollService protokollService, ObjectMapper objectMapper,
                                 AuffuellungService auffuellungService, PrioritaetService prioService) {
        this.protokollService = protokollService;
        this.objectMapper = objectMapper;
        this.auffuellungService = auffuellungService;
        this.prioService = prioService;
    }


    @PreDestroy
    public void shutdown() {
        cancel();
    }


    public void erstellePlan(Long veranstaltungId, SolverConfig config, String username) throws Exception {
        erstellePlan(veranstaltungId, config, MZN_MODEL_FILE, username);
    }


    /**
     * Bewusst NICHT {@code @Transactional}: der MiniZinc-Aufruf weiter unten blockiert bis zu
     * {@code config.getTimeout()} Sekunden (vom Organisator frei konfigurierbar, siehe PlanungTab.vue).
     * Würde diese Methode als Ganzes in einer Transaktion laufen, reißt der JTA-Transaktions-
     * Timeout (60s Default, 3m in dev) bei längeren Solver-Läufen mitten im externen Prozess-Aufruf,
     * noch bevor das Ergebnis gespeichert werden kann ("ARJUNA016102: The transaction is not
     * active!"). DB-Zugriffe sind daher in {@link #bereiteDznVor} und
     * {@link #speicherePlanungsergebnis} isoliert, jeweils in ihrer eigenen kurzen Transaktion.
     */
    public void erstellePlan(Long veranstaltungId, SolverConfig config, String modelName, String username) throws Exception {
        URL modelUrl = getClass().getClassLoader().getResource("minizinc/" + modelName);
        if (null == modelUrl) {
            throw new FileNotFoundException("MiniZinc model not found: " + modelName);
        }

        planning = true;
        cancelled = false;
        lastError = null;
        phase = Phase.VORBEREITUNG;

        try {
            DznVorbereitung vorbereitung = bereiteDznVor(veranstaltungId, config, username);
            if (null == vorbereitung) {
                return;
            }

            Path tempDzn = Files.createTempFile("planung_", ".dzn");
            Files.writeString(tempDzn, vorbereitung.dznContent(), StandardCharsets.UTF_8);

            if (LOG.isDebugEnabled()) {
                LOG.debug("MiniZinc Datendatei:\n" + vorbereitung.dznContent());
            }

            try {
                phase = Phase.BERECHNUNG;
                String resultJson = rufeMiniZincAuf(Paths.get(modelUrl.toURI()), tempDzn, config);

                if (resultJson.contains("instanz_slot") && isValidJson(resultJson)) {
                    phase = Phase.PERSISTIERUNG;
                    speicherePlanungsergebnis(veranstaltungId, resultJson, config, vorbereitung);
                    protokollService.log(ProtokollKategorie.PLANUNG, "Planerstellung erfolgreich",
                        "Planerstellung für '" + vorbereitung.vName() + "' abgeschlossen. Ergebnis wurde gespeichert.", veranstaltungId, veranstaltungId, username);
                } else {
                    String message = "MiniZinc konnte keine Lösung finden.";
                    protokollService.log(ProtokollKategorie.PLANUNG, "Planerstellung fehlgeschlagen", message, veranstaltungId, veranstaltungId, username);
                    if (!cancelled) {
                        lastError = message;
                    }
                }
            } finally {
                Files.deleteIfExists(tempDzn);
            }
        } catch (Exception e) {
            protokollService.log(ProtokollKategorie.PLANUNG, "Fehler bei Planerstellung", e.getMessage(), veranstaltungId, veranstaltungId, username);
            if (!cancelled) {
                lastError = (e.getMessage() != null) ? e.getMessage() : e.toString();
            }
            throw e;
        } finally {
            planning = false;
            phase = null;
        }
    }


    /**
     * Lädt die Planungsdaten, prüft Vorbedingungen/Kollisionen und erzeugt den DZN-Content -
     * alles in einer eigenen, kurzen Transaktion, getrennt vom nachfolgenden MiniZinc-Aufruf.
     * Liefert {@code null}, wenn die Planerstellung mangels erfüllter Vorbedingungen gar nicht
     * erst gestartet wird (bereits geloggt, {@link #lastError} gesetzt).
     */
    @Transactional
    DznVorbereitung bereiteDznVor(Long veranstaltungId, SolverConfig config, String username) {
        Veranstaltung veranstaltung = Veranstaltung.findById(veranstaltungId);
        assert veranstaltung != null;
        String vName = veranstaltung.getName();
        werfeBeiKollisionen(veranstaltung, veranstaltungId, username);

        PlanungsDaten daten = ladeSortiertePlanungsdaten(veranstaltung);
        List<Teilnehmer> teilnehmer = daten.teilnehmer();
        List<Wahlvortrag> wahlvortraege = daten.wahlvortraege();
        List<Slot> slots = daten.slots();
        List<Raum> raeume = daten.raeume();

        if (slots.isEmpty() || teilnehmer.isEmpty() || wahlvortraege.isEmpty()) {
            LOG.warn("Keine Wahlvorträge, Slots oder Teilnehmer vorhanden. Minizinc Datenerstellung wird nicht gestartet.");
            String message = "Voraussetzungen (Teilnehmer, Slots, Wahlvorträge) nicht erfüllt.";
            protokollService.log(ProtokollKategorie.PLANUNG, "Minizinc Datenerstellung abgebrochen", message, veranstaltungId, veranstaltungId, username);
            lastError = message;
            return null;
        }

        Map<Long, Map<Long, Integer>> teilnehmerPrioritaeten =
            prioService.getVortragPrioritaetenByVeranstaltung(veranstaltungId);
        String dznContent = generiereDzn(veranstaltung, teilnehmer, wahlvortraege, slots, raeume,
            teilnehmerPrioritaeten, config.getMaxInstanzen(), config.getMaxWvsProTn());

        return new DznVorbereitung(vName, dznContent,
            teilnehmer.stream().map(IdEntity::getId).toList(),
            wahlvortraege.stream().map(IdEntity::getId).toList(),
            slots.stream().map(IdEntity::getId).toList(),
            raeume.stream().map(IdEntity::getId).toList());
    }


    public record DznVorbereitung(String vName,
                                  String dznContent,
                                  List<Long> teilnehmer_oids,
                                  List<Long> wahlvortrag_oids,
                                  List<Long> slot_oids,
                                  List<Long> raum_oids) {
    }


    private record PlanungsDaten(List<Teilnehmer> teilnehmer, List<Wahlvortrag> wahlvortraege,
                                 List<Slot> slots, List<Raum> raeume) {
    }


    /**
     * Lädt Teilnehmer/Wahlvorträge/Slots/Räume einer Veranstaltung in derselben, stabilen
     * Reihenfolge, die auch die MiniZinc-Indizes bestimmt (siehe {@link #generiereDzn}). Wird
     * sowohl bei der Dzn-Erstellung als auch beim Konsistenz-Check eines Ergebnis-Imports
     * (Oids müssen exakt zum aktuellen Datenstand passen) verwendet.
     */
    private PlanungsDaten ladeSortiertePlanungsdaten(Veranstaltung veranstaltung) {
        List<Teilnehmer> teilnehmer = veranstaltung.teilnehmer().stream().sorted(Comparator.comparing(IdEntity::getId)).toList();
        List<Wahlvortrag> wahlvortraege = veranstaltung.getWahlvortraege().stream().sorted(Comparator.comparing(IdEntity::getId)).toList();
        List<Slot> slots = veranstaltung.getSlots().stream().sorted().toList();
        List<Raum> raeume = veranstaltung.getRaeume().stream().sorted(Comparator.comparing(IdEntity::getId)).toList();
        return new PlanungsDaten(teilnehmer, wahlvortraege, slots, raeume);
    }


    /**
     * Erzeugt die MiniZinc-Datendatei (.dzn) für eine Veranstaltung, ohne den Solver zu starten -
     * für den Export/Download-Button in der PlanungTab.vue. Wirft eine {@link BusinessException},
     * wenn die Vorbedingungen (Teilnehmer, Slots, Wahlvorträge, keine Kollisionen) nicht erfüllt sind.
     */
    @Transactional
    public String generiereDznVorschau(Long veranstaltungId, SolverConfig config, String username) {
        DznVorbereitung vorbereitung = bereiteDznVor(veranstaltungId, config, username);
        if (null == vorbereitung) {
            throw new BusinessException(lastError);
        }
        return vorbereitung.dznContent();
    }


    private static final long MAX_IMPORT_ENTRY_BYTES = 50L * 1024 * 1024;
    private static final String EXPORT_ENTRY_DZN = "veranstaltung.dzn";
    private static final String EXPORT_ENTRY_METADATA = "metadata.json";
    private static final String IMPORT_ENTRY_ERGEBNIS = "ergebnis.json";


    /**
     * Erzeugt ein vollständiges Export-Paket (ZIP) für eine Veranstaltung: die .dzn-Datendatei,
     * das MiniZinc-Modell (zur Bequemlichkeit auf einem externen Rechner ohne KonfPlan-Setup) und
     * eine {@code metadata.json} mit der verwendeten Solver-Konfiguration sowie den vier
     * Oid-Listen, die {@link #importErgebnisBundle} später braucht, um den positionsbasierten
     * Solver-Output wieder auf die richtigen DB-Ids abzubilden.
     */
    public byte[] erstelleExportBundle(Long veranstaltungId, SolverConfig config, String username) throws IOException {
        DznVorbereitung vorbereitung = bereiteDznVor(veranstaltungId, config, username);
        if (null == vorbereitung) {
            throw new BusinessException(lastError);
        }

        URL modelUrl = getClass().getClassLoader().getResource("minizinc/" + MZN_MODEL_FILE);
        if (null == modelUrl) {
            throw new FileNotFoundException("MiniZinc model not found: " + MZN_MODEL_FILE);
        }

        PlanExportMetadata metadata = new PlanExportMetadata(veranstaltungId, config,
            vorbereitung.teilnehmer_oids(), vorbereitung.wahlvortrag_oids(),
            vorbereitung.slot_oids(), vorbereitung.raum_oids());

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            zos.putNextEntry(new ZipEntry(EXPORT_ENTRY_DZN));
            zos.write(vorbereitung.dznContent().getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            zos.putNextEntry(new ZipEntry(EXPORT_ENTRY_METADATA));
            zos.write(objectMapper.writeValueAsBytes(metadata));
            zos.closeEntry();

            zos.putNextEntry(new ZipEntry(MZN_MODEL_FILE));
            try (InputStream modelStream = modelUrl.openStream()) {
                modelStream.transferTo(zos);
            }
            zos.closeEntry();
        }

        return baos.toByteArray();
    }


    /**
     * Importiert ein extern (z.B. auf einem Hochleistungsrechner) berechnetes MiniZinc-Ergebnis,
     * das zuvor über {@link #erstelleExportBundle} exportiert wurde. Erwartet ein ZIP mit der
     * unveränderten {@code metadata.json} aus dem Export sowie einer {@code ergebnis.json} mit
     * der (rohen) MiniZinc-Ausgabe - darf die komplette {@code --intermediate}-Ausgabe enthalten,
     * nicht nur die letzte Zeile, siehe {@link #extrahiereLoesung}.
     * Bricht mit {@link BusinessException} ab, wenn sich die Veranstaltungsdaten (Teilnehmer,
     * Wahlvorträge, Slots, Räume) seit dem Export geändert haben - die im Ergebnis kodierten
     * Solver-Indizes wären sonst nicht mehr den richtigen DB-Ids zuordenbar.
     */
    @Transactional
    public void importErgebnisBundle(Long veranstaltungId, Path zipPath, String username) throws IOException {
        Veranstaltung veranstaltung = Veranstaltung.findById(veranstaltungId);
        assert veranstaltung != null;

        try {
            String metadataJson = null;
            String ergebnisRaw = null;

            try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zipPath), StandardCharsets.UTF_8)) {
                ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    String content = readBounded(zis, MAX_IMPORT_ENTRY_BYTES);
                    if (EXPORT_ENTRY_METADATA.equals(entry.getName())) {
                        metadataJson = content;
                    } else if (IMPORT_ENTRY_ERGEBNIS.equals(entry.getName())) {
                        ergebnisRaw = content;
                    }
                }
            }

            if (null == metadataJson || null == ergebnisRaw) {
                throw new BusinessException("Import-Paket unvollständig: '" + EXPORT_ENTRY_METADATA
                    + "' und '" + IMPORT_ENTRY_ERGEBNIS + "' werden erwartet.");
            }

            PlanExportMetadata metadata;
            try {
                metadata = objectMapper.readValue(metadataJson, PlanExportMetadata.class);
            } catch (JsonProcessingException e) {
                throw new BusinessException(EXPORT_ENTRY_METADATA + " ist ungültig: " + e.getMessage());
            }

            if (!veranstaltungId.equals(metadata.getVeranstaltungId())) {
                throw new BusinessException("Export-Bundle gehört zu einer anderen Veranstaltung.");
            }
            pruefeOidsUnveraendert(veranstaltung, metadata);

            String loesungsJson;
            try {
                loesungsJson = extrahiereLoesung(new BufferedReader(new StringReader(ergebnisRaw)), metadata.getSolverConfig().getTimeout());
            } catch (MinizincException e) {
                throw new BusinessException(e.getMessage());
            }

            if (loesungsJson.isEmpty() || !loesungsJson.contains("instanz_slot") || !isValidJson(loesungsJson)) {
                throw new BusinessException(IMPORT_ENTRY_ERGEBNIS + " enthält keine gültige MiniZinc-Lösung.");
            }

            DznVorbereitung vorbereitung = new DznVorbereitung(veranstaltung.getName(), "",
                metadata.getTeilnehmerOids(), metadata.getWahlvortragOids(),
                metadata.getSlotOids(), metadata.getRaumOids());

            speicherePlanungsergebnis(veranstaltungId, loesungsJson, metadata.getSolverConfig(), vorbereitung);
            protokollService.log(ProtokollKategorie.PLANUNG, "Planungsergebnis importiert",
                "Extern berechnetes Planungsergebnis für '" + veranstaltung.getName() + "' importiert.",
                veranstaltungId, veranstaltungId, username);
        } catch (BusinessException e) {
            protokollService.log(ProtokollKategorie.PLANUNG, "Ergebnis-Import fehlgeschlagen", e.getMessage(),
                veranstaltungId, veranstaltungId, username);
            throw e;
        }
    }


    /**
     * Vergleicht die aktuellen Teilnehmer/Wahlvortrag/Slot/Raum-Ids einer Veranstaltung
     * (reihenfolgeabhängig, wie beim Dzn-Export) mit den beim Export gespeicherten Oid-Listen.
     */
    private void pruefeOidsUnveraendert(Veranstaltung veranstaltung, PlanExportMetadata metadata) {
        PlanungsDaten daten = ladeSortiertePlanungsdaten(veranstaltung);
        List<Long> aktuelleTnOids = daten.teilnehmer().stream().map(IdEntity::getId).toList();
        List<Long> aktuelleWvOids = daten.wahlvortraege().stream().map(IdEntity::getId).toList();
        List<Long> aktuelleSlotOids = daten.slots().stream().map(IdEntity::getId).toList();
        List<Long> aktuelleRaumOids = daten.raeume().stream().map(IdEntity::getId).toList();

        if (!aktuelleTnOids.equals(metadata.getTeilnehmerOids())
            || !aktuelleWvOids.equals(metadata.getWahlvortragOids())
            || !aktuelleSlotOids.equals(metadata.getSlotOids())
            || !aktuelleRaumOids.equals(metadata.getRaumOids())) {
            throw new BusinessException("Veranstaltungsdaten haben sich seit dem Export geändert "
                + "(andere Teilnehmer/Wahlvorträge/Slots/Räume) - Import abgebrochen.");
        }
    }


    private static String readBounded(InputStream in, long maxBytes) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        long total = 0;
        int read;
        while ((read = in.read(chunk)) != -1) {
            total += read;
            if (total > maxBytes) {
                throw new BusinessException("Datei im Import-Paket ist zu groß (> " + (maxBytes / 1024 / 1024) + " MB).");
            }
            buffer.write(chunk, 0, read);
        }
        return buffer.toString(StandardCharsets.UTF_8);
    }


    /**
     * Synchrone Vorbedingungs-Prüfung für die UI: Wirft eine {@link CollisionsException}, wenn die
     * Eingangsdaten Kollisionen enthalten. Wird vor dem asynchronen Start der Planerstellung aufgerufen,
     * damit die Fehlermeldung in der HTTP-Antwort (BusinessExceptionMapper -> 400) zurückkommt.
     */
    @Transactional
    public void pruefeKollisionenOrThrow(Long veranstaltungId, String username) {
        Veranstaltung veranstaltung = Veranstaltung.findById(veranstaltungId);
        assert veranstaltung != null;
        werfeBeiKollisionen(veranstaltung, veranstaltungId, username);
    }


    private void werfeBeiKollisionen(Veranstaltung veranstaltung, Long veranstaltungId, String username) {
        List<Kollision> kollisionen = pruefeKollisionen(veranstaltung);
        if (kollisionen.isEmpty()) {
            return;
        }
        String vName = veranstaltung.getName();
        String details = kollisionen.stream().map(Kollision::nachricht).collect(joining(LINE_SEP));
        String message = "Inkonsistente Daten für Planerstellung in '" + vName + "':" + LINE_SEP + details;
        LOG.warn(message);
        protokollService.log(ProtokollKategorie.PLANUNG, "Planerstellung abgebrochen",
            "Planerstellung für '" + vName + "' abgebrochen:" + LINE_SEP + details,
            veranstaltungId, veranstaltungId, username);
        throw new CollisionsException(message);
    }


    List<Kollision> pruefeKollisionen(Veranstaltung veranstaltung) {
        List<Kollision> kollisionen = new ArrayList<>();

        List<Teilnehmer> teilnehmer = veranstaltung.teilnehmer();
        List<Pflichtvortrag> pflichtvortraege = veranstaltung.getPflichtvortraege();

        // Einmalig bulk-geladen statt (wie zuvor) einer NutzerVerfuegbarkeit-Query pro Teilnehmer
        // bzw. pro Pflichtvortrag und einer RaumVerfuegbarkeit-Query pro Pflichtvortrag.
        Map<Long, NutzerVerfuegbarkeit> nvMap =
            NutzerVerfuegbarkeit.<NutzerVerfuegbarkeit>list("veranstaltungId = ?1", veranstaltung.getId())
                .stream().collect(toMap(NutzerVerfuegbarkeit::getNutzerId, Function.identity()));
        Map<Long, RaumVerfuegbarkeit> rvMap =
            RaumVerfuegbarkeit.<RaumVerfuegbarkeit>list("veranstaltungId = ?1", veranstaltung.getId())
                .stream().collect(toMap(RaumVerfuegbarkeit::getRaumId, Function.identity()));
        Map<Long, Slot> slotMap = veranstaltung.getSlots().stream()
            .collect(toMap(Slot::getId, Function.identity()));

        // Teilnehmer dürfen für ihren Pflichtslot nicht mehr als verfügbar markiert sein.
        for (Teilnehmer tn : teilnehmer) {
            NutzerVerfuegbarkeit nv = nvMap.get(tn.getId());
            if (null == nv) {
                continue;
            }
            for (Pflichtvortrag pv : pflichtvortraege) {
                Slot pflichtslot = pv.getPflichtslot();
                if (tn.gehoertZuGruppe(pv.getPflichtgruppe())
                    && nv.getVerfuegbareSlotIds().contains(pflichtslot.getId())) {
                    String tnName = StringUtils.strip(tn.getFullName());
                    if (StringUtils.isBlank(tnName)) {
                        tnName = tn.getEmail();
                    }
                    kollisionen.add(new Kollision(Kollision.Typ.TEILNEHMER_VERFUEGBARKEIT,
                        "Verfügbarkeits-Kollision: " + tnName + " (" + pv.getPflichtgruppe()
                            + ") hat Pflichtvortrag in Slot " + pflichtslot.getDescription()
                            + ". Bitte seine 'Verfügbaren Slots' anpassen."));
                }
            }
        }

        // Raum/Slot-Kollisionen zwischen Pflicht- und Wahlvorträgen erkennen.
        for (Pflichtvortrag pv : pflichtvortraege) {
            Raum pflichtraum = pv.getPflichtraum();
            Slot pflichtslot = pv.getPflichtslot();

            RaumVerfuegbarkeit rv = rvMap.get(pflichtraum.getId());
            if (null != rv && rv.getVerfuegbareSlotIds().contains(pflichtslot.getId())) {
                kollisionen.add(new Kollision(Kollision.Typ.RAUM_SLOT,
                    "Pflichtvortrag '" + pv.getTitel() + "' ist reserviert für Raum '" + pflichtraum.getName()
                        + "' und Slot " + pflichtslot.getDescription()
                        + ". Dieser Raum steht gleichzeitig für Wahlvorträge zur Verfügung, vgl. Verfügbare Slots: "
                        + rv.getVerfuegbareSlotIds().stream()
                        .map(slotMap::get)
                        .map(Slot::getDescription)
                        .collect(joining(", "))
                        + "."));
            }
        }

        // Referenten-Verfügbarkeit für ihren eigenen Pflichtvortrag prüfen.
        for (Pflichtvortrag pv : pflichtvortraege) {
            Referent referent = pv.getReferent();
            Slot pflichtslot = pv.getPflichtslot();
            NutzerVerfuegbarkeit nv = nvMap.get(referent.getId());
            if (null != nv && nv.getVerfuegbareSlotIds().contains(pflichtslot.getId())) {
                kollisionen.add(new Kollision(Kollision.Typ.REFERENT_VERFUEGBARKEIT,
                    "Verfügbarkeits-Kollision: Referent " + referent.getFullName() + " hat Pflichtvortrag '"
                        + pv.getTitel() + "' in Slot " + pflichtslot.getDescription()
                        + ", ist dafür aber weiterhin als verfügbar markiert. Bitte seine 'Verfügbaren Slots' anpassen."));
            }
        }

        return kollisionen;
    }


    public String rufeMiniZincAuf(Path modelPath, Path dznPath, SolverConfig solverConfig) throws IOException, InterruptedException {
        ProcessBuilder pb = getProcessBuilder(modelPath, dznPath, solverConfig);

        try {
            runningProcess = pb.start();
            LOG.info("MiniZinc gestartet..");
            String loesung;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(runningProcess.getInputStream(), StandardCharsets.UTF_8))) {
                loesung = extrahiereLoesung(reader, solverConfig.getTimeout());
            }

            int exitCode = runningProcess.waitFor();
            LOG.info("MiniZinc-Prozess beendet mit Exit-Code: " + exitCode);

            return loesung;
        } finally {
            runningProcess = null;
        }
    }


    /**
     * Liest MiniZinc-Ausgabe zeilenweise, merkt sich die letzte gültige JSON-Zeile (bei
     * {@code --intermediate} werden mehrere Zwischenlösungen ausgegeben) und wertet die
     * bekannten Fehler-/Status-Marker aus. Von {@link #rufeMiniZincAuf} auf die Live-Ausgabe des
     * Solver-Prozesses angewendet, und von {@link #importErgebnisBundle} auf den Rohtext einer
     * extern eingesammelten Ergebnisdatei - beide Fälle brauchen dieselbe Validierung, da eine
     * "roh" umgeleitete MiniZinc-Ausgabe ebenfalls mehrere Zeilen/Marker enthalten kann.
     */
    private String extrahiereLoesung(BufferedReader reader, int timeoutSekunden) throws IOException {
        String lastJsonSolution = "";
        StringBuilder fullLog = new StringBuilder();

        String line;
        while ((line = reader.readLine()) != null) {
            fullLog.append(line).append(LINE_SEP);
            if (line.trim().startsWith("{") && isValidJson(line)) {
                lastJsonSolution = line;

                if (LOG.isDebugEnabled()) {
                    LOG.debugf("Zwischenlösung gefunden: %s", line);
                }
            }
        }

        String output = fullLog.toString();

        if (LOG.isDebugEnabled()) {
            LOG.debug("Vollständige MiniZinc-Ausgabe:\n" + output);
        }

        if (output.contains("=====UNSATISFIABLE=====")) {
            throw new MinizincException(MinizincException.MZ_Exception.UNSATISFIABLE);
        }

        if (output.contains("Error:")) {
            throw new MinizincException(MinizincException.MZ_Exception.INVOCATION_ERROR, output);
        }

        if (lastJsonSolution.isEmpty() && output.contains("=====UNKNOWN=====")) {
            throw new MinizincException(MinizincException.MZ_Exception.TIMEOUT,
                "In der vorgegebenen Zeit (" + timeoutSekunden + " Sek.) konnte kein Ergebnis berechnet werden.");
        }

        return lastJsonSolution;
    }


    private @NonNull ProcessBuilder getProcessBuilder(Path modelPath, Path dznPath, SolverConfig solverConfig) {
        List<String> command = new ArrayList<>(Arrays.asList(
            miniZincPath, "--solver", MZN_SOLVER,
            "--solver-time-limit", String.valueOf(solverConfig.getTimeout() * 1000),
            "--parallel", String.valueOf(solverConfig.getNumThreads())
        ));
        command.add("--intermediate");
        command.add(modelPath.toAbsolutePath().toString());
        command.add(dznPath.toAbsolutePath().toString());

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);

        return pb;
    }


    public void cancel() {
        // Markiert den Lauf als abgebrochen, damit die nachlaufende erstellePlan-Logik
        // keinen irreführenden lastError (z.B. "keine Lösung") mehr setzt.
        cancelled = true;
        lastError = null;
        if (runningProcess != null && runningProcess.isAlive()) {
            runningProcess.destroyForcibly();
            LOG.info("MiniZinc-Prozess wurde abgebrochen.");
        }
    }


    private String generiereDzn(Veranstaltung veranstaltung, List<Teilnehmer> teilnehmer, List<Wahlvortrag> wahlvortraege,
                                List<Slot> slots, List<Raum> raeume,
                                Map<Long, Map<Long, Integer>> teilnehmerPrioritaeten,
                                int maxInstanzen, int maxWvsProTn) {
        StringBuilder sb = new StringBuilder();
        sb.append("%Generiert am: ").append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))).append(",Version: 1.0;\n");
        sb.append("max_instanzen = ").append(maxInstanzen).append(";\n");
        sb.append("max_wvs_pro_tn = ").append(maxWvsProTn).append(";\n");
        sb.append("opt_referent_raumtreue = true;\n\n");

        appendSlots(slots, sb);
        appendRaeume(raeume, sb);
        appendTeilnehmer(teilnehmer, sb);
        appendWahlvortraege(wahlvortraege, slots, sb);
        appendTnPrios(teilnehmer, wahlvortraege, teilnehmerPrioritaeten, sb);
        appendTnVerfuegbarkeiten(veranstaltung, teilnehmer, slots, sb);
        appendReferentVerfuegbarkeiten(wahlvortraege, veranstaltung, slots, sb);
        appendRaumVerfuegbarkeiten(veranstaltung, raeume, slots, sb);

        LOG.info("MiniZinc Daten erstellt");

        return sb.toString();
    }


    private static void appendSlots(List<Slot> slots, StringBuilder sb) {
        final int nSlots = slots.size();
        sb.append("n_slots = ").append(nSlots).append(";\n");
        sb.append("slots = [");
        int sIdx = 0;
        for (Slot s : slots) {
            sb.append(String.format("\n(oid: %d)", s.getId()));
            if (++sIdx < nSlots) {
                sb.append(",");
            } else {
                sb.append(" ");
            }
            sb.append(String.format(" %% %d: %s - %s", sIdx, s.getStartTime().format(WEEKDAY_TIME_FORMAT), s.getEndTime().format(TIME_FORMAT)));
        }
        sb.append("\n];\n\n");
    }


    private static void appendRaeume(List<Raum> raeume, StringBuilder sb) {
        final int nRaeume = raeume.size();
        sb.append("n_raeume = ").append(nRaeume).append(";\n");
        sb.append("raeume = [");

        int rIdx = 0;
        for (Raum raum : raeume) {
            sb.append(String.format("\n(oid: %d, kapazitaet: %d)", raum.getId(), raum.getKapazitaet()));
            if (++rIdx < nRaeume) {
                sb.append(",");
            } else {
                sb.append(" ");
            }
            sb.append(String.format(" %% %d: %s, %d", rIdx, raum.getName(), raum.getKapazitaet()));
        }
        sb.append("\n];\n\n");
    }


    private static void appendTeilnehmer(List<Teilnehmer> teilnehmer, StringBuilder sb) {
        final int nTNs = teilnehmer.size();
        sb.append("n_teilnehmer = ").append(nTNs).append(";\n");
        sb.append("teilnehmer = [");

        int pIdx = 0;
        for (Teilnehmer tn : teilnehmer) {
            sb.append(String.format("\n(oid: %d)", tn.getId()));
            if (++pIdx < nTNs) {
                sb.append(",");
            } else {
                sb.append(" ");
            }
            sb.append(String.format(" %% %d: %s %s", pIdx, tn.getEmail(), tn.getGruppen()));
        }

        sb.append("\n];\n\n");
    }


    private static void appendWahlvortraege(List<Wahlvortrag> wahlvortraege, List<Slot> slots, StringBuilder sb) {
        final int nWVs = wahlvortraege.size();
        sb.append("n_wahlvortraege = ").append(nWVs).append(";\n");
        Map<Long, Integer> refMap = new HashMap<>();
        int refCounter = 1;
        for (Wahlvortrag v : wahlvortraege) {
            if (!refMap.containsKey(v.getReferent().getId())) {
                refMap.put(v.getReferent().getId(), refCounter++);
            }
        }

        int wvIdx = 0;
        sb.append("wahlvortraege = [");
        String constantSlotIds = IntStream.rangeClosed(1, slots.size()).mapToObj(String::valueOf).collect(joining(","));

        for (Wahlvortrag wv : wahlvortraege) {
            sb.append("\n");
            sb.append(String.format("(oid: %d, referent_id: %d, belegbare_slots: [%s])", wv.getId(), refMap.get(wv.getReferent().getId()), constantSlotIds));
            if (++wvIdx < nWVs) {
                sb.append(",");
            } else {
                sb.append(" ");
            }
            sb.append(String.format(" %% %d: %s", wvIdx, wv.getTitel().substring(0, Math.min(25, wv.getTitel().length()))));
        }
        sb.append("\n];\n\n");
    }


    private static void appendTnPrios(List<Teilnehmer> teilnehmer, List<Wahlvortrag> wahlvortraege,
                                      Map<Long, Map<Long, Integer>> teilnehmerPrioritaeten, StringBuilder sb) {
        sb.append("prioritaeten = [|");
        int wvSize = wahlvortraege.size();
        int tnSize = teilnehmer.size();
        int tnIdx = 0;
        for (Teilnehmer tn : teilnehmer) {
            sb.append("\n");
            Map<Long, Integer> prios = teilnehmerPrioritaeten.getOrDefault(tn.getId(), Map.of());
            int wvIdx = 0;
            for (Wahlvortrag v : wahlvortraege) {
                sb.append(prios.getOrDefault(v.getId(), 0));
                if (++wvIdx < wvSize) {
                    sb.append(",");
                } else {
                    sb.append(" |");
                }
            }
            if (++tnIdx == tnSize) {
                sb.append("];");
            }
            sb.append(String.format(" %% %d: %s", tn.getId(), tn.getEmail()));
        }
        sb.append("\n\n");
    }


    private static void appendTnVerfuegbarkeiten(Veranstaltung veranstaltung, List<Teilnehmer> teilnehmer, List<Slot> slots,
                                                 StringBuilder sb) {
        sb.append("%In welchen Slots jeder Teilnehmer für Wahlvorträge einer Veranstaltung verfügbar ist:\n");
        int tnSize = teilnehmer.size();
        int slotSize = slots.size();
        int tnIdx = 0;
        Map<Long, NutzerVerfuegbarkeit> nvMap =
            NutzerVerfuegbarkeit.<NutzerVerfuegbarkeit>list("veranstaltungId = ?1", veranstaltung.getId())
                .stream().collect(toMap(NutzerVerfuegbarkeit::getNutzerId, Function.identity()));
        sb.append("tn_verfuegbar = [| %% Slot 1..").append(slots.size());
        for (Teilnehmer tn : teilnehmer) {
            sb.append("\n");
            int sIdx = 0;
            NutzerVerfuegbarkeit nv = nvMap.get(tn.getId());
            Set<Long> verfSlotIds = nv.getVerfuegbareSlotIds();
            for (Slot s : slots) {
                sb.append(verfSlotIds.contains(s.getId()));
                if (++sIdx < slotSize) {
                    sb.append(",");
                } else {
                    sb.append(" |");
                }
            }
            if (++tnIdx == tnSize) {
                sb.append("];");
            }
            sb.append(String.format(" %% %d: %s", tn.getId(), tn.getEmail()));
        }
        sb.append("\n\n");
    }


    private static void appendRaumVerfuegbarkeiten(Veranstaltung veranstaltung, List<Raum> raeume, List<Slot> slots, StringBuilder sb) {
        sb.append("%In welchen Slots Räume für Wahlvorträge einplanbar ist:\n");
        int raeumeSize = raeume.size();
        int slotSize = slots.size();
        Map<Long, RaumVerfuegbarkeit> rvMap =
            RaumVerfuegbarkeit.<RaumVerfuegbarkeit>list("veranstaltungId = ?1", veranstaltung.getId())
                .stream().collect(toMap(RaumVerfuegbarkeit::getRaumId, Function.identity()));

        int raumIdx = 0;
        sb.append("raum_belegbar = [| %% Slot 1..").append(slots.size());
        for (Raum raum : raeume) {
            sb.append("\n");
            RaumVerfuegbarkeit rv = rvMap.get(raum.getId());
            Set<Long> verfSlotIds = rv.getVerfuegbareSlotIds();
            int sIdx = 0;
            for (Slot s : slots) {
                sb.append(verfSlotIds.contains(s.getId()));
                if (++sIdx < slotSize) {
                    sb.append(",");
                } else {
                    sb.append(" |");
                }
            }
            if (++raumIdx == raeumeSize) {
                sb.append("];");
            }
            sb.append(String.format(" %% %d: %s", raum.getId(), raum.getName()));
        }
        sb.append("\n\n");
    }


    private static void appendReferentVerfuegbarkeiten(List<Wahlvortrag> wahlvortraege, Veranstaltung veranstaltung,
                                                       List<Slot> slots, StringBuilder sb) {
        sb.append("%In welchen Slots der Referent eines Wahlvortrags verfügbar ist (Pflichtvortrag-Kollisionen):\n");
        // Reihenfolge MUSS mit appendWahlvortraege's refMap übereinstimmen: dieselbe 'wahlvortraege'-Liste,
        // distinct() auf geordnetem Stream erhält First-Encounter-Reihenfolge -> identische 1-basierte Indizes.
        List<Referent> referenten = wahlvortraege.stream().map(Wahlvortrag::getReferent).distinct().toList();
        int refSize = referenten.size();
        int slotSize = slots.size();
        Map<Long, NutzerVerfuegbarkeit> nvMap =
            NutzerVerfuegbarkeit.<NutzerVerfuegbarkeit>list("veranstaltungId = ?1", veranstaltung.getId())
                .stream().collect(toMap(NutzerVerfuegbarkeit::getNutzerId, Function.identity()));

        int refIdx = 0;
        sb.append("referent_verfuegbar = [| %% Slot 1..").append(slots.size());
        for (Referent referent : referenten) {
            sb.append("\n");
            Set<Long> verfSlotIds = nvMap.get(referent.getId()).getVerfuegbareSlotIds();
            int sIdx = 0;
            for (Slot s : slots) {
                sb.append(verfSlotIds.contains(s.getId()));
                if (++sIdx < slotSize) {
                    sb.append(",");
                } else {
                    sb.append(" |");
                }
            }
            if (++refIdx == refSize) {
                sb.append("];");
            }
            sb.append(String.format(" %% %d: %s", refIdx, referent.getFullName()));
        }
        sb.append("\n\n");
    }


    @Transactional
    public void speicherePlanungsergebnis(Long veranstaltungId, String jsonErgebnis, SolverConfig config,
                                          DznVorbereitung vorbereitung) {
        Veranstaltung veranstaltung = Veranstaltung.findById(veranstaltungId);
        Planungsergebnis ergebnis = Planungsergebnis.find("veranstaltung", veranstaltung).firstResult();
        if (null == ergebnis) {
            ergebnis = new Planungsergebnis();
            ergebnis.setVeranstaltung(veranstaltung);
        }
        LOG.info("Speichere Planungsergebnis für Veranstaltung: " + veranstaltung.getName());
        try {
            ObjectNode root = (ObjectNode) objectMapper.readTree(jsonErgebnis);
            addOidsFromVorbereitung(vorbereitung, root);

            fixflatArrays(root,
                vorbereitung.teilnehmer_oids.size(),
                vorbereitung.wahlvortrag_oids.size(),
                config.getMaxInstanzen());

            Planungsergebnis.MinizincResult result =
                objectMapper.treeToValue(root, Planungsergebnis.MinizincResult.class);

            if (config.isAuffuellen()) {
                auffuellungService.fuelleAuf(veranstaltung, result, config.getMaxWvsProTn());
            }

            String fixedJson = result.toJson(objectMapper);
            LOG.info("###" + fixedJson);

            ergebnis.setJsonErgebnis(fixedJson);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }

        ergebnis.setSolverConfig(config);
        // Eine erfolgreiche Neu-Erstellung sieht nur noch die aktuell existierenden Wahlvortraege
        // und behebt damit jede vorherige Inkonsistenz durch einen zwischenzeitlich
        // zurueckgezogenen Vortrag (siehe NachrichtService#benachrichtigeUeberZurueckgezogenenVortrag).
        ergebnis.setVeraltet(false);
        ergebnis.persistAndFlush();

        LOG.info("Planungsergebnis für Veranstaltung '" + veranstaltung.getName() + "' wurde gespeichert/aktualisiert.");
    }


    private void addOidsFromVorbereitung(DznVorbereitung vorbereitung, ObjectNode root) {
        root.set("teilnehmer_oids", objectMapper.valueToTree(vorbereitung.teilnehmer_oids));
        root.set("wahlvortrag_oids", objectMapper.valueToTree(vorbereitung.wahlvortrag_oids));
        root.set("slot_oids", objectMapper.valueToTree(vorbereitung.slot_oids));
        root.set("raum_oids", objectMapper.valueToTree(vorbereitung.raum_oids));
    }


    public static boolean isValidJson(String json) {
        if (StringUtils.isBlank(json)) {
            return false;
        }
        try (var reader = Json.createReader(new StringReader(json))) {
            reader.readObject();
            return true;
        } catch (JsonException | ClassCastException e) {
            return false;
        }
    }

    // -------------------------------------------------------------------
    // Helper methods
    // -------------------------------------------------------------------


    public void restructure2DArray(ObjectNode root, String fieldName,
                                   int rows, int cols) {
        JsonNode flat = root.get(fieldName);
        if (null == flat || !flat.isArray()) {
            return;
        }

        // Flaches Array → 2D Array
        ArrayNode matrix = objectMapper.createArrayNode();

        for (int i = 0; i < rows; i++) {
            ArrayNode row = objectMapper.createArrayNode();
            for (int j = 0; j < cols; j++) {
                row.add(flat.get(i * cols + j));
            }
            matrix.add(row);
        }

        root.set(fieldName, matrix);
    }


    public void restructure3DArray(ObjectNode root, String fieldName,
                                   int dim1, int dim2, int dim3) {
        JsonNode flat = root.get(fieldName);
        if (null == flat || !flat.isArray()) {
            return;
        }

        // Flaches Array → 3D Array [dim1][dim2][dim3]
        ArrayNode matrix3D = objectMapper.createArrayNode();

        for (int i = 0; i < dim1; i++) {
            ArrayNode matrix2D = objectMapper.createArrayNode();
            for (int j = 0; j < dim2; j++) {
                ArrayNode row = objectMapper.createArrayNode();
                for (int k = 0; k < dim3; k++) {
                    int flatIndex = i * (dim2 * dim3) + j * dim3 + k;
                    row.add(flat.get(flatIndex));
                }
                matrix2D.add(row);
            }
            matrix3D.add(matrix2D);
        }

        root.set(fieldName, matrix3D);
    }


    private void fixflatArrays(ObjectNode root, int nTNs, int nWVs, int maxInstanzen) {
        restructure2DArray(root, "instanz_slot", nWVs, maxInstanzen);
        restructure2DArray(root, "instanz_raum", nWVs, maxInstanzen);
        restructure3DArray(root, "besucht", nTNs, nWVs, maxInstanzen);
    }
}
