package kreyj.konfplan.adapter.in.web.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public class AnwesenheitCheckinResultDto {
    public boolean aktiverTermin;
    public String vortragTitel;
    public String raumName;
    public String slotZeit;

    public static AnwesenheitCheckinResultDto keinAktiverTermin(String raumName) {
        AnwesenheitCheckinResultDto dto = new AnwesenheitCheckinResultDto();
        dto.aktiverTermin = false;
        dto.raumName = raumName;
        return dto;
    }

    public static AnwesenheitCheckinResultDto mitTermin(String vortragTitel, String raumName, String slotZeit) {
        AnwesenheitCheckinResultDto dto = new AnwesenheitCheckinResultDto();
        dto.aktiverTermin = true;
        dto.vortragTitel = vortragTitel;
        dto.raumName = raumName;
        dto.slotZeit = slotZeit;
        return dto;
    }
}
