# AGENTS.md – frontend/

## Überblick

Das Frontend ist eine **Vue 3**-Single-Page-Application mit Vite, Tailwind CSS und Pinia. Es kommuniziert mit dem Quarkus-Backend über eine REST-API und wird im Produktionsbetrieb via **Quarkus Quinoa** eingebettet.

## Schlüsseltechnologien

| Technologie      | Zweck                              |
|------------------|------------------------------------|
| Vue 3            | UI-Framework (Composition API)     |
| Vite 7           | Build-Tool + Dev-Server            |
| Pinia 2          | State Management (Auth, Context)   |
| Tailwind CSS 3   | Styling                            |
| lucide-vue-next  | Icon-Set                           |
| Axios            | HTTP-Client                        |
| Playwright       | End-to-End-Tests                   |

## Projektstruktur & Dashboards

- **`AdminDashboard.vue`**: Hauptkomponente für den Admin-Bereich. Agiert als Container, der die Navigation zwischen verschiedenen Verwaltungs-Tabs steuert und deren Daten sowie Aktionen koordiniert. Die spezifische Logik und das UI für jeden Tab wurden in separate Komponenten (`components/admin/tabs/`) ausgelagert.
  - **`components/admin/tabs/`**: Enthält die einzelnen Tab-Komponenten des `AdminDashboard.vue` (z.B. `ErgebnisseTab.vue`, `VeranstaltungenTab.vue`, `TeilnehmerTab.vue`, etc.).
- **`ReferentDashboard.vue`**: Verwaltung des eigenen Profils, der Vorträge und der persönlichen Verfügbarkeit pro Veranstaltung. Berücksichtigt Deadlines.
- **`TeilnehmerDashboard.vue`**: Ansicht des persönlichen Vortragsplans und Pflege der Prioritäten (Top 10) für Wahlvorträge.

## Wichtige Konzepte

### EventContext Store (`stores/eventContext.js`)
Speichert die aktuell im Admin-Bereich ausgewählte Veranstaltung global, damit beim Tab-Wechsel der Kontext erhalten bleibt.

### Dynamische Formulare & Modals
Editoren für Entitäten (Nutzer, Vorträge, Räume etc.) sind als separate Komponenten in `components/` ausgelagert und werden als Modals eingeblendet.

### Deadline-Handling
Das UI prüft die in den Veranstaltungs-DTOs gelieferten Deadlines (`deadlineReferenten`, `deadlineTeilnehmer`). Bei Ablauf werden Eingabefelder und Speicher-Buttons deaktiviert (HTML `disabled`).

### Verfügbarkeits-Matrizen
Die Verfügbarkeiten von Referenten und Teilnehmern sind als interaktive Checkbox-Matrizen direkt in die jeweiligen Nutzer-Listen integriert, um die Usability zu erhöhen.

### Wiederverwendbare Komponenten
- **`PaginationControls.vue`**: Eine generische Komponente zur Paginierung von Listen.

## Entwicklung & Build

```bash
# Abhängigkeiten installieren
cd frontend && npm install

# Dev-Server starten (Port 5173, Proxy zu Backend :9000)
npm run dev

# E2E-Tests ausführen (Backend muss laufen)
npx playwright test
```

## Styling-Konventionen
- Utility-First mit Tailwind CSS direkt in den Templates.
- Icons immer aus `lucide-vue-next`.
- Übergangseffekte mit der CSS-Klasse `animate-fade-in` für eine flüssige UX.
- Paginierung bei allen größeren Listen (Admin-Bereich).

## API-Kommunikation
- Nutzung der zentralen Axios-Instanz (`api/axios.js`).
- Automatisches Mitsenden des JWT-Tokens im Authorization-Header.
- Relative Pfade (z. B. `api.get('/api/vortraege')`) verwenden.
