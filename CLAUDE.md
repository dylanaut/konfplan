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
| Security | OIDC token verification against Keycloak (`quarkus-oidc`), Keycloak Admin REST Client for user provisioning |
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
- **Vortrag** (`SINGLE_TABLE` inheritance) → Pflichtvortrag | Wahlvortrag; optionally has an `AbschlussTyp`.
- **AbschlussTyp** – Enum for the school-leaving qualification associated with a talk.
- **Neigung** – Enum of professional/vocational aptitudes; Teilnehmer and Wahlvortrag can each have several.
- **EventSlot** – Time window within an event; overlap checking enforced.
- **Zuweisung** – Links a Teilnehmer to a Vortrag + Slot + Raum.
- **Prioritaet** – Teilnehmer's preference ranking (1–10, 10 = highest, 0 = no preference/hard-excluded) for a Wahlvortrag.
- **Verfuegbarkeit** – Whether a user is available in a slot (default: true when assigned).
- **RaumVerfuegbarkeit** – Room availability per slot, checked cross-event.

All entities extend `VersionedEntity` (Panache Active Record, `Long id`, `@Version Long version`).

### Frontend Structure (`frontend/src/`)

```
api/axios.js          # Central Axios instance; attaches/refreshes the Keycloak token per request
keycloak.js           # keycloak-js client instance (Authorization Code Flow)
components/
├── admin/tabs/       # Tab components for the Admin dashboard
└── *.vue             # Shared UI components (modals, buttons, pagination)
router/index.js       # Route definitions and navigation guards (redirect to Keycloak login)
stores/
├── auth.js           # isAuthenticated/role/login/logout wrapping keycloak-js
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
- **Identity/passwords live in Keycloak**, not in the database — `Nutzer` only carries a `keycloakId` link. `KeycloakUserProvisioningService` (`domain/service`) is the sole caller of the Keycloak Admin REST Client (used from `AdminService`/`TeilnehmerService`/`ReferentService` on every create/update/delete/reset-password).
- **Default password** on user create/import: `Konfplan1!` (non-temporary) in dev/test mode, a random UUID (temporary, forces a Keycloak-side password change on first login) in prod.
- **Password policy** (Keycloak realm-level, applies to every newly-set password — self-service reset and admin-set alike, but not retroactively to existing passwords): min. 8 characters, at least one uppercase, one lowercase, one digit, one special character.

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

Remote is **GitHub** (`github.com/dylanaut/konfplan`, private), authenticated via the `gh` CLI (HTTPS). Pull Requests, not GitLab MRs. CI runs via **GitHub Actions** (`.github/workflows/ci.yml`: build-and-test on every push/PR, package to GHCR on push to `main`; `.github/workflows/deploy.yml` is a separate, manually-triggered `workflow_dispatch` placeholder).

1. **Capture** – Create a GitHub Issue (label `feature`) with goal + acceptance criteria. The issue number (`#N`) is the tracking anchor.
2. **Branch** – Off up-to-date `main`, named `feature/<n>-<kebab-desc>` (`<n>` = GitHub issue number). Never commit feature work directly to `main`.
   ```bash
   git switch main && git pull
   git switch -c feature/<n>-<desc>
   ```
3. **Track** – Small, frequent commits; push with `git push -u origin <branch>`. Open a **Draft PR** early so CI runs per push. Put `Closes #<n>` in the PR description to auto-close the issue on merge.
4. **Merge** – CI green → remove Draft → **squash merge** + delete source branch.

- Commit messages end with the `Co-Authored-By: Claude Opus 4.8 (1M context)` trailer (see harness rules).
- Only stage files related to the current task; leave unrelated working-tree changes out of the commit.
- `gh pr create --fill --draft`, `gh pr merge --squash --delete-branch`, `gh issue create` work from the terminal.
- Dependabot (`.github/dependabot.yml`) opens PRs weekly for outdated Maven/npm/GitHub Actions dependencies — review and merge (or close) regularly.
- Verify the current branch with `git branch --show-current` before the first commit of a change set.
- After merging a PR, verify the change is actually present on `main` (`git log main --oneline | head`, `git grep <new-symbol> main`) and report the merge commit.

## Infrastructure Notes

- **Keycloak**: in dev mode, Quarkus Keycloak Dev Services auto-starts a container (needs Docker) and imports the realm from `backend/src/main/resources/keycloak/konfplan-realm.json` (roles, `konfplan-frontend`/`konfplan-backend`/`konfplan-admin-cli` clients). Fixed at `http://localhost:8180` so the frontend's hardcoded `keycloak.js` config can reach it directly. In `%test`, `quarkus.oidc` stays enabled (needed for the `JsonWebToken` CDI producer) but points at a dummy, never-contacted URL — tests use `@TestSecurity`/`@OidcSecurity` instead of a real Keycloak.
- **MiniZinc** must be installed and on `PATH` (configured as `/opt/homebrew/bin/minizinc` in `application.properties`).
- **Mailpit** is used in dev for outgoing email; configure credentials in `application.properties`.
- **Deadlines** (`deadlineReferenten`, `deadlineTeilnehmer`) gate editability in each dashboard.
- **Room conflict checks** are cross-event (a room booked in one Veranstaltung blocks it in another).
- **Vite** is configured to produce a `manifest.json` used by Quarkus Quinoa to embed frontend assets.
- **DB scripts:** `db/ensure_prod_db.sh` and `db/ensure_prod_infra.sh` for local PostgreSQL/Mailpit setup (Docker-based, dev/test use). Production `.deb` packaging lives under `packaging/debian/` (see its `README.md`).
- **Never operate production on a snapshot/floating image.** `deploy/.env`'s `IMAGE_TAG` must always be pinned to a specific tagged release (e.g. `1.3.0`, matching the GHCR tag without the `v` prefix — see GitHub Releases), never left on `latest` (which follows every push to `main`, i.e. potentially unfinished/untested work). Bump `IMAGE_TAG` explicitly after each new release before pulling.

## Worktrees & Environment Truth

- This repo is often used with git worktrees. Before claiming a config/file state, check the USER'S actual checkout path, not just the current worktree. Ask which checkout/branch they are running if unsure.
- Dev database is Postgres, database name `quarkus` (not `default`). Confirm with `\l` / `psql -c '\conninfo'` before writing DB-aware code.
- Never assume a config is correct across branches — grep the target branch explicitly (`git show <branch>:src/main/resources/application.properties`).
- When reporting on the contents/state of a config file, prefix the answer with the absolute file path and the branch it came from, especially if multiple worktrees or branches exist.

## Build & Verify Before Push

- Java/Quarkus: run `./mvnw -q verify` (or `test`) and the frontend build (`npm run build`) before every commit; do not push red.
- CI runs SpotBugs as part of the Maven build — check its findings locally before pushing to avoid pipeline noise.
- Verify runtime behaviour against the running dev backend (or Playwright for UI changes) before declaring a task done.

## Scope Discipline

- Change only what was asked. If a fix appears to require touching unrelated files (e.g. `.mzn` model inputs, Docker scaffolding), stop and ask first.
- Prefer the smallest diff that solves the reported problem; propose larger refactors as a separate issue.

## Documentation

- User-facing docs are AsciiDoc (`backend/src/main/asciidoc/*.adoc`). When a deployment, Keycloak, or packaging behaviour changes, update the relevant guide (e.g. `Deployment-DockerCompose.adoc`, `Ubuntu-Frischinstallation.adoc`) in the same PR.


