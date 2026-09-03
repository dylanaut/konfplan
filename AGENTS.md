# AGENTS.md – KonfPlan (Root)

## Projektübersicht

Der **KonfPlan** ist eine Webanwendung zur Planung und Verwaltung von Veranstaltungen mit Vorträgen (z. B. Schulungstage). Er unterstützt vier Nutzerrollen: Organisator, Administrator (ein Organisator mit zwei exklusiven Rechten - Wartungshinweis ankündigen und Verzeichnis-Import durchführen; Java-seitig `Administrator extends Organisator`, erbt also alle Organisator-Rechte), Referent und Teilnehmer. Ein zentrales Feature ist die automatische PlanErstellung der Teilnehmerzuweisung zu Wahlvorträgen via **MiniZinc**.

## Struktur

```
konfplan/
├── backend/          # Quarkus 3.35.1, Java 21, PostgreSQL (Dev/Prod), H2 (Test)
├── frontend/         # Vue 3 + Vite + Tailwind CSS
└── pom.xml           # Maven Multi-Module Parent
```

## Build & Start

### Build & Run

```bash
# Alles bauen (Root)
./mvnw clean install -DskipTests

# Backend starten (Dev-Modus mit Hot-Reload + Frontend via Quinoa)
cd backend && ../mvnw quarkus:dev
# → Backend: http://localhost:9000
# → Frontend Dev-Server: http://localhost:5173 (wird automatisch gestartet)

# Nur Frontend (lokal, ohne Backend)
cd frontend && npm install && npm run dev

# Frontend nur bauen (für Produktion)
cd frontend && npm run build
```

### Testing

```bash
# Alle Backend-Tests
cd backend && ../mvnw test

# Einzelne Test-Klasse
cd backend && ../mvnw test -Dtest=OrganisatorServiceTest

# Einzelne Test-Methode
cd backend && ../mvnw test -Dtest=OrganisatorServiceTest#methodName

# Playwright E2E-Tests
cd frontend && npx playwright test
```

Tests verwenden **H2** In-Memory-Datenbank; Produktion/Entwicklung verwendet **PostgreSQL**. Architektur-Konformität wird durch ArchUnit-Tests in `backend/src/test/java/.../architecture/` durchgesetzt.

## Schlüsseltechnologien

| Schicht      | Technologie                                       |
|--------------|---------------------------------------------------|
| Backend      | Quarkus 3.35.1, Java 21, RESTEasy Reactive        |
| Architektur  | Hexagonal (Ports & Adapters)                      |
| ORM          | Hibernate ORM Panache (Active Record Pattern)     |
| Datenbank    | PostgreSQL (Prod/Dev), H2 (Test)                  |
| Migration    | Flyway                                            |
| Security     | OIDC-Token-Verifikation gegen Keycloak (`quarkus-oidc`), Keycloak Admin REST Client |
| PlanErstellung  | MiniZinc (externer Prozess via `PlanErstellungService`) |
| CSV-Import   | OpenCSV 5.11.2                                    |
| PDF-Export   | OpenPDF 2.0.3                                     |
| E-Mail       | Quarkus Mailer (Mailpit für Dev)                  |
| Frontend     | Vue 3, Vite, Tailwind CSS, Pinia, Vue Router      |
| Integration  | Quarkus Quinoa (Frontend-Build eingebettet)       |
| E2E-Tests    | Playwright                                        |

## Backend-Paketstruktur

Hexagonale Architektur unter `backend/src/main/java/kreyj/konfplan/`:

```
adapter/
├── in/rest/          # REST-Ressourcen (HTTP-Einstiegspunkte, DTOs definiert hier)
│   ├── exception/    # CustomExceptionMapper
│   └── service/      # (Legacy-Standort, bevorzugt application/service)
application/
├── port/in/          # Use-Case-Schnittstellen (z.B. OrganisatorServiceInterface)
├── port/out/         # Repository/External-System-Schnittstellen
└── service/          # Business-Logik implementiert die in-ports
domain/               # JPA/Panache-Entitäten (dienen als Domain-Objekte)
infrastructure/       # Querschnittliche Belange
persistence/          # Panache-Repository-Implementierungen (out-port adapters)
util/                 # Hilfsfunktionen
```

**Abhängigkeitsregel:** Adapter hängen vom Anwendungskern ab; der Kern importiert nie Adapter-Klassen.

## Frontend-Struktur

Struktur unter `frontend/src/`:

```
api/axios.js          # Zentrale Axios-Instanz; haengt/erneuert das Keycloak-Token je Request
keycloak.js           # keycloak-js-Client-Instanz (Authorization Code Flow)
components/
├── organisator/tabs/ # Tab-Komponenten für Organisator-Dashboard
└── *.vue             # Gemeinsame UI-Komponenten (Modals, Buttons, Pagination)
router/index.js       # Route-Definitionen und Navigation Guards (Redirect zu Keycloak-Login)
stores/
├── auth.js           # isAuthenticated/Rolle/login/logout als Wrapper um keycloak-js
├── eventContext.js   # Global ausgewählte Veranstaltung
└── availability.js   # Map<userId, Set<slotId>> für Nutzer-/Raum-Verfügbarkeit
views/*.vue           # Top-Level-Seiten-Komponenten geroutet von Vue Router
```

