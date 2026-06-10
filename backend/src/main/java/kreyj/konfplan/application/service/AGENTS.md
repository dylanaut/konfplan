# AGENTS.md – service/

## Zweck

Services enthalten die gesamte **Geschäftslogik**. Sie werden von Resource-Klassen aufgerufen und kommunizieren direkt mit den Panache-Entitäten. In den Services werden komplexe Validierungen (z. B. Deadline-Prüfungen, Slot-Überschneidungen) durchgeführt.

## Vorhandene Services (Kernverantwortlichkeiten)

| Service               | Verantwortlichkeit                                                   |
|-----------------------|----------------------------------------------------------------------|
| `AdminService`        | CRUD für Nutzer, Vorträge und Slots; **Slot-Validierung** (Überschneidungsprüfung). |
| `ReferentService`     | Referenten-Profil und Vorträge; **Deadline-Prüfung** für Referenten. |
| `TeilnehmerService`   | Verwaltung von Teilnehmern und Gruppen.                              |
| `PrioritaetService`   | Speichern von Teilnehmer-Präferenzen; **Deadline-Prüfung** für Teilnehmer. |
| `VeranstaltungService`| Zentrale Verwaltung von Events und deren Metadaten (Logo, Termine).  |
| `PlanErstellungService`  | MiniZinc-basierte Zuweisung von Teilnehmern zu Wahlvorträgen.        |
| `PlanService`         | Stundenplan-Erstellung und Qualitätsberechnung der Zuweisung.        |
| `MailService`         | Versand von Einladungen, Benachrichtigungen und Passwort-Resets via Mailpit. |
| `PdfService`          | Erzeugung von Türschildern und Plänen via OpenPDF.                   |

## Wichtige Logik-Features

### Slot-Validierung (`AdminService`)
Die Methode `validateSlot` stellt sicher, dass:
- Beginn und Ende zeitlich korrekt aufeinanderfolgen.
- Der Slot innerhalb des Veranstaltungszeitraums liegt.
- Keine zeitlichen Überschneidungen mit anderen Slots derselben Veranstaltung existieren.

### Deadline-Steuerung
- `ReferentService`: Prüft `v.deadlineReferenten` vor Änderungen an Vorträgen oder Verfügbarkeiten.
- `PrioritaetService`: Prüft `v.deadlineTeilnehmer` vor dem Speichern von Prioritäten.

### Verfügbarkeits-Management
- **Nutzer**: Beim Zuweisen eines Nutzers zu einer Veranstaltung werden in `Nutzer.addVeranstaltung` automatisch `Verfuegbarkeit`-Einträge für alle Slots erstellt (Default: `true`).
- **Räume**: Die Raumverfügbarkeit wird veranstaltungsübergreifend geprüft, um Doppelbelegungen desselben Raums zur gleichen Zeit in verschiedenen Events zu verhindern.

## MiniZinc-PlanErstellung
Der `PlanErstellungService` exportiert die Daten in eine temporäre `.dzn`-Datei und ruft den MiniZinc-Solver auf. Er verarbeitet das JSON-Ergebnis und erzeugt die `Zuweisung`-Entitäten.

## Regeln & Konventionen
- Schreibende Methoden benötigen **immer** `@Transactional`.
- Komplexe DTO-Mappings können in statischen Methoden der Resource-Klassen liegen, um die Services sauber zu halten.
- Logging erfolgt über `org.jboss.logging.Logger`.
