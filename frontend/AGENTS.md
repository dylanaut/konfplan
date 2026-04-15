# AGENTS.md – frontend/

## Überblick

Das Frontend ist eine **Vue 3**-Single-Page-Application mit Vite, Tailwind CSS und Pinia. Es kommuniziert mit dem Quarkus-Backend über eine REST-API und wird im Produktionsbetrieb via **Quarkus Quinoa** eingebettet.

## Technologien

| Technologie      | Version  | Zweck                              |
|------------------|----------|------------------------------------|
| Vue 3            | ^3.3     | UI-Framework (Composition API)     |
| Vite             | ^7.3     | Build-Tool + Dev-Server            |
| Pinia            | ^2.1     | State Management                   |
| Vue Router       | ^4.2     | Client-seitiges Routing            |
| Tailwind CSS     | ^3.4     | Utility-First CSS                  |
| Axios            | ^1.4     | HTTP-Client                        |
| jwt-decode       | ^3.1     | JWT-Parsing im Browser             |
| lucide-vue-next  | ^0.244   | Icon-Bibliothek                    |
| Playwright       | ^1.59    | End-to-End-Tests                   |

## Projektstruktur

```
src/
├── main.js              # App-Einstiegspunkt (Vue + Router + Pinia)
├── App.vue              # Root-Komponente
├── style.css            # Globale Styles + Tailwind-Direktiven
├── router/
│   └── index.js         # Routen + Navigation Guards
├── stores/
│   └── auth.js          # Pinia Auth-Store (JWT, Rolle, Login/Logout)
├── api/
│   └── axios.js         # Axios-Instanz mit Auth-Header-Interceptor
├── views/               # Seiten-Komponenten (1 pro Route)
│   ├── Login.vue
│   ├── ResetPassword.vue
│   ├── AdminDashboard.vue      # Haupt-UI für Admins
│   ├── ReferentDashboard.vue   # Profil + Vorträge für Referenten
│   └── TeilnehmerDashboard.vue # Prioritäten + Plan für Teilnehmer
└── components/          # Wiederverwendbare UI-Komponenten (Modals)
    ├── AdminTalkEditorModal.vue
    ├── AdminVortragEditorModal.vue
    ├── UserEditorModal.vue
    ├── RaumEditorModal.vue
    ├── GebaeudeEditorModal.vue
    ├── VeranstaltungEditorModal.vue
    └── EventSlotEditorModal.vue
```

## Entwicklung & Build

```bash
# Abhängigkeiten installieren
npm install

# Dev-Server starten (Port 5173, Proxy zu Backend :9000)
npm run dev

# Produktions-Build (Output nach dist/)
npm run build

# E2E-Tests ausführen (Backend muss laufen)
npx playwright test
```

## Routing & Zugangskontrolle

| Route            | Komponente              | Rolle         |
|------------------|-------------------------|---------------|
| `/login`         | `Login.vue`             | Public        |
| `/reset-password`| `ResetPassword.vue`     | Public        |
| `/admin`         | `AdminDashboard.vue`    | ADMIN         |
| `/referent`      | `ReferentDashboard.vue` | REFERENT      |
| `/teilnehmer`    | `TeilnehmerDashboard.vue` | TEILNEHMER  |

Der Navigation Guard in `router/index.js` prüft `authStore.isAuthenticated` und `authStore.userRole`. Unberechtigte Zugriffe werden zu `/login` weitergeleitet.

## Auth-Store (Pinia)

```javascript
// stores/auth.js – Nutzung
const authStore = useAuthStore();

authStore.token       // JWT-String oder null
authStore.userRole    // "ADMIN" | "REFERENT" | "TEILNEHMER" | null
authStore.isAuthenticated  // Boolean

authStore.login(token)   // Setzt Token, dekodiert Rolle
authStore.logout()       // Löscht Token und leitet zu /login
```

## API-Kommunikation

Alle API-Calls über die konfigurierte **Axios-Instanz** aus `api/axios.js`:
```javascript
import api from '@/api/axios';

// Beispiel
const response = await api.get('/api/veranstaltungen');
const data = response.data;
```

- Base-URL: `/` (relativ, kein hardcodierter Host)
- Auth-Header wird automatisch als Interceptor gesetzt (`Authorization: Bearer <token>`)
- Backend-Port 9000 (Dev: Vite-Proxy leitet `/api` weiter)

## Konventionen

- **Composition API** (`<script setup>`) verwenden, keine Options API
- Komponenten in `components/` sind Modal-Dialoge (Editor-Pattern)
- Neue Views in `views/` anlegen und in `router/index.js` registrieren
- Tailwind-Klassen direkt im Template, keine separaten CSS-Dateien
- Icons aus `lucide-vue-next`: `import { IconName } from 'lucide-vue-next'`
- Kein TypeScript (Projekt verwendet vanilla JavaScript)
- Keine globalen `console.log`-Aufrufe im produktiven Code

## E2E-Tests (Playwright)

```
tests/
├── example.spec.js              # Basis-Smoke-Test
└── PlanungsWorkflow.spec.js     # Vollständiger Planungs-Workflow
```

- Konfiguration: `playwright.config.js`
- Tests laufen gegen `http://localhost:9000` (Quarkus muss laufen)
- CI: `.github/workflows/playwright.yml`
- Neue Tests in `tests/*.spec.js` ablegen
