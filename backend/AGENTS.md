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
    *   **`adapter/out/minizinc`:** Der `PlanErstellungService` agiert als Adapter, der die Anwendungsdaten in ein für MiniZinc verständliches Format übersetzt und den externen Prozess aufruft.

## Security

- Rollen: `ADMIN`, `REFERENT`, `TEILNEHMER`.
- Alle Endpunkte im `web`-Adapter werden mit `@RolesAllowed` oder `@Authenticated` abgesichert.
- Identität liegt komplett bei **Keycloak**: Login, Passwörter und Passwort-Reset laufen über
  Keycloaks gehostete Login-Seite bzw. Account-Console, nicht mehr über einen eigenen Endpunkt.
  Das Backend validiert eingehende Tokens nur noch via `quarkus-oidc` (kein eigenes JWT, kein
  lokaler Passwort-Hash mehr).
- `Nutzer` enthält nur noch `keycloakId` als Verknüpfung zum Keycloak-User; die Rolle steckt im
  Token-Claim `realm_access.roles` (`quarkus.oidc.roles.role-claim-path`, siehe
  `application.properties`).
- Frontend nutzt `keycloak-js` mit Authorization Code Flow (Redirect statt eigenem Formular).
- Dev-Modus: Quarkus Keycloak Dev Services startet automatisch einen Container; Realm-Import aus
  `src/main/resources/keycloak/konfplan-realm.json` (Rollen, Clients, Service-Account für den
  Admin-REST-Client).

### `KeycloakUserProvisioningService` (`domain/service`)

- Einzige Stelle, die den Keycloak Admin REST Client (`quarkus-keycloak-admin-rest-client`)
  anspricht - `createUser`/`updateUser`/`resetPassword`/`deleteUser`, eingebunden in
  `AdminService`, `TeilnehmerService`, `ReferentService` an jeder Stelle, die früher direkt einen
  `passwordHash` gesetzt hat.
- Passwort-Konvention beim Anlegen: `Konfplan1!` (nicht-temporär) in Dev/Test, eine zufällige UUID
  (`temporary=true`, `requiredActions=["UPDATE_PASSWORD"]`) in Prod - Keycloak erzwingt dort eine
  Passwortänderung beim ersten Login.
- Passwort-Policy (Keycloak-Realm-Ebene, `ProdKeycloakRealmSyncService`/`konfplan-realm.json`):
  mind. 8 Zeichen, je mind. ein Groß-/Kleinbuchstabe, eine Ziffer, ein Sonderzeichen - gilt für
  jedes neu gesetzte Passwort, nicht rückwirkend.

### Admin-Konten ohne E-Mail (Lockout-Schutz)

- Keycloaks Passwort-Reset-Flow braucht eine E-Mail-Adresse (der Reset-Link wird dorthin
  verschickt). Für `REFERENT`/`TEILNEHMER` gibt es einen übergeordneten `ADMIN`, der das Passwort
  notfalls per Admin-REST-API zurücksetzen kann - für `ADMIN`-Konten selbst gibt es keine
  übergeordnete Instanz. Ein `ADMIN` ohne E-Mail wäre bei einem vergessenen Passwort
  **permanent** ausgesperrt.
- **Prävention:** `AdminService#createUser` und `#updateUser` lehnen es ab (`BusinessException` /
  `UpdateNutzerException`), eine `Admin`-E-Mail-Adresse leer zu setzen oder ein Admin-Konto ohne
  E-Mail anzulegen.
- **Recovery (Rettungsweg für bereits bestehende Konten ohne E-Mail):** `AdminService#resetPassword`
  (`POST /api/admin/nutzer/{id}/reset-password`) setzt das Passwort eines beliebigen Nutzers direkt
  in Keycloak, ohne E-Mail-Bestätigung - ein anderer `ADMIN` kann damit ein ausgesperrtes
  Admin-Konto wiederherstellen. Voraussetzung ist, dass mindestens ein zweiter Admin noch Zugriff
  hat.
