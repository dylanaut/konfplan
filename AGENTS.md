# AGENTS.md – KonfPlan (Root)

## Projektübersicht

Der **KonfPlan** ist eine Webanwendung zur Planung und Verwaltung von Veranstaltungen mit Vorträgen (z. B. Schulungstage). Er unterstützt drei Nutzerrollen: Admin, Referent und Teilnehmer. Ein zentrales Feature ist die automatische Optimierung der Teilnehmerzuweisung zu Wahlvorträgen via **MiniZinc**.

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
| Optimierung  | MiniZinc (externer Prozess via `OptimierungService`) |
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

- MiniZinc muss auf dem System installiert sein (`minizinc` im PATH) für `OptimierungService`.
- Deadlines verhindern Eingaben/Änderungen für Referenten und Teilnehmer nach Ablauf.
- Räume werden veranstaltungsübergreifend auf Überschneidungen geprüft.
- Passwort-Reset per E-Mail (Mailpit-Credentials in `application.properties` setzen).
- Standard-Passwort bei Erstellung/Import: `start123` (BCrypt-gehasht).