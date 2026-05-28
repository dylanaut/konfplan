## Architektur (Hexagonal / Ports & Adapters)

Das Backend folgt den Prinzipien der **Hexagonalen Architektur**, auch bekannt als **Ports & Adapters**. Das Kernprinzip ist die strikte Trennung zwischen der reinen Anwendungslogik (dem *Hexagon* oder *Kern*) und den technischen Details der Außenwelt (den *Adaptern*).

**Die goldene Regel:** Abhängigkeiten im Code zeigen **immer nur nach innen**, zum Kern der Anwendung. Der Kern darf niemals von einem Adapter abhängig sein.

### Paketstruktur

Die Struktur spiegelt diese Trennung wider:

```
kreyj/konfplan/
├── application/      # DER HEXAGON (Anwendungskern)
│   ├── port/
│   │   ├── in/       # Eingehende Ports (Was die Anwendung kann, z.B. Use Cases)
│   │   └── out/      # Ausgehende Ports (Was die Anwendung braucht, z.B. Repositories)
│   └── service/      # Implementierung der Anwendungslogik (Use-Case-Implementierung)
│
├── domain/           # Das Domänenmodell (Entitäten, Value Objects)
│
└── adapter/          # ADAPTER (Technologie-Implementierungen)
    ├── in/           # Eingehende Adapter (treiben die Anwendung an)
    │   └── web/      # REST-Adapter (Ressourcen & DTOs)
    └── out/          # Ausgehende Adapter (werden von der Anwendung angetrieben)
        ├── persistence/ # JPA/Panache-Implementierung der Persistenz-Ports
        └── minizinc/    # Adapter für den externen MiniZinc-Solver
```

### Komponenten im Detail

*   **`application` (Der Hexagon):**
    *   **`port.in`:** Definiert die Schnittstellen der Anwendungslogik (Use Cases). Beispiel: `AdminServiceInterface`. Sie beschreiben, *was* die Anwendung kann.
    *   **`port.out`:** Definiert die Schnittstellen, die der Kern benötigt, um mit externen Systemen zu kommunizieren (z.B. Datenbank). Beispiel: `NutzerRepositoryPort` (hypothetisch).
    *   **`service`:** Implementiert die `in`-Ports. Hier befindet sich die reine Geschäftslogik, frei von technologischen Details wie HTTP oder JPA.

*   **`domain`:**
    *   Enthält die Kern-Domänenobjekte (`Nutzer`, `Veranstaltung`, etc.).
    *   **Pragmatische Anmerkung:** In diesem Projekt dienen die JPA/Panache-Entitäten gleichzeitig als Domänenobjekte. In einer "reineren" Form wären dies POJOs ohne Persistenz-Annotationen.

*   **`adapter` (Die Außenwelt):**
    *   **`adapter/in/web`:** Der REST-Adapter.
        *   Die `resource`-Klassen (`AdminResource`) nehmen HTTP-Anfragen entgegen, validieren sie und rufen die entsprechenden Methoden auf den **eingehenden Ports** (`AdminServiceInterface`) auf.
        *   **DTOs (`dto`-Paket hier):** Data Transfer Objects sind Teil des Web-Adapters. Sie definieren den "Vertrag" der REST-API und werden niemals an die `service`-Schicht weitergegeben. Die `resource`-Klasse ist für die Umwandlung zwischen DTO und Domänenobjekt verantwortlich.
    *   **`adapter/out/persistence`:** Der Persistenz-Adapter. Implementiert die `out`-Ports und enthält die konkrete Logik zum Speichern und Laden von Daten mittels Panache.
    *   **`adapter/out/minizinc`:** Der `OptimierungService` agiert als Adapter, der die Anwendungsdaten in ein für MiniZinc verständliches Format übersetzt und den externen Prozess aufruft.

## Security

- Rollen: `ADMIN`, `REFERENT`, `TEILNEHMER`.
- Alle Endpunkte im `web`-Adapter werden mit `@RolesAllowed` oder `@Authenticated` abgesichert.
- JWT-Token wird von `AuthResource` ausgestellt.
- Passwörter: BCrypt via `BcryptUtil.bcryptHash()`.
