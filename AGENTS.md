# AGENTS.md – Vortragsmanager (Root)

## Projektübersicht

Der **Vortragsmanager** ist eine Webanwendung zur Planung und Verwaltung von Veranstaltungen mit Vorträgen (z. B. Schulungstage). Er unterstützt drei Nutzerrollen: Admin, Referent und Teilnehmer. Ein zentrales Feature ist die automatische Optimierung der Teilnehmerzuweisung zu Wahlvorträgen via **MiniZinc**.

## Struktur

```
vortragsmanager/
├── backend/          # Quarkus 3.20.1, Java 21, SQLite
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
| Backend      | Quarkus 3.20.1, Java 21, RESTEasy Reactive        |
| ORM          | Hibernate ORM Panache (Active Record Pattern)     |
| Datenbank    | SQLite via `quarkus-jdbc-sqlite4j`                |
| Migration    | Flyway (`V1__tables.sql`)                           |
| Security     | JWT (SmallRye), BCrypt, `quarkus-security-jpa`    |
| Optimierung  | MiniZinc (externer Prozess via `OptimierungService`) |
| CSV-Import   | OpenCSV 5.11.2                                    |
| PDF-Export   | OpenPDF 2.0.3                                     |
| E-Mail       | Quarkus Mailer (Mailtrap für Dev)                 |
| Frontend     | Vue 3, Vite, Tailwind CSS, Pinia, Vue Router      |
| Integration  | Quarkus Quinoa (Frontend-Build eingebettet)       |
| E2E-Tests    | Playwright                                        |

## Domänenmodell (Kurzübersicht)

- **Veranstaltung** – zentrale Entität; hat EventSlots, Gebäude, User
- **User** (SINGLE_TABLE) → Admin | Referent | Teilnehmer
- **Vortrag** (SINGLE_TABLE) → Pflichtvortrag | Wahlvortrag
- **EventSlot** – Zeitfenster innerhalb einer Veranstaltung
- **Zuweisung** – ordnet Teilnehmer einem Vortrag + Slot + Raum zu
- **Prioritaet** – Präferenz eines Teilnehmers für einen Wahlvortrag
- **Verfuegbarkeit** – gibt an, wann ein Teilnehmer/Raum verfügbar ist

## Wichtige Konventionen

- Alle Entitäten erben von `SqliteEntity` (Panache Active Record, `Long id`)
- Polymorphe Typen nutzen `@Inheritance(SINGLE_TABLE)` + Jackson `@JsonSubTypes`
- Datenbankfelder: Public Fields (kein Lombok), kein privater Getter/Setter-Boilerplate außer wo nötig
- Optimistic Locking: `@Version Long version` in allen Entitäten
- Datum/Zeit: `LocalDateTime` mit Custom `LocalDateTimeConverter` (ISO-Format als String in SQLite)
- Fehlerbehandlung: `CustomExceptionMapper` mappt Exceptions auf HTTP-Responses
- Alle REST-Endpunkte unter `/api/...`; Security via `@RolesAllowed`

## Bekannte Besonderheiten

- SQLite erlaubt nur **max. 2 gleichzeitige JDBC-Verbindungen** → keine parallelen Schreiboperationen
- MiniZinc muss auf dem System installiert sein (`minizinc` im PATH) für `OptimierungService`
- Flyway-Migration muss manuell aktiviert werden (`quarkus.flyway.migrate-at-start=true`)
- Passwort-Reset per E-Mail (Mailtrap-Credentials in `application.properties` setzen)
- Standard-Passwort bei CSV-Import: `start123` (BCrypt-gehasht)
