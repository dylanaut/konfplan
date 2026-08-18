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
- **Veranlagung** – Enum of professional/vocational aptitudes; Teilnehmer and Wahlvortrag can each have several.
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
- **Default password** on user create/import: `konfplan` (non-temporary) in dev/test mode, a random UUID (temporary, forces a Keycloak-side password change on first login) in prod.

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

## Infrastructure Notes

- **Keycloak**: in dev mode, Quarkus Keycloak Dev Services auto-starts a container (needs Docker) and imports the realm from `backend/src/main/resources/keycloak/konfplan-realm.json` (roles, `konfplan-frontend`/`konfplan-backend`/`konfplan-admin-cli` clients). Fixed at `http://localhost:8180` so the frontend's hardcoded `keycloak.js` config can reach it directly. In `%test`, `quarkus.oidc` stays enabled (needed for the `JsonWebToken` CDI producer) but points at a dummy, never-contacted URL — tests use `@TestSecurity`/`@OidcSecurity` instead of a real Keycloak.
- **MiniZinc** must be installed and on `PATH` (configured as `/opt/homebrew/bin/minizinc` in `application.properties`).
- **Mailpit** is used in dev for outgoing email; configure credentials in `application.properties`.
- **Deadlines** (`deadlineReferenten`, `deadlineTeilnehmer`) gate editability in each dashboard.
- **Room conflict checks** are cross-event (a room booked in one Veranstaltung blocks it in another).
- **Vite** is configured to produce a `manifest.json` used by Quarkus Quinoa to embed frontend assets.
- **DB scripts:** `db/ensure_prod_db.sh` and `db/ensure_prod_infra.sh` for local PostgreSQL/Mailpit setup (Docker-based, dev/test use). Production `.deb` packaging lives under `packaging/debian/` (see its `README.md`).

Add as a new top-level '## Git & Branch Workflow' section near the top of CLAUDE.md, since version_control was the #1 goal across sessions.\n\n## Git & Branch Workflow
- Never commit directly to `main`. For every task: create a GitHub issue, then a feature branch named `feature/<issue-number>-<slug>`, then open a PR when green.
- Before committing, run `git status` and `git diff --cached` and confirm ONLY the files related to the current task are staged. Never commit pre-existing staged files.
- Verify the current branch with `git branch --show-current` before the first commit of a change set.
- After merging a PR, verify the change is actually present on `main` (`git log main --oneline | head`, `git grep <new-symbol> main`) and report the merge commit.
Add a '## Worktrees & Environment Truth' section under any existing Environment/Setup heading.\n\n## Worktrees & Environment Truth
- This repo is often used with git worktrees. Before claiming a config/file state, check the USER'S actual checkout path, not just the current worktree. Ask which checkout/branch they are running if unsure.
- Dev database is Postgres, database name `quarkus` (not `default`). Confirm with `\l` / `psql -c '\conninfo'` before writing DB-aware code.
- Never assume a config is correct across branches — grep the target branch explicitly (`git show <branch>:src/main/resources/application.properties`).
Add under a '## Testing' or '## Definition of Done' section.\n\n## Build & Verify Before Push
- Java/Quarkus: run `./mvnw -q verify` (all 115+ tests) and the frontend build (`npm run build`) before every commit; do not push red.
- CI runs SpotBugs and a Dockerfile lint — run these locally before pushing to avoid pipeline failures.
- Verify runtime behaviour against the running dev backend (or Playwright for UI changes) before declaring a task done.
Add near the top of CLAUDE.md as general working agreements.\n\n## Scope Discipline
- Change only what was asked. If a fix appears to require touching unrelated files (e.g. .mzn model inputs, Docker scaffolding), stop and ask first.
- Prefer the smallest diff that solves the reported problem; propose larger refactors as a separate issue.
Add a '## Documentation' section listing the doc files that must be kept in sync.\n\n## Documentation
- User-facing docs are AsciiDoc (`docs/*.adoc`). When a deployment, Keycloak, or packaging behaviour changes, update the Ubuntu/Windows install guide in the same PR.

