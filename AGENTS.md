# AGENTS.md – KonfPlan (Root)

## Projektübersicht

Der **KonfPlan** ist eine Webanwendung zur Planung und Verwaltung von Veranstaltungen mit Vorträgen (z. B. Schulungstage). Er unterstützt drei Nutzerrollen: Admin, Referent und Teilnehmer. Ein zentrales Feature ist die automatische PlanErstellung der Teilnehmerzuweisung zu Wahlvorträgen via **MiniZinc**.

## Struktur

```
konfplan/
├── backend/          # Quarkus 3.35.1, Java 21, PostgreSQL (Dev/Prod), H2 (Test)
├── frontend/         # Vue 3 + Vite + Tailwind CSS
└── pom.xml           # Maven Multi-Module Parent
```

## Build & Start

```bash
# Alles bauen (Root)
./mvnw clean install -DskipTests

# Backend starten (Dev-Modus mit Hot-Reload + Frontend via Quinoa)
cd backend && ../mvnw quarkus:dev
# → Backend: http://localhost:9000
# → Frontend Dev-Server: http://localhost:5173 (wird automatisch gestartet)

# Nur Backend-Tests
cd backend && ../mvnw test

# Nur Frontend
cd frontend && npm install && npm run dev
```

## Schlüsseltechnologien

| Schicht      | Technologie                                       |
|--------------|---------------------------------------------------|
| Backend      | Quarkus 3.35.1, Java 21, RESTEasy Reactive        |
| Architektur  | Hexagonal (Ports & Adapters)                      |
| ORM          | Hibernate ORM Panache (Active Record Pattern)     |
| Datenbank    | PostgreSQL (Prod/Dev), H2 (Test)                  |
| Migration    | Flyway                                            |
| Security     | JWT (SmallRye), BCrypt, `quarkus-security-jpa`    |
| PlanErstellung  | MiniZinc (externer Prozess via `PlanErstellungService`) |
| CSV-Import   | OpenCSV 5.11.2                                    |
| PDF-Export   | OpenPDF 2.0.3                                     |
| E-Mail       | Quarkus Mailer (Mailpit für Dev)                  |
| Frontend     | Vue 3, Vite, Tailwind CSS, Pinia, Vue Router      |
| Integration  | Quarkus Quinoa (Frontend-Build eingebettet)       |
| E2E-Tests    | Playwright                                        |

## Domänenmodell (Kurzübersicht)

- **Veranstaltung** – Zentrale Entität; hat EventSlots, Gebäude, Nutzer; besitzt Deadlines für Referenten/Teilnehmer.
- **Nutzer** (SINGLE_TABLE) → Admin | Referent | Teilnehmer
- **Vortrag** (SINGLE_TABLE) → Pflichtvortrag | Wahlvortrag
- **EventSlot** – Zeitfenster innerhalb einer Veranstaltung; mit Überschneidungsprüfung.
- **Zuweisung** – Ordnet Teilnehmer einem Vortrag + Slot + Raum zu.
- **Prioritaet** – Präferenz eines Teilnehmers für einen Wahlvortrag (Ranking 1-10).
- **Verfuegbarkeit** – Gibt an, ob Nutzer in einem Slot verfügbar ist (Default: true bei Zuweisung).
- **RaumVerfuegbarkeit** – Modelliert die Verfügbarkeit von Räumen pro Slot inklusive veranstaltungsübergreifender Prüfung.
- **AdminPrioritaetUpdateRequestDto** - DTO für die Aktualisierung einer einzelnen Teilnehmerpriorität durch Administratoren.

## Wichtige Konventionen

- Alle Entitäten erben von `VersionedEntity` (Panache Active Record, `Long id`, `@Version Long version`).
- Polymorphe Typen nutzen `@Inheritance(SINGLE_TABLE)` + Jackson `@JsonSubTypes`.
- Datenbankfelder: Public Fields (kein Lombok), kein privater Getter/Setter-Boilerplate außer wo nötig.
- Datum/Zeit: `LocalDateTime` mit Custom `LocalDateTimeConverter`.
- Fehlerbehandlung: `CustomExceptionMapper` mappt Exceptions auf HTTP-Responses.
- Alle REST-Endpunkte unter `/api/...`; Security via `@RolesAllowed`.
- Neuer Admin-Endpunkt: `PUT /api/admin/veranstaltungen/{vid}/teilnehmer/{tid}/priorities` zur individuellen Aktualisierung von Teilnehmerprioritäten.

## Bekannte Besonderheiten

- MiniZinc muss auf dem System installiert sein (`minizinc` im PATH) für `PlanErstellungService`.
- Deadlines verhindern Eingaben/Änderungen für Referenten und Teilnehmer nach Ablauf.
- Räume werden veranstaltungsübergreifend auf Überschneidungen geprüft.
- Passwort-Reset per E-Mail (Mailpit-Credentials in `application.properties` setzen).
- Standard-Passwort bei Erstellung/Import: `start123` (BCrypt-gehasht).

## Arbeitsanweisungen für den Agenten

### Checkliste: Full-Stack-Feature-Slice bei Datenmodell-Änderungen

Wenn eine Änderung am Datenmodell als "Full-Stack-Feature-Slice" angefordert wird, sind die folgenden Schritte durchzuführen:

1.  **Persistenz-Schicht (Backend):**
    *   **Entität anpassen:** Das neue Feld zur entsprechenden JPA-Entitätsklasse hinzufügen (z.B. `Vortrag.java`).
    *   **Enum erstellen:** Falls das neue Feld ein Enum ist, die `enum`-Klasse anlegen (z.B. `Berufsfeld.java`).
    *   **Datenbank-Migration:** Ein neues Flyway-Migrationsskript (`V_... .sql`) erstellen, um das Datenbankschema mit `ALTER TABLE ... ADD COLUMN ...` zu aktualisieren.

2.  **Datenübertragungs-Schicht (Backend):**
    *   **DTO anpassen:** Die entsprechende(n) DTO-Klasse(n) (z.B. `VortragDto.java`) um das neue Feld erweitern.
    *   **Mapper-Logik aktualisieren:** Die Methoden, die Entitäten in DTOs umwandeln, anpassen, um das neue Feld zu berücksichtigen (z.B. in `ReferentService.mapVortragToDto`).

3.  **Service- & Business-Logik (Backend):**
    *   **Importer anpassen:** Falls es einen CSV-Importer gibt, die Logik erweitern (z.B. in `AdminService.importVortraegeFromCsv`).
    *   **Erstellungs-/Update-Logik:** Die `create...`- und `update...`-Methoden in den relevanten Services anpassen.

4.  **Test-Schicht (Backend):**
    *   **Test-Daten anpassen:** Bestehende Test-Daten-Generatoren (z.B. `DevDataInitService`) oder CSV-Dateien im `test/resources`-Verzeichnis erweitern.
    *   **Testfälle erweitern:** Bestehende Unit- und Integrationstests (`*Test.java`) anpassen, um das neue Feld zu berücksichtigen.

5.  **Präsentations-Schicht (Frontend):**
    *   **Anzeige-Komponenten:** Vue-Komponenten, die die Daten anzeigen (z.B. `VortraegeTab.vue`), erweitern.
    *   **Bearbeitungs-Komponenten:** Vue-Komponenten, die zum Erstellen oder Bearbeiten verwendet werden (z.B. `AdminVortragEditorModal.vue`), um ein neues Eingabefeld erweitern.
    *   **Daten-Handling im Frontend:** Das reaktive `form`-Objekt und die `save`-Methoden im Frontend anpassen.