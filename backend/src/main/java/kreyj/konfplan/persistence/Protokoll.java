package kreyj.konfplan.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import kreyj.konfplan.persistence.converter.LocalDateTimeConverter;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
public class Protokoll extends IdEntity {

    @Column(nullable = false)
    @Convert(converter = LocalDateTimeConverter.class)
    private LocalDateTime zeitpunkt;

    @Column(nullable = false)
    private String akteur; // E-Mail des Nutzers oder "SYSTEM"

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProtokollKategorie kategorie;

    @Column(nullable = false)
    private String ereignis; // Kurzbeschreibung

    @Column(columnDefinition = "TEXT")
    private String details; // Optional: JSON oder längerer Text

    private Long referenzId; // Optional: ID der betroffenen Entität (z.B. veranstaltungId)

    // Panache Active Record Pattern: Statische Methoden für Finder etc.
    // Keine Getter/Setter für Public Fields, außer wo nötig.
}