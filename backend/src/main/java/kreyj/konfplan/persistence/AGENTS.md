# AGENTS.md – entity/

## Zweck

Dieses Paket enthält alle JPA-Entitäten nach dem **Panache Active Record Pattern**. Alle Entitäten erben von `VersionedEntity` und bringen ihre Datenbankoperationen (Finder, Persist, Delete) selbst mit.

## Klassenübersicht

| Klasse              | Beschreibung                                                     |
|---------------------|------------------------------------------------------------------|
| `VersionedEntity`   | Basisklasse: `@MappedSuperclass`, Long id (IDENTITY), `@Version Long version`. |
| `Nutzer`            | Abstrakt; SINGLE_TABLE-Hierarchie; Quarkus Security Integration. Erstellt automatische Verfügbarkeiten bei Veranstaltungszuweisung. |
| `Admin`             | Nutzer-Subtyp; organisiert Veranstaltungen.                      |
| `Referent`          | Nutzer-Subtyp; hält Vorträge; hat JobRole, Organisation.         |
| `Teilnehmer`        | Nutzer-Subtyp; nimmt an Vorträgen teil; hat Gruppe.              |
| `Veranstaltung`     | Zentrale Entität; hat Slots, Gebäude, Nutzer; besitzt Deadlines für Rollen. |
| `Vortrag`           | Abstrakt; SINGLE_TABLE; Polymorphie via vortrag_typ.             |
| `Pflichtvortrag`    | Pflichtslot + Pflichtraum fest zugewiesen.                       |
| `Wahlvortrag`       | Wiederholbar, hat max. Wiederholungen.                           |
| `EventSlot`         | Zeitfenster innerhalb einer Veranstaltung.                       |
| `Gebaeude`          | Gebäude mit Typ, Adresse und Räumen.                             |
| `Raum`              | Raum mit Kapazität und Etage.                                    |
| `Zuweisung`         | Verbindet Teilnehmer + Vortrag + Slot + Raum.                    |
| `Prioritaet`        | Präferenz eines Teilnehmers (Ranking 1-10, 10 = höchste, 0 = keine Präferenz). |
| `Verfuegbarkeit`    | Verfügbarkeit eines Nutzers in einem Slot.                       |
| `RaumVerfuegbarkeit`| Verfügbarkeit eines Raumes in einem Slot.                        |

## Pflichtregeln beim Anlegen neuer Entitäten

```java
// Pflichtstruktur für jede neue Entität:
@Entity
@Table(name = "MeineEntitaet")
public class MeineEntitaet extends VersionedEntity {

    // version ist bereits in VersionedEntity definiert!

    // Felder: public (kein Lombok, kein privater Boilerplate)
    @Column(nullable = false)
    public String name;

    // LocalDateTime immer mit Converter:
    @Convert(converter = LocalDateTimeConverter.class)
    public LocalDateTime zeitpunkt;

    @ManyToOne
    @JoinColumn(name = "veranstaltung_id")
    public Veranstaltung veranstaltung;
}
```

## Vererbungshierarchien

### Nutzer (SINGLE_TABLE, Diskriminator: `role`)
- Jackson-Polymorphie via `@JsonTypeInfo` + `@JsonSubTypes`.
- Quarkus Security: `@UserDefinition`, `@Username`, `@Password`, `@Roles`.
- Methoden `addVeranstaltung` und `removeVeranstaltung` verwalten automatisch die `Verfuegbarkeit`-Datensätze für alle Slots der Veranstaltung.

### Vortrag (SINGLE_TABLE, Diskriminator: `vortrag_typ`)
- Jackson-Polymorphie via `@JsonTypeInfo(property = "vortrag_typ")`.

## Wichtige Hinweise

- **Primärschlüssel**: Immer `GenerationType.IDENTITY`.
- **Bidirektionale Relationen**: `@JsonIgnoreProperties` setzen um Endlosschleifen bei der Serialisierung zu vermeiden.
- **Optimistic Locking**: `@Version` schützt vor Lost Updates.
- **Konvertierung**: `converter/LocalDateTimeConverter.java` für alle `LocalDateTime`-Felder verwenden.