## Domänenmodell (Kurzübersicht)

- **Veranstaltung** – Zentrale Entität; hat EventSlots, Gebäude, Nutzer; besitzt Deadlines für Referenten/Teilnehmer.
- **Nutzer** (SINGLE_TABLE) → Organisator | Administrator (extends Organisator) | Referent | Teilnehmer
- **Vortrag** (SINGLE_TABLE) → Pflichtvortrag | Wahlvortrag; hat optional einen `AbschlussTyp`.
- **AbschlussTyp** - Enum für den mit einem Vortrag assoziierten Schulabschluss.
- **Neigung** - Enum fachlicher/beruflicher Ausrichtungen; Teilnehmer und Wahlvortrag können jeweils mehrere zuordnen.
- **EventSlot** – Zeitfenster innerhalb einer Veranstaltung; mit Überschneidungsprüfung.
- **Zuweisung** – Ordnet Teilnehmer einem Vortrag + Slot + Raum zu.
- **Prioritaet** – Präferenz eines Teilnehmers für einen Wahlvortrag (Ranking 1-10, 10 = höchste, 0 = keine Präferenz/Hard-Exclude).
- **Verfuegbarkeit** – Gibt an, ob Nutzer in einem Slot verfügbar ist (Default: true bei Zuweisung).
- **RaumVerfuegbarkeit** – Modelliert die Verfügbarkeit von Räumen pro Slot inklusive veranstaltungsübergreifender Prüfung.
- **TeilnehmerDashboardDto** - DTO, das alle Daten für das persönliche Dashboard eines Teilnehmers bündelt.

## Wichtige Konventionen

- Alle Entitäten erben von `VersionedEntity` (Panache Active Record, `Long id`, `@Version Long version`).
- Polymorphe Typen nutzen `@Inheritance(SINGLE_TABLE)` + Jackson `@JsonSubTypes`.
- Datenbankfelder: Public Fields (kein Lombok), kein privater Getter/Setter-Boilerplate außer wo nötig.
- Datum/Zeit: `LocalDateTime` mit Custom `LocalDateTimeConverter`.
- Fehlerbehandlung: `CustomExceptionMapper` mappt Exceptions auf HTTP-Responses.
- **REST-API:** Alle Endpunkte unter `/api/...`; Security via `@RolesAllowed` (`ORGANISATOR`, `ADMINISTRATOR`, `REFERENT`, `TEILNEHMER`) oder `@Authenticated`.
- **DTOs leben im Web-Adapter** (`adapter/in/rest`), werden nie in die Service-Schicht weitergegeben.
- CSV-Import von Verfügbarkeiten erfolgt über 1-basierte Slot-Indizes.
- **Code-Stil:** `.editorconfig` im Root-Verzeichnis — 4 Leerzeichen für Java/XML, 2 für JS/TS/Vue.
- **Identität/Passwörter liegen in Keycloak**, nicht in der Datenbank — `Nutzer` trägt nur `keycloakId` als Verknüpfung. `KeycloakUserProvisioningService` (`domain/service`) ist die einzige Stelle, die den Keycloak Admin REST Client aufruft.
- **Standard-Passwort** bei Nutzer-Erstellung/Import: `Konfplan1!` (nicht temporär) im Dev/Test-Modus, ein zufälliges UUID-Passwort (temporär, erzwingt Keycloak-seitig eine Änderung beim ersten Login) in Produktion.
- **Passwort-Policy** (Keycloak-Realm-Ebene, gilt für jedes neu gesetzte Passwort — Selbst-Reset wie Organisator-gesetzt, nicht rückwirkend auf bestehende Passwörter): mind. 8 Zeichen, je mind. ein Groß-/Kleinbuchstabe, eine Ziffer, ein Sonderzeichen.

## Bekannte Besonderheiten & Infrastruktur-Notizen

- **MiniZinc** muss auf dem System installiert sein und im PATH sein (konfiguriert als `/opt/homebrew/bin/minizinc` in `application.properties` für macOS).
- Deadlines (`deadlineReferenten`, `deadlineTeilnehmer`) steuern die Bearbeitbarkeit von Daten in den jeweiligen Dashboards.
- Räume werden **veranstaltungsübergreifend** auf Überschneidungen geprüft (ein Raum in einer Veranstaltung blockiert ihn in einer anderen).
- **Keycloak**: Im Dev-Modus startet Quarkus Keycloak Dev Services automatisch einen Container (Docker nötig) und importiert das Realm aus `backend/src/main/resources/keycloak/konfplan-realm.json`. Passwort-Reset läuft über Keycloaks eigene Login-Seite, nicht mehr über einen Mailpit-Link aus dieser App.
- Vite (`vite.config.mjs`) ist so konfiguriert, dass eine `manifest.json` für die dynamische Einbindung von Assets in Qute-Templates erzeugt wird.
- **DB-Skripte:** `db/ensure_prod_db.sh` und `db/ensure_prod_infra.sh` für lokales PostgreSQL/Mailpit-Setup (Docker-basiert, Dev/Test). Produktions-`.deb`-Pakete liegen unter `packaging/debian/` (siehe dessen `README.md`).

