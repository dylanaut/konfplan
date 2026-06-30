# AGENTS.md – dto/

## Zweck

DTOs (Data Transfer Objects) entkoppeln die API-Schicht von den JPA-Entitäten. Sie sind einfache Java-Klassen ohne Geschäftslogik.

## DTO-Kategorien

### API-DTOs (Request/Response)
| DTO                   | Zweck                                              |
|-----------------------|----------------------------------------------------|
| `UserDto`             | User-Daten (alle Rollen, role-Feld unterscheidet)  |
| `VeranstaltungDto`    | Veranstaltungs-Daten inkl. Gebäude-IDs             |
| `GebaeudeSimpleDto`   | Gebäude ohne Räume (für Listen)                    |
| `RefProfilDto`        | Referenten-Profil (öffentlich sichtbar)            |
| `RefVortragDto`       | Vortrag aus Referenten-Perspektive                 |
| `VortragBelegungDto`  | Belegungsstatus eines Vortrags                     |
| `VortragStatDto`      | Statistik (Anmeldezahlen etc.) für einen Vortrag   |
| `ZuweisungDto`        | Zuweisung Teilnehmer ↔ Vortrag ↔ Slot ↔ Raum      |
| `RaumBelegungDto`     | Welcher Vortrag ist wann in welchem Raum           |
| `PlanQualitaetDto`    | Qualitätsmetriken des PlanErstellungsergebnisses      |
| `LoginRequest`        | E-Mail + Passwort für Login                        |
| `TokenResponse`       | JWT-Token als Antwort auf Login                    |
| `ResetRequest`        | Token + neues Passwort für Passwort-Reset          |
| `SolverConfigDto`     | Konfiguration für MiniZinc-Solver (Timeout etc.)  |
| `ImportResultDto`     | Ergebnis eines CSV-Imports (Anzahl + Fehler)       |
| `FileUploadDto`       | Wrapper für Datei-Upload-Endpunkte                 |

### CSV-Import-DTOs (OpenCSV-Mapping)
| DTO                    | Trennzeichen | Zweck                          |
|------------------------|--------------|--------------------------------|
| `TeilnehmerCsvDto`     | `;`          | Teilnehmer aus CSV importieren |
| `ReferentCsvDto`       | `;`          | Referenten aus CSV importieren |
| `VortragCsvDto`        | `;`          | Vorträge aus CSV importieren   |
| `VeranstaltungCsvDto`  | `;`          | Veranstaltungen importieren    |
| `GebaeudeRaeumeCsvDto` | `;`          | Gebäude+Räume importieren      |
| `RaumCsvDto`           | `;`          | Räume einzeln importieren      |
| `EventSlotCsvDto`      | `;`          | Zeitslots importieren          |
| `AdminCsvDto`          | `;`          | Admins/Veranstalter importieren |
| `VortragPrioDto`    | –            | Priorität setzen (POST-Body)   |

## Konventionen

```java
// Standard-API-DTO (Public Fields, kein Lombok)
public class MeinDto {
    public Long id;
    public String name;
    // ...
}

// CSV-DTO mit OpenCSV-Annotation
public class MeinCsvDto {
    @CsvBindByName(column = "Spaltenname")
    public String feldname;
    // ...
}
```

- **Public Fields** ohne Getter/Setter (außer wenn Jackson es explizit braucht)
- Kein Lombok (Projekt verwendet es nicht)
- CSV-DTOs haben `@CsvBindByName(column = "...")` mit dem exakten Spaltennamen aus der CSV-Datei
- CSV-Trennzeichen ist immer **Semikolon** (`;`)
- `ImportResultDto` enthält: Erfolgsmeldung (String) + Liste von Fehlermeldungen (List<String>)
- Null-Werte erlaubt – Pflichtfelder werden im Service oder in der Entität validiert
- Zirkuläre Referenzen vermeiden: Nested DTOs statt Entitäts-Referenzen
