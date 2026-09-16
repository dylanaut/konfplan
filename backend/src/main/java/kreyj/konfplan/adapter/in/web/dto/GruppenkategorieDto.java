package kreyj.konfplan.adapter.in.web.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import kreyj.konfplan.persistence.Gruppenkategorie;

import java.util.Comparator;
import java.util.List;

@RegisterForReflection
public class GruppenkategorieDto {
    public Long id;
    public String name;
    public boolean mehrwertig;
    public boolean pflicht;
    public List<GruppenkategorieWertDto> werte;


    public GruppenkategorieDto() {
    }


    public GruppenkategorieDto(Long id, String name, boolean mehrwertig, boolean pflicht, List<GruppenkategorieWertDto> werte) {
        this.id = id;
        this.name = name;
        this.mehrwertig = mehrwertig;
        this.pflicht = pflicht;
        this.werte = werte;
    }


    public static GruppenkategorieDto from(Gruppenkategorie kategorie) {
        List<GruppenkategorieWertDto> werte = kategorie.getWerte().stream()
            .map(GruppenkategorieWertDto::from)
            .sorted(Comparator.comparing(w -> w.wert))
            .toList();
        return new GruppenkategorieDto(kategorie.getId(), kategorie.getName(), kategorie.isMehrwertig(), kategorie.isPflicht(), werte);
    }
}
