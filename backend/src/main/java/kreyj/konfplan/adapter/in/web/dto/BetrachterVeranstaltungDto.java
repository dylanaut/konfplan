package kreyj.konfplan.adapter.in.web.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;

import java.time.LocalDateTime;

@RegisterForReflection
public class BetrachterVeranstaltungDto {
    public Long id;
    public String name;
    public LocalDateTime beginntAm;
    public LocalDateTime endetAm;
}
