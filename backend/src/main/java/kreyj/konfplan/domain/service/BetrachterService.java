package kreyj.konfplan.domain.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import kreyj.konfplan.adapter.in.web.dto.NutzerVerfuegbarkeitDto;
import kreyj.konfplan.adapter.in.web.dto.ZuweisungDto;
import kreyj.konfplan.persistence.Betrachter;
import kreyj.konfplan.persistence.IdEntity;
import kreyj.konfplan.persistence.NutzerVerfuegbarkeit;
import kreyj.konfplan.persistence.Teilnehmer;
import kreyj.konfplan.persistence.Veranstaltung;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Kapselt die Lese-Logik für die Rolle Betrachter (siehe #718): auf die einem Betrachter
 * zugewiesenen {@link kreyj.konfplan.persistence.GruppenkategorieWert}-Werte beschränkte Sicht
 * auf Teilnehmer, deren Verfügbarkeiten und Buchungen einer Veranstaltung.
 */
@ApplicationScoped
public class BetrachterService {

    private final PlanService planService;


    public BetrachterService(PlanService planService) {
        this.planService = planService;
    }


    @Transactional
    public List<Teilnehmer> getSichtbareTeilnehmer(Betrachter betrachter, Veranstaltung veranstaltung) {
        if (betrachter.getGruppenwerte().isEmpty()) {
            return List.of();
        }
        return Teilnehmer.find(
            "SELECT DISTINCT tn FROM Teilnehmer tn JOIN tn.gruppenwerte gkw JOIN tn.veranstaltungen v "
                + "WHERE v = ?1 AND gkw IN ?2 AND tn.isActive = true",
            veranstaltung, betrachter.getGruppenwerte()).list();
    }


    @Transactional
    public List<NutzerVerfuegbarkeitDto> getVerfuegbarkeiten(Betrachter betrachter, Veranstaltung veranstaltung) {
        List<Long> sichtbareIds = getSichtbareTeilnehmer(betrachter, veranstaltung).stream().map(IdEntity::getId).toList();
        if (sichtbareIds.isEmpty()) {
            return List.of();
        }
        return NutzerVerfuegbarkeit.<NutzerVerfuegbarkeit>find("nutzerId in ?1 and veranstaltungId = ?2", sichtbareIds, veranstaltung.getId())
            .list().stream()
            .map(NutzerVerfuegbarkeitDto::new)
            .toList();
    }


    /**
     * Buchungen je sichtbarem Teilnehmer - absichtlich ohne ergebnisId-Parameter: ein Betrachter
     * darf nie ein unveröffentlichtes Planungsergebnis einsehen (siehe
     * {@link PlanService#getPlanFuerTeilnehmer(Teilnehmer, Veranstaltung)}, das immer nur das
     * veröffentlichte Ergebnis liefert).
     */
    @Transactional
    public Map<Long, List<ZuweisungDto>> getZuweisungen(Betrachter betrachter, Veranstaltung veranstaltung) {
        List<Teilnehmer> sichtbareTeilnehmer = getSichtbareTeilnehmer(betrachter, veranstaltung);
        return sichtbareTeilnehmer.stream()
            .collect(Collectors.toMap(IdEntity::getId, t -> planService.getPlanFuerTeilnehmer(t, veranstaltung)));
    }
}