## Git & Feature Workflow

Remote ist **GitLab** (`gitlab.zt.msg.team`), Authentifizierung via SSH. Merge Requests (MRs), nicht GitHub PRs. `glab` CLI ist verfügbar.

1. **Erfassung** – GitLab-Issue (Label `feature`) mit Ziel + Akzeptanzkriterien erstellen. Die Issue-Nummer (`#N`) ist der Tracking-Anker.
2. **Branch** – Basierend auf aktuellem `main`, benannt nach `feature/VOM-<n>-<kebab-desc>` (bestehende Konvention; `<n>` = GitLab-Issue-Nummer). Nie Feature-Arbeit direkt in `main` committen.
   ```bash
   git switch main && git pull
   git switch -c feature/VOM-<n>-<desc>
   ```
3. **Tracking** – Kleine, häufige Commits; Push mit `git push -u origin <branch>`. Frühzeitig Draft-MR öffnen, damit CI bei jedem Push läuft. `Closes #<n>` in die MR-Beschreibung aufnehmen, um Issue beim Merge automatisch zu schließen.
4. **Merge** – Pipeline grün → Draft entfernen → **Squash-Merge** + Source-Branch löschen.

- Commit-Nachrichten enden mit dem `Co-Authored-By: Claude Opus 4.8 (1M context)` Trailer.
- Nur Dateien zur aktuellen Aufgabe staging; unverwandte Änderungen aus dem Commit ausschließen.
- `glab mr create --fill --draft`, `glab mr merge --squash --remove-source-branch`, `glab issue create` funktionieren aus dem Terminal.

## Arbeitsanweisungen für den Agenten

### Checkliste: Full-Stack-Feature-Slice bei Datenmodell-Änderungen

Wenn eine Änderung am Datenmodell als "Full-Stack-Feature-Slice" angefordert wird, sind die folgenden Schritte durchzuführen:

1.  **Persistenz-Schicht (Backend):**
    *   **Entität anpassen:** Das neue Feld zur entsprechenden JPA-Entitätsklasse hinzufügen (z.B. `Vortrag.java`).
    *   **Enum erstellen:** Falls das neue Feld ein Enum ist, die `enum`-Klasse anlegen (z.B. `AbschlussTyp.java`).
    *   **Datenbank-Migration:** Ein neues Flyway-Migrationsskript (`V_... .sql`) erstellen, um das Datenbankschema mit `ALTER TABLE ... ADD COLUMN ...` zu aktualisieren.

2.  **Datenübertragungs-Schicht (Backend):**
    *   **DTO anpassen:** Die entsprechende(n) DTO-Klasse(n) (z.B. `VortragDto.java`) um das neue Feld erweitern.
    *   **Mapper-Logik aktualisieren:** Die Methoden, die Entitäten in DTOs umwandeln, anpassen, um das neue Feld zu berücksichtigen (z.B. in `ReferentService.mapVortragToDto`).

3.  **Service- & Business-Logik (Backend):**
    *   **Importer anpassen:** Falls es einen CSV-Importer gibt, die Logik erweitern (z.B. in `OrganisatorService.importVortraegeFromCsv`).
    *   **Erstellungs-/Update-Logik:** Die `create...`- und `update...`-Methoden in den relevanten Services anpassen.

4.  **Test-Schicht (Backend):**
    *   **Test-Daten anpassen:** Bestehende Test-Daten-Generatoren (z.B. `DevDataInitService`) oder CSV-Dateien im `test/resources`-Verzeichnis erweitern.
    *   **Testfälle erweitern:** Bestehende Unit- und Integrationstests (`*Test.java`) anpassen, um das neue Feld zu berücksichtigen.

5.  **Präsentations-Schicht (Frontend):**
    *   **Anzeige-Komponenten:** Vue-Komponenten, die die Daten anzeigen (z.B. `VortraegeTab.vue`), erweitern.
    *   **Bearbeitungs-Komponenten:** Vue-Komponenten, die zum Erstellen oder Bearbeiten verwendet werden (z.B. `OrganisatorVortragEditorModal.vue`), um ein neues Eingabefeld erweitern.
    *   **Daten-Handling im Frontend:** Das reaktive `form`-Objekt und die `save`-Methoden im Frontend anpassen.
