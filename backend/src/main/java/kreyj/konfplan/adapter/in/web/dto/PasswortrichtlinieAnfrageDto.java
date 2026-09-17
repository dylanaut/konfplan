package kreyj.konfplan.adapter.in.web.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public class PasswortrichtlinieAnfrageDto {
    public int minLaenge;
    public Integer maxLaenge;
    public boolean erfordertGrossbuchstabe;
    public boolean erfordertKleinbuchstabe;
    public boolean erfordertZiffer;
    public boolean erfordertSonderzeichen;
    public boolean nurZiffern;
}
