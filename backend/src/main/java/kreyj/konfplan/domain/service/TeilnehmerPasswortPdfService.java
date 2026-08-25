package kreyj.konfplan.domain.service;

import io.quarkus.qute.Location;
import io.quarkus.qute.Template;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import kreyj.konfplan.domain.exception.BusinessException;
import kreyj.konfplan.persistence.ProtokollKategorie;
import kreyj.konfplan.persistence.Teilnehmer;
import kreyj.konfplan.persistence.Veranstaltung;
import kreyj.konfplan.util.PasswordGenerator;
import org.jboss.logging.Logger;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Erzeugt fuer eine ausgewaehlte Menge von Teilnehmern je ein neues temporaeres Passwort (setzt
 * dabei denselben Keycloak-Zwangswechsel-Mechanismus wie der bestehende Einzel-Reset, siehe
 * {@link KeycloakUserProvisioningService#resetPassword}) und fasst alle Login-Namen +
 * Passwoerter in einer passwortverschluesselten PDF-Tabelle zusammen. Die erzeugten Passwoerter
 * selbst werden nirgendwo persistiert oder geloggt - nur in der PDF und bei Keycloak.
 */
@ApplicationScoped
public class TeilnehmerPasswortPdfService {
    private static final Logger LOG = Logger.getLogger(TeilnehmerPasswortPdfService.class);

    private final KeycloakUserProvisioningService keycloakUserProvisioningService;
    private final ProtokollService protokollService;
    private final PdfService pdfService;
    private final Template teilnehmerPasswortReportTemplate;

    public TeilnehmerPasswortPdfService(KeycloakUserProvisioningService keycloakUserProvisioningService,
                                         ProtokollService protokollService,
                                         PdfService pdfService,
                                         @Location("reports/teilnehmerPasswortReport") Template teilnehmerPasswortReportTemplate) {
        this.keycloakUserProvisioningService = keycloakUserProvisioningService;
        this.protokollService = protokollService;
        this.pdfService = pdfService;
        this.teilnehmerPasswortReportTemplate = teilnehmerPasswortReportTemplate;
    }


    private record Kandidat(String loginName, String fullName, Teilnehmer nutzer) {
    }


    private record ValidierungsErgebnis(String veranstaltungName, List<Kandidat> kandidaten) {
    }


    @Transactional
    protected ValidierungsErgebnis ladeUndValidiere(Long veranstaltungId, List<Long> nutzerIds) {
        Veranstaltung veranstaltung = Veranstaltung.findById(veranstaltungId);
        if (null == veranstaltung) {
            throw new BusinessException("Veranstaltung nicht gefunden.");
        }

        List<Teilnehmer> teilnehmerListe = Teilnehmer.find(
            "SELECT DISTINCT t FROM Teilnehmer t JOIN t.veranstaltungen v WHERE t.id IN ?1 AND v.id = ?2",
            nutzerIds, veranstaltungId).list();
        if (teilnehmerListe.size() != nutzerIds.size()) {
            throw new BusinessException("Mindestens ein Teilnehmer gehört nicht zu dieser Veranstaltung oder wurde nicht gefunden.");
        }

        List<Kandidat> ergebnis = new ArrayList<>();
        for (Teilnehmer t : teilnehmerListe) {
            ergebnis.add(new Kandidat(t.getLoginName(), t.getFirstName() + " " + t.getLastName(), t));
        }
        // Name waehrend der noch offenen Session lesen - t.getVeranstaltungen() ist eine Lazy-
        // Collection, die nach Ende dieser @Transactional-Methode nicht mehr zugreifbar ist.
        return new ValidierungsErgebnis(veranstaltung.getName(), ergebnis);
    }


    public TeilnehmerPasswortPdfResult resetPasswordsAndGeneratePdf(Long veranstaltungId, List<Long> nutzerIds, String pdfPassword) {
        if (null == nutzerIds || nutzerIds.isEmpty()) {
            throw new BusinessException("Es wurde kein Teilnehmer ausgewählt.");
        }
        if (null == pdfPassword || pdfPassword.length() < 8) {
            throw new BusinessException("Das PDF-Passwort muss mindestens 8 Zeichen lang sein.");
        }

        ValidierungsErgebnis validierung = ladeUndValidiere(veranstaltungId, nutzerIds);
        List<Kandidat> kandidaten = validierung.kandidaten();
        String veranstaltungName = validierung.veranstaltungName();

        List<Map<String, String>> erfolgsZeilen = new ArrayList<>();
        List<String> fehlgeschlagen = new ArrayList<>();

        for (Kandidat k : kandidaten) {
            String neuesPasswort = PasswordGenerator.generate();
            try {
                keycloakUserProvisioningService.resetPassword(k.nutzer(), neuesPasswort);
                erfolgsZeilen.add(Map.of("fullName", k.fullName(), "loginName", k.loginName(), "password", neuesPasswort));
            } catch (Exception e) {
                LOG.warn("Passwort-Reset für '" + k.loginName() + "' fehlgeschlagen: " + e.getMessage());
                fehlgeschlagen.add(k.loginName());
            }
        }

        if (erfolgsZeilen.isEmpty()) {
            throw new BusinessException("Passwort-Reset für alle ausgewählten Teilnehmer fehlgeschlagen: "
                + String.join(", ", fehlgeschlagen));
        }

        String html = teilnehmerPasswortReportTemplate
            .data("veranstaltungName", veranstaltungName)
            .data("erzeugtAm", LocalDateTime.now())
            .data("eintraege", erfolgsZeilen)
            .render();

        byte[] pdf;
        try {
            pdf = pdfService.renderEncryptedPdf(html, pdfPassword);
        } catch (Exception e) {
            throw new BusinessException("PDF-Erzeugung fehlgeschlagen: " + e.getMessage());
        }

        protokollService.log(ProtokollKategorie.SECURITY,
            "Temporäre Passwörter per PDF erzeugt",
            erfolgsZeilen.size() + " Teilnehmer erfolgreich zurückgesetzt"
                + (fehlgeschlagen.isEmpty() ? "" : ("; fehlgeschlagen: " + String.join(", ", fehlgeschlagen))),
            null, veranstaltungId);

        return new TeilnehmerPasswortPdfResult(pdf, fehlgeschlagen);
    }
}
