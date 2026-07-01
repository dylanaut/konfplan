# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**KonfPlan** is a full-stack web application for planning and managing events with talks (e.g., school training days). It supports three user roles: Admin, Referent (speaker), and Teilnehmer (participant). A key feature is automatic scheduling of participants to optional talks via **MiniZinc** constraint solving.

## Commands

### Build & Run

```bash
# Build everything (from root)
./mvnw clean install -DskipTests

# Run backend in dev mode (hot-reload + automatic frontend dev-server via Quinoa)
cd backend && ../mvnw quarkus:dev
# Backend: http://localhost:9000
# Frontend dev-server: http://localhost:5173

# Frontend only
cd frontend && npm install && npm run dev
cd frontend && npm run build
```

### Testing

```bash
# Run all backend tests
cd backend && ../mvnw test

# Run a single test class
cd backend && ../mvnw test -Dtest=AdminServiceTest

# Run a single test method
cd backend && ../mvnw test -Dtest=AdminServiceTest#methodName
```

Tests use **H2** in-memory database; production/dev uses **PostgreSQL**. Architecture conformance is enforced by ArchUnit tests in `backend/src/test/java/.../architecture/`.

### Playwright E2E Tests

```bash
cd frontend && npx playwright test
```

## Architecture

### Stack

| Layer | Technology |
|---|---|
| Backend | Quarkus 3.35.1, Java 21, RESTEasy Reactive |
| Architecture | Hexagonal (Ports & Adapters) |
| ORM | Hibernate ORM Panache (Active Record) |
| Database | PostgreSQL (prod/dev), H2 (test), Flyway migrations |
| Security | JWT (SmallRye), BCrypt, `quarkus-security-jpa` |
| Scheduling | MiniZinc (external process via `PlanErstellungService`) |
| Frontend | Vue 3, Vite, Tailwind CSS, Pinia, Vue Router, Axios |
| Integration | Quarkus Quinoa (embeds frontend build into backend) |

### Backend Package Structure (`backend/src/main/java/kreyj/konfplan/`)

```
adapter/
├── in/rest/          # REST resources (HTTP entry points, DTOs defined here)
│   ├── exception/    # CustomExceptionMapper
│   └── service/      # (legacy location, prefer application/service)
application/
├── port/in/          # Use-case interfaces (e.g. AdminServiceInterface)
├── port/out/         # Repository/external-system interfaces
└── service/          # Business logic implementing the in-ports
domain/               # JPA/Panache entities (serve as domain objects)
infrastructure/       # Cross-cutting concerns
persistence/          # Panache repository implementations (out-port adapters)
util/                 # Utilities
```

**Dependency rule:** Adapters depend on the application core; the core never imports adapter classes.

### Domain Model

- **Veranstaltung** – Central entity; owns EventSlots, rooms, users, and deadlines.
- **Nutzer** (`SINGLE_TABLE` inheritance) → Admin | Referent | Teilnehmer
- **Vortrag** (`SINGLE_TABLE` inheritance) → Pflichtvortrag | Wahlvortrag; optionally has a `Berufsfeld`.
- **Berufsfeld** – Enum for categorizing talks.
- **EventSlot** – Time window within an event; overlap checking enforced.
- **Zuweisung** – Links a Teilnehmer to a Vortrag + Slot + Raum.
- **Prioritaet** – Teilnehmer's preference ranking (1–10) for a Wahlvortrag.
- **Verfuegbarkeit** – Whether a user is available in a slot (default: true when assigned).
- **RaumVerfuegbarkeit** – Room availability per slot, checked cross-event.

All entities extend `VersionedEntity` (Panache Active Record, `Long id`, `@Version Long version`).

### Frontend Structure (`frontend/src/`)

