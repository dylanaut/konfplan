package kreyj.konfplan.adapter.in.web.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import kreyj.konfplan.persistence.Passwortrichtlinie;

@RegisterForReflection
public class PasswortrichtlinieDto {
    public String rolle;
    /** true, wenn für diese Rolle nichts konfiguriert ist und daher {@link Passwortrichtlinie#STANDARD} gilt. */
    public boolean istStandard;
    public int minLaenge;
    public Integer maxLaenge;
    public boolean erfordertGrossbuchstabe;
    public boolean erfordertKleinbuchstabe;
    public boolean erfordertZiffer;
    public boolean erfordertSonderzeichen;
    public boolean nurZiffern;


    public static PasswortrichtlinieDto from(String rolle, Passwortrichtlinie konfiguriert) {
        Passwortrichtlinie quelle = null != konfiguriert ? konfiguriert : Passwortrichtlinie.STANDARD;

        PasswortrichtlinieDto dto = new PasswortrichtlinieDto();
        dto.rolle = rolle;
        dto.istStandard = null == konfiguriert;
        dto.minLaenge = quelle.getMinLaenge();
        dto.maxLaenge = quelle.getMaxLaenge();
        dto.erfordertGrossbuchstabe = quelle.isErfordertGrossbuchstabe();
        dto.erfordertKleinbuchstabe = quelle.isErfordertKleinbuchstabe();
        dto.erfordertZiffer = quelle.isErfordertZiffer();
        dto.erfordertSonderzeichen = quelle.isErfordertSonderzeichen();
        dto.nurZiffern = quelle.isNurZiffern();
        return dto;
    }
}
