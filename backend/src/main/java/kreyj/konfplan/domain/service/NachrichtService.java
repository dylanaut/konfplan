package kreyj.konfplan.domain.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotFoundException;
import kreyj.konfplan.domain.exception.BusinessException;
import kreyj.konfplan.persistence.Nachricht;
import kreyj.konfplan.persistence.NachrichtKategorie;
import kreyj.konfplan.persistence.Nutzer;
import kreyj.konfplan.persistence.Organisator;
import kreyj.konfplan.persistence.Planungsergebnis;
import kreyj.konfplan.persistence.Prioritaet;
import kreyj.konfplan.persistence.Teilnehmer;
import kreyj.konfplan.persistence.Veranstaltung;
import kreyj.konfplan.persistence.Wahlvortrag;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * In-App-Postfach ("MessageBox") pro Nutzer für systemgenerierte Nachrichten. Erste Ausprägung:
 * Benachrichtigung bei Rückzug eines Wahlvortrags (siehe {@link #benachrichtigeUeberZurueckgezogenenVortrag}).
 */
@ApplicationScoped
public class NachrichtService {
    private static final DateTimeFormatter DEADLINE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    private final PlanService planService;


    public NachrichtService(PlanService planService) {
        this.planService = planService;
    }


    /**
     * @param absender JWT-Principal-Name des Verfassers (analog Planungsergebnis#ersteller), oder
     *                 {@code null} für eine systemgenerierte Nachricht ("System").
     */
    @Transactional
    public Nachricht sendeNachricht(Nutzer empfaenger, String titel, String inhalt, NachrichtKategorie kategorie, Long veranstaltungId, String absender) {
        Nachricht nachricht = new Nachricht();
        nachricht.setEmpfaenger(empfaenger);
        nachricht.setAbsender(absender);
        nachricht.setTitel(titel);
        nachricht.setInhalt(inhalt);
        nachricht.setKategorie(kategorie);
        nachricht.setVeranstaltungId(veranstaltungId);
        nachricht.setErstelltAm(LocalDateTime.now());
        nachricht.persist();
        return nachricht;
    }


    /**
     * Sendet eine manuell von einem Organisator verfasste Nachricht an mehrere ausgewählte
     * Empfänger - jeder muss tatsächlich Organisator, Teilnehmer oder Referent DIESER
     * Veranstaltung sein (kein beliebiger Nutzer-Zugriff über die ID).
     */
    @Transactional
    public void sendeAnAusgewaehlte(Veranstaltung veranstaltung, List<Long> empfaengerIds, String titel, String inhalt, String absender) {
        Set<Long> erlaubteIds = new HashSet<>();
        veranstaltung.organisatoren().forEach(n -> erlaubteIds.add(n.getId()));
        veranstaltung.teilnehmer().forEach(n -> erlaubteIds.add(n.getId()));
        veranstaltung.referenten().forEach(n -> erlaubteIds.add(n.getId()));

        for (Long empfaengerId : empfaengerIds) {
            if (!erlaubteIds.contains(empfaengerId)) {
                throw new BusinessException("Nutzer " + empfaengerId + " gehört nicht zu dieser Veranstaltung.");
            }
        }

        for (Long empfaengerId : empfaengerIds) {
            Nutzer empfaenger = Nutzer.findById(empfaengerId);
            sendeNachricht(empfaenger, titel, inhalt, NachrichtKategorie.ORGANISATOR_NACHRICHT, veranstaltung.getId(), absender);
        }
    }


    public List<Nachricht> getNachrichtenFuerNutzer(String loginName) {
        Nutzer nutzer = Nutzer.findByLoginName(loginName);
        if (null == nutzer) {
            return List.of();
        }
        return Nachricht.findFuerEmpfaenger(nutzer);
    }


    public long getUngeleseneAnzahl(String loginName) {
        Nutzer nutzer = Nutzer.findByLoginName(loginName);
        if (null == nutzer) {
            return 0;
        }
        return Nachricht.countUngelesenFuerEmpfaenger(nutzer);
    }


    @Transactional
    public void markiereAlsGelesen(String loginName, Long nachrichtId) {
        Nachricht nachricht = Nachricht.findById(nachrichtId);
        if (null == nachricht) {
            throw new NotFoundException("Nachricht mit ID " + nachrichtId + " nicht gefunden.");
        }
        if (null == nachricht.getEmpfaenger() || !nachricht.getEmpfaenger().getLoginName().equals(loginName)) {
            throw new ForbiddenException("Nachricht gehört nicht zum angemeldeten Nutzer.");
        }
        if (null == nachricht.getGelesenAm()) {
            nachricht.setGelesenAm(LocalDateTime.now());
        }
    }


    /**
     * Benachrichtigt die Organisatoren einer Veranstaltung sowie alle Teilnehmer, die für den
     * zurückgezogenen Wahlvortrag bereits eine Priorität ({@code prioWert > 0}) vergeben hatten.
     * Muss VOR dem eigentlichen Löschen des Vortrags aufgerufen werden, da die zugehörigen
     * Prioritaet-Zeilen per JPA-orphanRemoval mitgelöscht werden. Markiert ein bereits
     * existierendes Planungsergebnis als veraltet, falls der Vortrag darin tatsächlich
     * Teilnehmer zugewiesen hatte - eine spätere Voll-Neuberechnung behebt das automatisch.
     */
    @Transactional
    public void benachrichtigeUeberZurueckgezogenenVortrag(Wahlvortrag vortrag, Veranstaltung veranstaltung) {
        List<Teilnehmer> betroffeneTeilnehmer = Prioritaet.<Prioritaet>list("vortrag = ?1 and prioWert > 0", vortrag)
            .stream()
            .map(Prioritaet::getTeilnehmer)
            .toList();

        boolean planBetroffen = false;
        Planungsergebnis planungsergebnis = Planungsergebnis.getPlanungsergebnis(veranstaltung);
        if (null != planungsergebnis) {
            Planungsergebnis.MinizincResult result = planService.getMinizincResult(planungsergebnis);
            int wvIdx = indexOf(result.wahlvortrag_oids, vortrag.getId());
            if (wvIdx >= 0 && istVortragBesetzt(result, wvIdx)) {
                planBetroffen = true;
                planungsergebnis.setVeraltet(true);
            }
        }

        String organisatorInhalt = "Referent '" + vortrag.getReferent().getFullName() + "' hat den Wahlvortrag '"
            + vortrag.getTitel() + "' zurückgezogen. " + betroffeneTeilnehmer.size()
            + " Teilnehmer hatte(n) dafür bereits eine Priorität vergeben."
            + (planBetroffen
                ? " Für diese Veranstaltung existiert bereits ein Plan, der diesen Vortrag enthielt - bitte erstellen Sie den Plan neu, damit die betroffenen Teilnehmer neu verteilt werden."
                : "");
        for (Organisator organisator : veranstaltung.organisatoren()) {
            sendeNachricht(organisator, "Wahlvortrag zurückgezogen", organisatorInhalt,
                NachrichtKategorie.VORTRAG_ZURUECKGEZOGEN, veranstaltung.getId(), null);
        }

        String deadlineText = veranstaltung.getDeadlineTeilnehmer() != null
            ? DEADLINE_FORMAT.format(veranstaltung.getDeadlineTeilnehmer())
            : "der nächsten Deadline";
        String teilnehmerInhalt = "Der von dir priorisierte Wahlvortrag '" + vortrag.getTitel()
            + "' wurde zurückgezogen. Bitte vergib bis " + deadlineText
            + " eine neue Priorität für einen anderen Wahlvortrag.";
        for (Teilnehmer teilnehmer : betroffeneTeilnehmer) {
            sendeNachricht(teilnehmer, "Dein priorisierter Vortrag wurde zurückgezogen", teilnehmerInhalt,
                NachrichtKategorie.VORTRAG_ZURUECKGEZOGEN, veranstaltung.getId(), null);
        }
    }


    private static int indexOf(long[] arr, long value) {
        for (int i = 0; i < arr.length; i++) {
            if (arr[i] == value) {
                return i;
            }
        }
        return -1;
    }


    private static boolean istVortragBesetzt(Planungsergebnis.MinizincResult result, int wvIdx) {
        if (null == result.besucht) {
            return false;
        }
        for (boolean[][] proTeilnehmer : result.besucht) {
            if (wvIdx < proTeilnehmer.length) {
                for (boolean besucht : proTeilnehmer[wvIdx]) {
                    if (besucht) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