```
api/axios.js          # Central Axios instance with JWT interceptor
components/
├── admin/tabs/       # Tab components for the Admin dashboard
└── *.vue             # Shared UI components (modals, buttons, pagination)
router/index.js       # Route definitions and navigation guards
stores/
├── auth.js           # Login/logout/token (drives the Axios JWT interceptor)
├── eventContext.js   # Globally selected Veranstaltung
└── availability.js   # Map<userId, Set<slotId>> for user/room availability
views/*.vue           # Top-level page components routed by Vue Router
```

## Key Conventions

- **REST API:** All endpoints under `/api/...`, secured with `@RolesAllowed` (`ADMIN`, `REFERENT`, `TEILNEHMER`) or `@Authenticated`.
- **DTOs live in the web adapter** (`adapter/in/rest`), never passed to the service layer.
- **No Lombok:** Public fields on JPA entities; getters/setters only where required.
- **Dates:** `LocalDateTime` with a custom `LocalDateTimeConverter`.
- **Polymorphism:** `@Inheritance(SINGLE_TABLE)` + Jackson `@JsonSubTypes` for Nutzer and Vortrag hierarchies.
- **CSV import:** Slot indices are 1-based.
- **Code style:** `.editorconfig` at root — 4 spaces for Java/XML, 2 spaces for JS/TS/Vue.
- **Default password** on user create/import: `start123` (BCrypt-hashed).

## Full-Stack Feature Slice Checklist (Data Model Changes)

When adding a field to the domain model:

1. **Entity** – add field to the JPA class.
2. **Flyway** – create `V{n}__description.sql` in `backend/src/main/resources/db/migration/` with `ALTER TABLE ... ADD COLUMN`.
3. **DTO** – update the relevant DTO class in the web adapter.
4. **Mapper** – update entity→DTO mapping in the service (e.g. `mapVortragToDto`).
5. **Service** – update `create...` / `update...` methods.
6. **Test data** – update `DevDataInitService` or CSV fixtures in `test/resources`.
7. **Tests** – extend affected `*Test.java` files.
8. **Frontend** – update display components, editor modals, and reactive `form` objects.

## Git & Feature Workflow

Remote is **GitLab** (`gitlab.zt.msg.team`), authenticated via SSH. Merge Requests (MRs), not GitHub PRs. `glab` CLI is available.

1. **Capture** – Create a GitLab Issue (label `feature`) with goal + acceptance criteria. The issue number (`#N`) is the tracking anchor.
2. **Branch** – Off up-to-date `main`, named `feature/VOM-<n>-<kebab-desc>` (existing convention; `<n>` = GitLab issue number). Never commit feature work directly to `main`.
   ```bash
   git switch main && git pull
   git switch -c feature/VOM-<n>-<desc>
   ```
3. **Track** – Small, frequent commits; push with `git push -u origin <branch>`. Open a **Draft MR** early so CI runs per push. Put `Closes #<n>` in the MR description to auto-close the issue on merge.
4. **Merge** – Pipeline green → remove Draft → **squash merge** + delete source branch.

- Commit messages end with the `Co-Authored-By: Claude Opus 4.8 (1M context)` trailer (see harness rules).
- Only stage files related to the current task; leave unrelated working-tree changes out of the commit.
- `glab mr create --fill --draft`, `glab mr merge --squash --remove-source-branch`, `glab issue create` work from the terminal.

## Infrastructure Notes

- **MiniZinc** must be installed and on `PATH` (configured as `/opt/homebrew/bin/minizinc` in `application.properties`).
- **Mailpit** is used in dev for outgoing email; configure credentials in `application.properties`.
- **Deadlines** (`deadlineReferenten`, `deadlineTeilnehmer`) gate editability in each dashboard.
- **Room conflict checks** are cross-event (a room booked in one Veranstaltung blocks it in another).
- **Vite** is configured to produce a `manifest.json` used by Quarkus Quinoa to embed frontend assets.
- **DB scripts:** `db/init_db.sh` and `db/ensure_prod_db.sh` for PostgreSQL setup.
