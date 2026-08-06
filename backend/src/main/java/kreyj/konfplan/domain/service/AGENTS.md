# AGENTS.md – service/

## Zweck

Services enthalten die gesamte **Geschäftslogik**. Sie werden von Resource-Klassen aufgerufen und kommunizieren direkt mit den Panache-Entitäten. In den Services werden komplexe Validierungen (z. B. Deadline-Prüfungen, Slot-Überschneidungen) durchgeführt.

## Vorhandene Services (Kernverantwortlichkeiten)

| Service               | Verantwortlichkeit                                                   |
|-----------------------|----------------------------------------------------------------------|
| `AdminService`        | CRUD für Nutzer, Vorträge und Slots; **CSV-Import** (Nutzer, Vorträge, Verfügbarkeiten); **Slot-Validierung**. |
| `ReferentService`     | Referenten-Profil und Vorträge; **Deadline-Prüfung** für Referenten. |
| `TeilnehmerService`   | Verwaltung von Teilnehmern und Gruppen; Bereitstellung von Dashboard-Daten. |
| `PrioritaetService`   | Speichern von Teilnehmer-Präferenzen; **Deadline-Prüfung** für Teilnehmer. |
| `VeranstaltungService`| Zentrale Verwaltung von Events und deren Metadaten; Bereitstellung von Vortragsübersichten. |
| `PlanErstellungService`  | MiniZinc-basierte Zuweisung von Teilnehmern zu Wahlvorträgen.        |
| `PlanService`         | Stundenplan-Erstellung und Qualitätsberechnung der Zuweisung.        |
| `DashboardService`    | Aufbereitung von komplexen, aggregierten Daten (inkl. `DashboardData`-Aufbau) für die `ReportResource`-Dashboards. |
| `MailService`         | Versand von Einladungen, Benachrichtigungen und Passwort-Resets via Mailpit. |
| `PdfService`          | Erzeugung von Türschildern und Plänen via OpenPDF.                   |
| `LoginRateLimiterService` | IP-basiertes Rate-Limiting für `/api/auth/login` (siehe `../../../../AGENTS.md` „Security"). |
| `ForgotPasswordRateLimiterService` | IP-basiertes Rate-Limiting für `/api/auth/forgot-password` (siehe `../../../../AGENTS.md` „Security"). |
| `TokenInvalidationService` | Merkt sich pro Anmeldename, ab wann ausgestellte JWTs nach einem Passwort-Reset ungültig sind (siehe `../../../../AGENTS.md` „Security"). |

## Wichtige Logik-Features

### Slot-Validierung (`AdminService`)
Die Methode `validateSlot` stellt sicher, dass:
- Beginn und Ende zeitlich korrekt aufeinanderfolgen.
- Der Slot innerhalb des Veranstaltungszeitraums liegt.
- Keine zeitlichen Überschneidungen mit anderen Slots derselben Veranstaltung existieren.

### Deadline-Steuerung
- `ReferentService`: Prüft `v.deadlineReferenten` vor Änderungen an Vorträgen oder Verfügbarkeiten.
- `TeilnehmerService` / `PrioritaetService`: Prüfen `v.deadlineTeilnehmer` vor dem Speichern von Prioritäten oder Verfügbarkeiten.

### Verfügbarkeits-Management
- **Nutzer**: Beim Zuweisen eines Nutzers zu einer Veranstaltung werden in `Nutzer.addVeranstaltung` automatisch `Verfuegbarkeit`-Einträge für alle Slots erstellt (Default: `true`).
- **Räume**: Die Raumverfügbarkeit wird veranstaltungsübergreifend geprüft, um Doppelbelegungen desselben Raums zur gleichen Zeit in verschiedenen Events zu verhindern.
- **CSV-Import**: `AdminService` bietet Methoden zum Import von Nutzer- und Raumverfügbarkeiten anhand von 1-basierten Slot-Indizes.

## MiniZinc-PlanErstellung
Der `PlanErstellungService` exportiert die Daten in eine temporäre `.dzn`-Datei und ruft den MiniZinc-Solver auf. Er verarbeitet das JSON-Ergebnis und erzeugt die `Zuweisung`-Entitäten.

## Regeln & Konventionen
- Schreibende Methoden benötigen **immer** `@Transactional`.
- Komplexe DTO-Mappings können in statischen Methoden der Resource-Klassen liegen, um die Services sauber zu halten.
- Logging erfolgt über `org.jboss.logging.Logger`.
