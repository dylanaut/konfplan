package kreyj.konfplan.adapter.in.web.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import kreyj.konfplan.persistence.GruppenkategorieWert;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@RegisterForReflection
@NoArgsConstructor
@AllArgsConstructor
public class GruppenkategorieWertDto {
    public Long id;
    public String wert;


    public static GruppenkategorieWertDto from(GruppenkategorieWert wert) {
        return new GruppenkategorieWertDto(wert.getId(), wert.getWert());
    }
}
