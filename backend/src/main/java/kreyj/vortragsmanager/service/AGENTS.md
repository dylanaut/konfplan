# AGENTS.md – service/

## Zweck

Services enthalten die gesamte **Geschäftslogik**. Sie werden von Resource-Klassen aufgerufen und kommunizieren direkt mit den Panache-Entitäten. Services arbeiten ausschließlich mit DTOs als Ein- und Ausgabe.

## Vorhandene Services

| Service                 | Verantwortlichkeit                                                   |
|-------------------------|----------------------------------------------------------------------|
| `AdminService`          | CRUD für alle User-Typen; CSV-Import von Teilnehmer, Referenten etc. |
| `VeranstaltungService`  | CRUD für Veranstaltungen, EventSlots, Vorträge; CSV-Import           |
| `ReferentService`       | Referenten-Profil, Vortragsliste für Referent-Dashboard              |
| `TeilnehmerService`     | Prioritäten setzen, persönlicher Plan eines Teilnehmers              |
| `GebaeudeService`       | CRUD für Gebäude und Räume; CSV-Import                               |
| `RaumService`           | Raum-Verfügbarkeiten verwalten                                       |
| `OptimierungService`    | MiniZinc-basierte Zuweisung von Teilnehmern zu Wahlvorträgen         |
| `PlanService`           | Planqualität berechnen, Zuweisungen auslesen/exportieren             |
| `PrioritaetService`     | Prioritäten eines Teilnehmers verwalten                              |
| `ZuweisungService`      | Zuweisungen lesen und anpassen                                       |
| `PdfService`            | PDF-Export des Stundenplans via OpenPDF                              |

## Pflichtstruktur für neue Services

```java
@ApplicationScoped
public class MeinService {

    private static final Logger LOG = Logger.getLogger(MeinService.class);

    // Andere Services per @Inject einbinden (kein new)
    @Inject
    AndererService andererService;

    // Lesende Methoden: KEINE @Transactional nötig (Panache öffnet eigene Tx)
    public List<MeinDto> listAll() {
        return MeineEntitaet.<MeineEntitaet>listAll()
                .stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    // Schreibende Methoden: IMMER @Transactional
    @Transactional
    public MeinDto create(MeinDto dto) {
        MeineEntitaet entity = new MeineEntitaet();
        // ... Felder setzen
        entity.persist();
        return mapToDto(entity);
    }

    // DTO-Mapping: private Hilfsmethoden
    private MeinDto mapToDto(MeineEntitaet entity) {
        MeinDto dto = new MeinDto();
        dto.id = entity.id;
        // ...
        return dto;
    }
}
```

## Regeln & Konventionen

- `@ApplicationScoped` ist der Standard-Scope; kein `@RequestScoped` ohne triftigen Grund
- **Alle Schreiboperationen** (persist, delete, update) benötigen `@Transactional`
- Services geben **niemals** Entitäten direkt zurück – immer DTOs (verhindert lazy-loading-Probleme und ungewollte Serialisierung)
- Fehler werden als `jakarta.ws.rs.core.Response` zurückgegeben oder als Exception geworfen (→ `CustomExceptionMapper`)
- Logging: `Logger.getLogger(MeinService.class)` von `org.jboss.logging`
- CSV-Import-Logik gehört in Services, **nicht** in Resource-Klassen
- Bei `@Transactional`-Methoden: Exception nicht schlucken, sonst wird Rollback verhindert

## OptimierungService – Besonderheiten

- Ruft `minizinc` als externen Prozess auf (`ProcessBuilder`)
- Schreibt temporäre `.dzn`-Datei in `Files.createTempFile()`
- Liest JSON-Output des Solvers und persistiert `Zuweisung`-Entitäten
- Pflichtvorträge werden **vorab** direkt zugewiesen (nicht durch MiniZinc)
- Timeouts konfigurierbar via `SolverConfigDto`
- MiniZinc muss auf dem System installiert und im PATH verfügbar sein

## CSV-Import-Pattern

```java
@Transactional
public ImportResultDto importCsv(InputStream inputStream, Long veranstaltungId) {
    try (Reader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8)) {
        CsvToBean<MeinCsvDto> csvToBean = new CsvToBeanBuilder<MeinCsvDto>(reader)
                .withType(MeinCsvDto.class)
                .withSeparator(';')
                .withIgnoreLeadingWhiteSpace(true)
                .build();

        List<MeinCsvDto> rows = csvToBean.parse();
        int count = 0;
        for (MeinCsvDto row : rows) {
            // ... Entität anlegen
            count++;
        }
        return new ImportResultDto(count + " Einträge importiert", List.of());
    } catch (Exception e) {
        LOG.error("CSV-Import fehlgeschlagen", e);
        throw new RuntimeException("Import fehlgeschlagen: " + e.getMessage());
    }
}
```
CSV-Trennzeichen ist **Semikolon** (`;`), Spaltennamen via `@CsvBindByName(column = "...")` in DTO-Klassen.
