package kreyj.konfplan.adapter.in.web.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import kreyj.konfplan.persistence.Wartungshinweis;

import java.time.LocalDateTime;

@RegisterForReflection
public class WartungshinweisDto {
    public LocalDateTime startZeitpunkt;
    public LocalDateTime endeZeitpunkt;

    public static WartungshinweisDto leer() {
        return new WartungshinweisDto();
    }

    public static WartungshinweisDto from(Wartungshinweis w) {
        WartungshinweisDto dto = new WartungshinweisDto();
        dto.startZeitpunkt = w.getStartZeitpunkt();
        dto.endeZeitpunkt = w.getEndeZeitpunkt();
        return dto;
    }
}
