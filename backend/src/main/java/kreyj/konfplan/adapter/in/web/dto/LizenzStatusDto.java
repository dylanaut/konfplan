package kreyj.konfplan.adapter.in.web.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import kreyj.konfplan.domain.lizenz.LizenzClaims;
import kreyj.konfplan.domain.lizenz.LizenzStatus;
import kreyj.konfplan.persistence.Lizenzzustand;

import java.time.Instant;
import java.util.Set;

@RegisterForReflection
public class LizenzStatusDto {
    public String zustand;
    /** Nur bei zustand EINGESCHRAENKT: warum die Lizenz abgelehnt wurde. */
    public String grund;
    /** Ob die Schreibsperre in dieser Instanz scharf geschaltet ist. */
    public boolean durchsetzung;
    public String lizenzId;
    public String kundenId;
    public Integer maxInstanzen;
    public Set<String> features;
    public String typ;
    public Instant gueltigBis;
    /** Ob ein Lizenzserver konfiguriert ist (Online-Aktivierung moeglich). */
    public boolean onlineMoeglich;
    /** Nur Online-Lizenzen: ab hier ist die Revalidierung faellig, danach laeuft die Grace-Period bis gueltigBis. */
    public Instant naechsteRevalidierung;
    public Instant revalidierungVersuch;
    public String revalidierungFehler;

    public static LizenzStatusDto from(LizenzStatus status, boolean durchsetzung, boolean onlineMoeglich, Lizenzzustand z) {
        LizenzStatusDto dto = new LizenzStatusDto();
        dto.zustand = status.zustand().name();
        dto.grund = null == status.grund() ? null : status.grund().name();
        dto.durchsetzung = durchsetzung;
        dto.onlineMoeglich = onlineMoeglich;
        dto.revalidierungVersuch = z.getRevalidierungVersuch();
        dto.revalidierungFehler = z.getRevalidierungFehler();
        LizenzClaims c = status.claims();
        if (null != c) {
            dto.lizenzId = c.lizenzId();
            dto.kundenId = c.kundenId();
            dto.maxInstanzen = c.maxInstanzen();
            dto.features = c.features();
            dto.typ = c.typ().name();
            dto.gueltigBis = c.gueltigBis();
            dto.naechsteRevalidierung = c.naechsteRevalidierung();
        }
        return dto;
    }
}
