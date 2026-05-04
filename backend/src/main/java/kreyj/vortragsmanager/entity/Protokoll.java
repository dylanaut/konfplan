package kreyj.vortragsmanager.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import kreyj.vortragsmanager.entity.converter.LocalDateTimeConverter;

import java.time.LocalDateTime;

@Entity
public class Protokoll extends IdEntity {

    @Column(nullable = false)
    @Convert(converter = LocalDateTimeConverter.class)
    public LocalDateTime zeitpunkt;

    @Column(nullable = false)
    public String akteur; // E-Mail des Nutzers oder "SYSTEM"

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public ProtokollKategorie kategorie;

    @Column(nullable = false)
    public String ereignis; // Kurzbeschreibung

    @Column(columnDefinition = "TEXT")
    public String details; // Optional: JSON oder längerer Text

    public Long referenzId; // Optional: ID der betroffenen Entität (z.B. veranstaltungId)

    // Panache Active Record Pattern: Statische Methoden für Finder etc.
    // Keine Getter/Setter für Public Fields, außer wo nötig.
}
