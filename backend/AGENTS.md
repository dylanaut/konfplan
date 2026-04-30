# AGENTS.md – backend/

## Überblick

Das Backend ist eine **Quarkus 3.33.1**-Anwendung (Java 21) mit RESTful API, JWT-Security und einer PostgreSQL-Datenbank für Dev/Prod sowie H2 für Tests. Es folgt einer klassischen Dreischicht-Architektur: `resource` → `service` → `entity`.

## Paketstruktur

```
src/main/java/kreyj/vortragsmanager/
├── entity/       # JPA-Entitäten (Panache Active Record)
├── dto/          # Datentransferobjekte (Request/Response)
├── service/      # Geschäftslogik (@ApplicationScoped)
├── resource/     # JAX-RS REST-Endpunkte (@Path)
└── util/         # Querschnittsklassen (z. B. LoggingFilter)

src/main/resources/
├── application.properties
├── db/migration/   # Flyway-Schema
├── minizinc/vortragsplanung.mzn  # Optimierungsmodell
├── templates/                  # Freemarker-Templates (E-Mail)
└── assets/                     # Statische Dateien
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
- Datenbank: PostgreSQL (Docker Dev Services im Dev-Modus, konfiguriert für Prod)
- Test-Datenbank: H2 In-Memory
- JWT: RSA-Schlüsselpaar in `src/main/resources/` (PEM-Dateien)
- Hibernate: `drop-and-create` (Dev/Test) bzw. `update` (Prod)
- Flyway: Zur Schema-Migration genutzt.

## Architekturregeln

1. **Resource-Klassen** haben keine Geschäftslogik – nur HTTP-Mapping, Delegation an Services und Mapping zu DTOs.
2. **Service-Klassen** sind `@ApplicationScoped`; Transaktionen mit `@Transactional`.
3. **Entities** nutzen Panache Active Record (statische Finder-Methoden direkt auf der Klasse).
4. Neue Entitäten **müssen** von `VersionedEntity` erben.
5. Neue Entitäten **müssen** `@Version Long version` für Optimistic Locking enthalten.
6. Datums-/Zeitfelder: `LocalDateTime` + `@Convert(converter = LocalDateTimeConverter.class)`.

## Security

- Rollen: `ADMIN`, `REFERENT`, `TEILNEHMER`.
- Alle Endpunkte mit `@RolesAllowed` oder `@Authenticated` absichern.
- JWT-Token wird von `AuthResource` ausgestellt.
- Passwörter: BCrypt via `BcryptUtil.bcryptHash()`.

## Geschäftslogik-Highlights

- **Slot-Validierung**: Prüfung auf Überschneidungsfreiheit und zeitliche Korrektheit bei Erstellung/Update von EventSlots.
- **Verfügbarkeiten**: Automatische Erstellung von Standard-Verfügbarkeiten (true) beim Hinzufügen von Nutzern zu Veranstaltungen.
- **Raum-Management**: Veranstaltungsübergreifende Prüfung der Raumverfügbarkeit zur Vermeidung von Doppelbelegungen.
- **Deadlines**: Referenten und Teilnehmer können ihre Daten nur bis zu einem administrativ festgelegten Zeitpunkt ändern.
- **Prioritäten-Management (Admin)**: Admins können individuelle Prioritäten für Teilnehmer an Wahlvorträgen über den Endpunkt `/api/admin/veranstaltungen/{vid}/teilnehmer/{tid}/priorities` aktualisieren. Dies ermöglicht gezielte Updates ohne Beeinflussung anderer Prioritäten.

## MiniZinc-Optimierung

Der `OptimierungService` ruft MiniZinc als externen Prozess auf:
- Modell: `src/main/resources/minizinc/vortragsplanung.mzn`
- Timeouts und Solver-Konfiguration (z.B. OR-Tools) über `SolverConfigDto`.
