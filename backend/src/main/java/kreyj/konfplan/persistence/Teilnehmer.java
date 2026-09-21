package kreyj.konfplan.persistence;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

import java.util.List;

/**
 * Primärrolle Teilnehmer - alle Felder/Methoden (gruppen, gruppenwerte, prioritaeten, neigungen,
 * ...) leben seit #751 auf {@link Nutzer}, damit eine Person auch mit TEILNEHMER als Zusatzrolle
 * vollwertig als Teilnehmer handeln kann.
 */
@Entity
@DiscriminatorValue("TEILNEHMER")
public class Teilnehmer extends Nutzer {

    public Teilnehmer() {
        this.setRole("TEILNEHMER");
    }


    /**
     * Bewusst weiterhin auf die Primärrolle beschränkt (nicht nach #751 auf {@link Nutzer}
     * gehoben): wird ausschließlich von der Pflichtvortrag-/Planungslogik verwendet
     * ({@link Pflichtvortrag}, {@code PlanService}, {@code OrganisatorService}), die in Stufe 1
     * von #751 bewusst nur die Primärrolle berücksichtigt (siehe Plan-Dokumentation zu #751) -
     * eine Person mit TEILNEHMER nur als Zusatzrolle taucht hier absichtlich nicht auf.
     */
    public static List<Teilnehmer> getGruppenTeilnehmer(String gruppenName, Veranstaltung veranstaltung) {
        return Teilnehmer.find("SELECT DISTINCT tn from Teilnehmer tn " +
                " JOIN tn.veranstaltungen v " +
                " WHERE v = ?2 AND tn.isActive = true " +
                " AND (?1 MEMBER OF tn.gruppen " +
                "      OR EXISTS (SELECT gkw FROM GruppenkategorieWert gkw " +
                "                 WHERE gkw MEMBER OF tn.teilnehmerGruppenwerte " +
                "                 AND gkw.wert = ?1 AND gkw.gruppenkategorie.veranstaltung = v))",
            gruppenName, veranstaltung).list();
    }
}
