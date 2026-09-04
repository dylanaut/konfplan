package kreyj.konfplan.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.ManyToOne;
import kreyj.konfplan.persistence.converter.LocalDateTimeConverter;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@NoArgsConstructor
@Getter
@Setter
public class Verbesserungsvorschlag extends VersionedEntity {

    @Column(nullable = false)
    private String titel;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String beschreibung;

    @Column(nullable = false)
    @Convert(converter = LocalDateTimeConverter.class)
    private LocalDateTime erstelltAm;

    @ManyToOne
    private Nutzer ersteller;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private VorschlagStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Dringlichkeit dringlichkeit;

    @Column(nullable = false)
    private String release;

    public static List<Verbesserungsvorschlag> listAll() {
        return list("order by erstelltAm desc");
    }
}
