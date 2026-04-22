# AGENTS.md – backend/

## Überblick

Das Backend ist eine **Quarkus 3.20.1**-Anwendung (Java 21) mit RESTful API, JWT-Security und SQLite-Datenbank. Es folgt einer klassischen Dreischicht-Architektur: `resource` → `service` → `entity`.

## Paketstruktur

```
src/main/java/kreyj/vortragsmanager/
├── entity/       # JPA-Entitäten (Panache Active Record)
├── dto/          # Datentransferobjekte (Request/Response)
├── service/      # Geschäftslogik (@ApplicationScoped)
├── resource/     # JAX-RS REST-Endpunkte (@Path)
└── util/         # Querschnittsklassen (z. B. ExceptionMapper)

src/main/resources/
├── application.properties
├── db/migration/V1__tables.sql   # Flyway-Schema
├── minizinc/vortragsplanung.mzn  # Optimierungsmodell
├── templates/                  # Freemarker-Templates (E-Mail)
└── assets/                     # Statische Dateien (Logo)

src/test/java/kreyj/vortragsmanager/resource/  # Integrationstests
src/test/resources/csv_import/bo_26_09/         # Reale Testdaten (CSV)
```

## Entwicklung

```bash
# Dev-Modus starten (Hot-Reload)
cd backend && ../mvnw quarkus:dev

# Tests ausführen
cd backend && ../mvnw test

# Produktions-Build
cd backend && ../mvnw package
```

## Konfiguration (`application.properties`)

- Port: **9000**
- CORS für Frontend-Dev auf Port **5173** aktiviert
- SQLite-Datei: `vortragsmanager.db` (im Arbeitsverzeichnis)
- JWT: RSA-Schlüsselpaar in `src/main/resources/` (PEM-Dateien)
- Hibernate: `validate` (Schema wird NICHT automatisch angepasst → Flyway)
- Flyway: Standardmäßig **deaktiviert** (`migrate-at-start=false`), für Dev manuell aktivieren

## Architekturregeln

1. **Resource-Klassen** haben keine Geschäftslogik – nur HTTP-Mapping und Delegation an Services
2. **Service-Klassen** sind `@ApplicationScoped`; Transaktionen mit `@Transactional`
3. **Entities** nutzen Panache Active Record (statische Finder-Methoden direkt auf der Klasse)
4. Neue Entitäten **müssen** von `SqliteEntity` erben
5. Neue Entitäten **müssen** `@Version Long version` für Optimistic Locking enthalten
6. Für SQLite gilt: **kein `GenerationType.SEQUENCE`** – immer `GenerationType.IDENTITY` + `columnDefinition = "INTEGER"`
7. Datums-/Zeitfelder: `LocalDateTime` + `@Convert(converter = LocalDateTimeConverter.class)`

## Security

- Drei Rollen: `ADMIN`, `REFERENT`, `TEILNEHMER`
- Alle Endpunkte mit `@RolesAllowed` absichern
- JWT-Token wird von `AuthResource` ausgestellt (RSA-signiert)
- Passwörter: BCrypt via `BcryptUtil.bcryptHash()`

## SQLite-Besonderheiten

- Max. **2 JDBC-Verbindungen** gleichzeitig (Konfiguration beachten!)
- `foreign_keys=true` und `busy_timeout=30000` als JDBC-Properties gesetzt
- Keine DDL-Autogeneration durch Hibernate – Schema nur über Flyway-Migrationen ändern
- Für neue Felder/Tabellen: neue Flyway-Datei `V2__beschreibung.sql` anlegen

## MiniZinc-Optimierung

Der `OptimierungService` ruft MiniZinc als externen Prozess auf:
- Modell: `src/main/resources/minizinc/vortragsplanung.mzn`
- MiniZinc muss auf dem System installiert sein (`minizinc` im PATH)
- Der Service schreibt temporäre `.dzn`-Datendateien und liest JSON-Output
- Timeouts und Solver-Konfiguration über `SolverConfigDto`
