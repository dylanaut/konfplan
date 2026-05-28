# AGENTS.md – Frontend

## Projektübersicht

Das Frontend des KonfPlan ist eine Single-Page-Application (SPA), die mit **Vue 3** und dem **Vite**-Build-Tool entwickelt wurde. Sie kommuniziert über eine REST-API mit dem Quarkus-Backend.

## Schlüsseltechnologien

| Bereich              | Technologie                               |
|----------------------|-------------------------------------------|
| Framework            | Vue 3 (Composition API)                   |
| Build-Tool           | Vite                                      |
| State Management     | Pinia                                     |
| Routing              | Vue Router                                |
| Styling              | Tailwind CSS                              |
| API-Kommunikation    | Axios                                     |
| UI-Komponenten       | Headless UI (für Modals, etc.), Lucide Icons |

## Build & Start

```bash
# Abhängigkeiten installieren
npm install

# Dev-Server starten (mit Hot-Reload)
npm run dev
# → Frontend erreichbar unter http://localhost:5173

# Produktion-Build erstellen
npm run build
```

## Projektstruktur (`/src`)

```
src/
├── api/
│   └── axios.js          # Zentrale Axios-Instanz mit Interceptor für JWT-Header
├── components/
│   ├── admin/            # Spezifische Komponenten für das Admin-Dashboard
│   │   └── tabs/         # Einzelne Tab-Komponenten (Teilnehmer, Vorträge etc.)
│   └── *.vue             # Wiederverwendbare UI-Komponenten (Modals, Buttons etc.)
├── router/
│   └── index.js          # Definition aller Frontend-Routen und Navigation Guards
├── stores/
│   ├── auth.js           # Pinia-Store für Authentifizierung (Login, Logout, Token)
│   ├── eventContext.js   # Pinia-Store, der die global ausgewählte Veranstaltung hält
│   └── availability.js   # Pinia-Store für das Management von Verfügbarkeiten
├── views/
│   └── *.vue             # Haupt-Seitenkomponenten (z.B. AdminDashboard.vue, Login.vue)
├── App.vue               # Root-Komponente der Anwendung
└── main.js               # Einstiegspunkt: Initialisiert Vue, Pinia, Router etc.
```

## State Management (Pinia)

Die Anwendung nutzt Pinia für ein zentrales und typensicheres State Management. Die Stores sind nach Verantwortlichkeiten getrennt:

-   **`auth.js`**:
    -   Verwaltet den `user` und das `token`.
    -   Bietet Aktionen wie `login()`, `logout()`.
    -   Ein Getter `isAuthenticated` prüft, ob ein gültiges Token vorhanden ist.
    -   Der Axios-Interceptor nutzt diesen Store, um den `Authorization`-Header bei jedem API-Aufruf zu setzen.

-   **`eventContext.js`**:
    -   Ein einfacher Store, der die `selectedEvent` (die vom Admin ausgewählte Veranstaltung) global verfügbar macht.
    -   Dies verhindert, dass die Event-ID durch unzählige Komponenten per Props durchgereicht werden muss.

-   **`availability.js` (Neu & Wichtig)**:
    -   Dieser Store wurde eingeführt, um die komplexe Logik der Verfügbarkeiten zu zentralisieren.
    -   **State:** Hält `userAvailabilities` und `roomAvailabilities` als `Map<number, Set<number>>`. Dies ist eine sehr performante Struktur, um die IDs der verfügbaren Slots für eine Nutzer- oder Raum-ID zu speichern.
    -   **Actions:**
        -   `fetchAvailabilities(eventId)`: Lädt alle Verfügbarkeiten für eine Veranstaltung vom Backend und füllt die Maps.
        -   `toggleUserAvailability(userId, slotId)`: Fügt eine Slot-ID zum Set eines Nutzers hinzu oder entfernt sie. Markiert den Nutzer als "geändert".
        -   `saveAvailabilities(eventId)`: Sendet nur die geänderten Verfügbarkeits-Sets an das Backend.
    -   **Getters:**
        -   `isUserAvailable(userId, slotId)`: Bietet eine einfache Möglichkeit für UI-Komponenten, die Verfügbarkeit reaktiv zu prüfen (`map.get(userId)?.has(slotId)`).

## API-Kommunikation

Alle HTTP-Anfragen an das Backend werden über eine zentrale Axios-Instanz in `/src/api/axios.js` abgewickelt. Diese Instanz hat einen **Interceptor**, der automatisch bei jedem ausgehenden Request das JWT-Token aus dem `auth` Store in den `Authorization: Bearer ...` Header einfügt. Dies entkoppelt die Komponenten von der Authentifizierungslogik.

## Komponenten-Konzept

-   **`/views`**: Enthalten die Haupt-Seiten, die über den Vue Router direkt aufgerufen werden (z.B. `AdminDashboard.vue`). Diese Komponenten sind für das Layout der Seite und das Laden der übergeordneten Daten zuständig.
-   **`/components`**: Enthalten wiederverwendbare UI-Elemente.
    -   **Allgemeine Komponenten** (z.B. `PaginationControls.vue`, `UserEditorModal.vue`) sind direkt im `components`-Ordner.
    -   **Spezifische Komponenten**, die nur in einem bestimmten Kontext verwendet werden (z.B. die Tabs im Admin-Dashboard), liegen in entsprechenden Unterordnern wie `/components/admin/tabs`.