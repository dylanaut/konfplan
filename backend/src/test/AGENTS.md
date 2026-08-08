# AGENTS.md – src/test/

## Überblick

Tests sind **Quarkus-Integrationstests** die eine vollständige Anwendungsinstanz hochfahren (mit echter SQLite-Datenbank im Speicher). Jeder Test läuft gegen die echte REST-API via RestAssured.

## Teststruktur

```
src/test/
├── java/kreyj/konfplan/resource/
│   ├── AdminResourceTest.java         # Admin-CRUD
│   ├── UserPersistenceTest.java       # User-Vererbungshierarchie + Persistenz
│   ├── UserInheritanceTest.java       # Polymorphie-Tests für User-Typen
│   ├── VeranstaltungResourceTest.java # Veranstaltungs-CRUD
│   ├── CsvImportTest.java             # CSV-Import aller Entitäten
│   └── CsvFileImportTest.java         # CSV-Import mit echten Testdaten-Dateien
└── resources/
    └── csv_import/bo_26_09/           # Reale Testdaten aus Bocholt 26.09.
        ├── referenten.csv
        ├── teilnehmer.csv
        ├── vortraege (pflicht/wahl).csv
        ├── veranstaltungen.csv
        ├── gebaeude.csv
        ├── slots.csv
        ├── raum_belegbarkeiten.csv
        ├── tn_verfuegbarkeiten.csv
        └── prioritäten.csv
```

## Pflichtstruktur für neue Tests

```java
@QuarkusTest
@TestSecurity(nutzer = "admin@test.de", roles = "ADMIN") // Authentifizierung simulieren
class MeineResourceTest {

    Long testVid;  // ID der Test-Veranstaltung

    @BeforeEach
    @Transactional
    void setup() {
        // IMMER: Datenbank in definiertem Zustand bringen
        // Reihenfolge beachten: abhängige Tabellen zuerst leeren
        Zuweisung.deleteAll();
        Prioritaet.deleteAll();
        Verfuegbarkeit.deleteAll();
        Vortrag.deleteAll();
        EventSlot.deleteAll();
        User.update("veranstaltung = null");
        Veranstaltung.deleteAll();
        User.deleteAll();
        Raum.deleteAll();
        Gebaeude.deleteAll();

        // Minimale Testdaten anlegen
        Admin admin = new Admin();
        admin.email = "admin@test.de";
        admin.persist();

        Veranstaltung v = new Veranstaltung();
        v.name = "Test Event " + System.currentTimeMillis();  // eindeutiger Name!
        v.beginntAm = LocalDateTime.of(2025, 10, 10, 9, 0);
        v.organisator = admin;
        v.persist();
        testVid = v.id;
    }

    @Test
    void testMeinEndpunkt() {
        given()
            .contentType(ContentType.JSON)
            .body("{\"name\": \"Test\"}")
        .when()
            .post("/api/meine-ressource")
        .then()
            .statusCode(200)
            .body("name", equalTo("Test"));
    }
}
```

## Regeln & Konventionen

- **`@QuarkusTest`** auf jeder Testklasse – startet den Quarkus-Server
- **`@TestSecurity`** simuliert einen eingeloggten User mit Rollen (kein echtes JWT nötig)
- **`@BeforeEach`** muss die Datenbank vollständig bereinigen – Tests sind **nicht** isoliert durch Rollback
- Reihenfolge beim Löschen: Kinder-Entitäten vor Eltern-Entitäten löschen (FK-Constraints)
- `User.update("veranstaltung = null")` vor `Veranstaltung.deleteAll()` wegen FK
- Veranstaltungs-Namen eindeutig machen (`+ System.currentTimeMillis()`) wegen UNIQUE-Constraint
- **RestAssured** für HTTP-Tests: `given().when().then()`-Pattern
- Für Transaktionen außerhalb von `@Transactional`: `QuarkusTransaction.begin()` + `.commit()`
- Assertions: JUnit 5 `Assertions.*` + Hamcrest `CoreMatchers.*`
- Please write a test for each new feature. Remember to carefully check and initialize all mandatory fields and relations for any persistent entities you create.

## CSV-Import-Tests

CSV-String direkt im Test konstruieren (für Unit-Szenarien):
```java
String csv = "Vorname;Nachname;Email;Gruppe\n" +
             "Tom;Student;tom@stud.de;10b";
given()
    .multiPart("file", "teilnehmer.csv", csv.getBytes())
.when()
    .post("/api/veranstaltungen/{vid}/teilnehmer/import", testVid)
.then()
    .statusCode(200);
```

Echte CSV-Dateien aus `src/test/resources/csv_import/bo_26_09/` für Integrations-Szenarien:
```java
File csvFile = new File("src/test/resources/csv_import/bo_26_09/referenten.csv");
given()
    .multiPart("file", csvFile)
.when()
    .post("/api/veranstaltungen/{vid}/referenten/import", testVid)
.then()
    .statusCode(200);
```

## Verfügbare Rollen für `@TestSecurity`

| Wert         | Rolle         |
|--------------|---------------|
| `"ADMIN"`    | Admin-Zugriff |
| `"REFERENT"` | Referent      |
| `"TEILNEHMER"` | Teilnehmer  |
