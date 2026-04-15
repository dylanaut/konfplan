# AGENTS.md – entity/

## Zweck

Dieses Paket enthält alle JPA-Entitäten nach dem **Panache Active Record Pattern**. Alle Entitäten erben von `SqliteEntity` und bringen ihre Datenbankoperationen (Finder, Persist, Delete) selbst mit.

## Klassenübersicht

| Klasse              | Beschreibung                                                     |
|---------------------|------------------------------------------------------------------|
| `SqliteEntity`      | Basisklasse: `@MappedSuperclass`, Long id (IDENTITY), toString   |
| `User`              | Abstrakt; SINGLE_TABLE-Hierarchie; Quarkus Security Integration  |
| `Admin`             | User-Subtyp; organisiert Veranstaltungen                         |
| `Referent`          | User-Subtyp; hält Vorträge; hat Biografie, JobRole, Organisation |
| `Teilnehmer`        | User-Subtyp; nimmt an Vorträgen teil; hat Gruppe                |
| `Veranstaltung`     | Zentrale Entität; hat Slots, Gebäude, User                       |
| `Vortrag`           | Abstrakt; SINGLE_TABLE; Polymorphie via vortrag_typ              |
| `Pflichtvortrag`    | Pflichtslot + Pflichtraum fest zugewiesen                        |
| `Wahlvortrag`       | Wiederholbar, hat Zielgruppe und max. Wiederholungen             |
| `EventSlot`         | Zeitfenster (startTime/endTime) innerhalb einer Veranstaltung    |
| `Gebaeude`          | Gebäude mit Typ, Adresse und Räumen                              |
| `Raum`              | Raum mit Kapazität und Etage; gehört zu einem Gebäude            |
| `Zuweisung`         | Verbindet Teilnehmer + Vortrag + Slot + Raum                     |
| `Prioritaet`        | Präferenz eines Teilnehmers für einen Wahlvortrag (Integer-Wert) |
| `Verfuegbarkeit`    | Gibt an, ob Teilnehmer/Raum in einem Slot verfügbar ist          |

## Pflichtregeln beim Anlegen neuer Entitäten

```java
// Pflichtstruktur für jede neue Entität:
@Entity
@Table(name = "MeineEntitaet")
public class MeineEntitaet extends SqliteEntity {

    @Version                          // PFLICHT: Optimistic Locking
    public Long version;

    // Felder: public (kein Lombok, kein privater Boilerplate)
    @Column(nullable = false)
    public String name;

    // LocalDateTime immer mit Converter:
    @Convert(converter = LocalDateTimeConverter.class)
    public LocalDateTime zeitpunkt;

    // Foreign Keys: columnDefinition = "INTEGER" explizit angeben
    @ManyToOne
    @JoinColumn(name = "veranstaltung_id", columnDefinition = "INTEGER")
    public Veranstaltung veranstaltung;
}
```

## Vererbungshierarchien

### User (SINGLE_TABLE, Diskriminator: `role`)
```
User (abstrakt)
├── Admin       → role = "ADMIN"
├── Referent    → role = "REFERENT"
└── Teilnehmer  → role = "TEILNEHMER"
```
- Jackson-Polymorphie via `@JsonTypeInfo` + `@JsonSubTypes` auf `User`
- Quarkus Security: `@UserDefinition`, `@Username`, `@Password`, `@Roles`

### Vortrag (SINGLE_TABLE, Diskriminator: `vortrag_typ`)
```
Vortrag (abstrakt)
├── Pflichtvortrag  → vortrag_typ = "PFLICHT"
└── Wahlvortrag     → vortrag_typ = "WAHL"
```
- Abstrakte Methode `istPflicht()` muss in Subtypen implementiert werden
- Jackson-Polymorphie via `@JsonTypeInfo(property = "vortrag_typ")`

## Wichtige Hinweise

- **Keine** `GenerationType.SEQUENCE` oder `GenerationType.TABLE` verwenden – nur `IDENTITY`
- **Kein** `@Column(name = "...")` nötig wenn Feldname = Spaltenname (Panache-Konvention)
- Bei bidirektionalen Relationen `@JsonIgnoreProperties` setzen um zyklische Serialisierung zu vermeiden
- `@Version` schützt vor Lost Updates – bei OptimisticLockException HTTP 409 zurückgeben
- Änderungen am Schema erfordern eine neue Flyway-Migration (kein `hbm2ddl`)
- `converter/LocalDateTimeConverter.java` für alle `LocalDateTime`-Felder verwenden (SQLite speichert Timestamps als ISO-String)
