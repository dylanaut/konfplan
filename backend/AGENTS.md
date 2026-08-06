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
- JWT-Token wird von `AuthResource#login` ausgestellt (4h Gültigkeit).
- Passwörter: BCrypt via `BcryptUtil.bcryptHash()`.

### Admin-Konten ohne E-Mail (Lockout-Schutz)

- `AuthResource#forgotPassword` kann ein Passwort nur zurücksetzen, wenn der Nutzer eine
  E-Mail-Adresse hinterlegt hat (der Reset-Link wird dorthin verschickt). Ohne E-Mail bricht der
  Self-Service-Weg ab (Response bleibt trotzdem `202`, um keine Rückschlüsse auf existierende
  Konten zuzulassen) und protokolliert nur den Versuch.
- Für `REFERENT`/`TEILNEHMER` gibt es einen übergeordneten `ADMIN`, der das Passwort notfalls
  zurücksetzen kann - für `ADMIN`-Konten selbst gibt es keine übergeordnete Instanz. Ein
  `ADMIN` ohne E-Mail wäre bei einem vergessenen Passwort **permanent** ausgesperrt gewesen.
- **Prävention:** `AdminService#createUser` und `#updateUser` lehnen es ab (`BusinessException` /
  `UpdateNutzerException`), eine `Admin`-E-Mail-Adresse leer zu setzen oder ein Admin-Konto ohne
  E-Mail anzulegen.
- **Recovery (Rettungsweg für bereits bestehende Konten ohne E-Mail):** `AdminService#resetPassword`
  (`POST /api/admin/nutzer/{id}/reset-password`) setzt das Passwort eines beliebigen Nutzers direkt,
  ohne E-Mail-Bestätigung - ein anderer `ADMIN` kann damit ein ausgesperrtes Admin-Konto
  wiederherstellen. Voraussetzung ist, dass mindestens ein zweiter Admin noch Zugriff hat.

### Rate-Limiting (Login & Forgot-Password)

- `LoginRateLimiterService` und `ForgotPasswordRateLimiterService` (beide `domain/service`) sperren
  eine anfragende IP nach zu vielen Versuchen innerhalb eines Zeitfensters (konfigurierbar via
  `app.security.login-rate-limit.*` / `app.security.forgot-password-rate-limit.*`) - Antwort dann
  `429 Too Many Requests` mit `Retry-After`-Header.
- Bewusst pro **IP**, nicht pro Anmeldename: sonst könnte ein Angreifer gezielt das Kontingent
  eines fremden Kontos aufbrauchen und dessen Besitzer aussperren.
- In-Memory (`ConcurrentHashMap`), passend für den Ein-Instanz-Betrieb der Debian-Pakete - nach
  einem Neustart sind alle Zähler zurückgesetzt.
- **Wichtige Falle:** Der `Retry-After`-Header ist kein CORS-safelisted Response-Header. Läuft das
  Frontend auf einer anderen Origin als das Backend (z.B. Dev: `5173` vs. `9000`), kann ihn das
  Frontend per `fetch`/`XHR` nur auslesen, wenn das Backend ihn explizit über
  `quarkus.http.cors.exposed-headers=Retry-After` freigibt (siehe `application.properties`) - ohne
  diese Freigabe zeigt das Frontend nur eine generische Wartemeldung, auch wenn der Header im
  tatsächlichen HTTP-Response bereits korrekt gesetzt ist.

### JWT-Invalidierung bei Passwort-Reset

- Ein Passwort-Reset (`AuthResource#resetPassword` per Token, oder der Admin-Rettungsweg
  `AdminService#resetPassword`) ändert nur den Passwort-Hash - ein bereits ausgestelltes JWT bliebe
  ohne weitere Maßnahme bis zu seinem regulären Ablauf (4h) gültig, selbst wenn es einem
  Angreifer gehört.
- `TokenInvalidationService` (`domain/service`) merkt sich pro Anmeldename einen
  "ungültig-vor"-Zeitstempel; beide Reset-Pfade rufen `invalidateTokensIssuedBefore(loginName)` auf,
  nachdem der neue Hash gespeichert wurde.
- Durchgesetzt wird das über `TokenInvalidationAugmentor` (`adapter/in/web`), einen
  `SecurityIdentityAugmentor`, der bei **jeder** authentifizierten Anfrage die `iat`-Claim des
  Tokens gegen diesen Zeitstempel prüft und das Token sonst ablehnt (401) - die von Quarkus
  vorgesehene Stelle, um ein signatur-gültiges Token nachträglich zu verwerfen.
- Der Zeitstempel wird auf volle Sekunden abgerundet, da `iat` nur Sekundenpräzision hat - sonst
  könnte ein direkt im Anschluss (noch in derselben Sekunde) neu ausgestelltes Token fälschlich
  als "davor" gelten.
- Ebenfalls In-Memory und damit denselben Neustart-Tradeoff wie beim Rate-Limiting eingehend.