Before any git operation in this repo, always first run and show me: `git branch --show-current`, `git status --short`, `git worktree list`, and `git log --oneline -3`. Then state in one sentence which branch you will commit to and exactly which files you will stage. Wait for my confirmation if anything unrelated to the current task is staged.

Whenever you report on the contents or state of a config file, always prefix the answer with the absolute file path and the branch it came from. If multiple worktrees or branches exist, check all of them and show me the differences before drawing any conclusion.

Use a subagent to investigate this deployment failure end to end: read the container logs, systemd units, ufw/iptables rules, and the Keycloak + app config. Do not change anything. Come back with a ranked list of hypotheses, the evidence for each, and the single cheapest command that would confirm or eliminate the top one.

Create a reusable workflow for me. First, read our recent 20 merged PRs with `gh pr list --state merged` and `git log` to infer our exact conventions: branch naming, commit message style, PR description format, test commands, and CI checks. Then write two files:

1. `CLAUDE.md` (or append to it) documenting: the repo layout, backend/frontend build+test commands, Flyway migration rules, and hard git rules — always `git status` and verify staged files before committing, always confirm the current branch matches the intended feature branch, never force-push shared branches, and always verify the target DB/schema name before running DB-dependent code.

2. `.claude/commands/ship-issue.md` — a slash command taking an issue number that: (a) fetches the issue with `gh issue view`, (b) writes an implementation plan and asks me to approve it, (c) creates the feature branch, (d) implements backend + frontend changes, (e) runs the full test suite and frontend build, (f) self-reviews `git diff` against the issue acceptance criteria and fixes anything questionable, (g) pushes and opens a draft PR, (h) polls CI with `gh pr checks --watch` and fixes SpotBugs/Dockerfile/lint failures autonomously, (i) marks the PR ready only when everything is green, and reports a summary.

After writing them, run `/ship-issue` on the oldest open issue as a live test and tell me where the workflow was ambiguous.

I want to work on 3 independent issues in parallel using git worktrees and subagents.

Setup: for each issue, create a dedicated worktree under `../worktrees/<branch-name>` with `git worktree add`. Then launch one subagent per issue. Give every subagent these non-negotiable rules: (1) you may only read, edit, and run commands inside YOUR assigned worktree path — never assume the main checkout reflects your branch, and never inspect another worktree; (2) before touching config or database code, print the resolved config file from your own worktree and confirm the actual datasource/schema name rather than assuming; (3) run the full backend test suite and frontend build inside your worktree before reporting done; (4) commit and push your branch and open a draft PR.

Ask me which 3 issues to pick, confirm they don't touch overlapping files, then run all three subagents concurrently. When all finish, act as coordinator: rebase each branch on latest main, resolve conflicts, re-run the full suite on the integrated result, and give me a merge order with a one-paragraph risk note per PR. Finally clean up the worktrees.

Build me an autonomous deploy-and-verify agent as `.claude/commands/deploy-verify.md`.

First, mine our AsciiDoc deployment guide, git history, and any incident notes for every deployment failure we've hit — Keycloak/OIDC container startup ordering, private GHCR registry auth, Docker Hub network blocking, leftover ufw/iptables rules, TLS redirect loops, secrets-file permissions causing crash-loops, single-domain /auth reverse-proxy config, Flyway migration mismatches. Turn each into a named, checkable hypothesis with the exact diagnostic command and the exact fix.

The command should then: (1) deploy the current build to the target environment, (2) tail service logs and container status until the app is up or clearly failed, (3) on failure, walk the hypothesis list in order of likelihood, running the diagnostic command for each and reporting evidence before concluding, (4) explicitly classify the root cause as APP-BUG (fix the code, commit, redeploy, loop) or INFRA-BLOCKER (stop and give me an exact remediation checklist with commands to run), (5) once up, run Playwright smoke tests against the live URL covering login via Keycloak, loading a report, and one import flow, (6) loop up to 3 times, then produce a final report.

Write the command, then dry-run it against our dev environment and show me the hypothesis list you generated so I can correct anything wrong.


