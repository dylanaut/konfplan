package kreyj.konfplan.domain.service;

import com.opencsv.CSVWriterBuilder;
import com.opencsv.ICSVWriter;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import kreyj.konfplan.domain.exception.BusinessException;
import kreyj.konfplan.persistence.GruppenkategorieWert;
import kreyj.konfplan.persistence.ProtokollKategorie;
import kreyj.konfplan.persistence.Teilnehmer;
import kreyj.konfplan.persistence.Veranstaltung;
import kreyj.konfplan.util.PasswordGenerator;
import org.jboss.logging.Logger;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Erzeugt fuer eine ausgewaehlte Menge von Teilnehmern je ein neues temporaeres Passwort (setzt
 * dabei denselben Keycloak-Zwangswechsel-Mechanismus wie der bestehende Einzel-Reset, siehe
 * {@link KeycloakUserProvisioningService#resetPassword}) und fasst alle Login-Namen + Passwoerter
 * in einer passwortverschluesselten ZIP-Datei mit einer CSV-Tabelle darin zusammen. Die erzeugten
 * Passwoerter selbst werden nirgendwo persistiert oder geloggt - nur in der ZIP und bei Keycloak.
 */
@ApplicationScoped
public class TeilnehmerPasswortZipService {
    private static final Logger LOG = Logger.getLogger(TeilnehmerPasswortZipService.class);
    private static final byte[] UTF8_BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
    private static final String CSV_DATEINAME = "teilnehmer_passwoerter.csv";

    private final KeycloakUserProvisioningService keycloakUserProvisioningService;
    private final ProtokollService protokollService;
    private final ZipService zipService;

    public TeilnehmerPasswortZipService(KeycloakUserProvisioningService keycloakUserProvisioningService,
                                         ProtokollService protokollService,
                                         ZipService zipService) {
        this.keycloakUserProvisioningService = keycloakUserProvisioningService;
        this.protokollService = protokollService;
        this.zipService = zipService;
    }


    protected record Kandidat(String loginName, String fullName, Map<String, List<String>> gruppenwerteByKategorie, Teilnehmer nutzer) {
    }


    private record ReportZeile(String fullName, String loginName, String password, Map<String, List<String>> gruppenwerteByKategorie) {
    }


    @Transactional
    protected List<Kandidat> ladeUndValidiere(Long veranstaltungId, List<Long> nutzerIds) {
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
            Map<String, List<String>> gruppenwerteByKategorie = t.getGruppenwerte().stream()
                .collect(Collectors.groupingBy(w -> w.getGruppenkategorie().getName(),
                    Collectors.mapping(GruppenkategorieWert::getWert, Collectors.toList())));
            ergebnis.add(new Kandidat(t.getLoginName(), t.getLastName() + ", " + t.getFirstName(),
                gruppenwerteByKategorie, t));
        }
        return ergebnis;
    }


    public TeilnehmerPasswortZipResult resetPasswordsAndGenerateZip(Long veranstaltungId, List<Long> nutzerIds, String zipPassword) {
        if (null == nutzerIds || nutzerIds.isEmpty()) {
            throw new BusinessException("Es wurde kein Teilnehmer ausgewählt.");
        }
        if (null == zipPassword || zipPassword.length() < 8) {
            throw new BusinessException("Das ZIP-Passwort muss mindestens 8 Zeichen lang sein.");
        }

        List<Kandidat> kandidaten = ladeUndValidiere(veranstaltungId, nutzerIds);

        List<ReportZeile> erfolgsZeilen = new ArrayList<>();
        List<String> fehlgeschlagen = new ArrayList<>();

        for (Kandidat k : kandidaten) {
            String neuesPasswort = PasswordGenerator.generate();
            try {
                keycloakUserProvisioningService.resetPassword(k.nutzer(), neuesPasswort);
                erfolgsZeilen.add(new ReportZeile(k.fullName(), k.loginName(), neuesPasswort, k.gruppenwerteByKategorie()));
            } catch (Exception e) {
                LOG.warn("Passwort-Reset für '" + k.loginName() + "' fehlgeschlagen: " + e.getMessage());
                fehlgeschlagen.add(k.loginName());
            }
        }

        if (erfolgsZeilen.isEmpty()) {
            throw new BusinessException("Passwort-Reset für alle ausgewählten Teilnehmer fehlgeschlagen: "
                + String.join(", ", fehlgeschlagen));
        }

        byte[] zip;
        try {
            zip = zipService.encryptSingleEntry(CSV_DATEINAME, buildCsv(erfolgsZeilen), zipPassword);
        } catch (IOException e) {
            throw new BusinessException("ZIP-Erzeugung fehlgeschlagen: " + e.getMessage());
        }

        protokollService.log(ProtokollKategorie.SECURITY,
            "Temporäre Passwörter per ZIP erzeugt",
            erfolgsZeilen.size() + " Teilnehmer erfolgreich zurückgesetzt"
                + (fehlgeschlagen.isEmpty() ? "" : ("; fehlgeschlagen: " + String.join(", ", fehlgeschlagen))),
            null, veranstaltungId);

        return new TeilnehmerPasswortZipResult(zip, fehlgeschlagen);
    }


    private byte[] buildCsv(List<ReportZeile> zeilen) throws IOException {
        List<String> kategorieNamen = zeilen.stream()
            .flatMap(z -> z.gruppenwerteByKategorie().keySet().stream())
            .distinct()
            .sorted()
            .toList();

        List<String> header = new ArrayList<>(List.of("Name", "Login", "Temporäres Passwort"));
        header.addAll(kategorieNamen);

        try (ByteArrayOutputStream os = new ByteArrayOutputStream()) {
            os.write(UTF8_BOM);
            try (Writer writer = new OutputStreamWriter(os, StandardCharsets.UTF_8);
                 ICSVWriter csvWriter = new CSVWriterBuilder(writer).withSeparator(';').build()) {
                csvWriter.writeNext(header.toArray(new String[0]));
                for (ReportZeile z : zeilen) {
                    List<String> zeile = new ArrayList<>(List.of(z.fullName(), z.loginName(), z.password()));
                    for (String kategorieName : kategorieNamen) {
                        zeile.add(String.join(", ", z.gruppenwerteByKategorie().getOrDefault(kategorieName, List.of())));
                    }
                    csvWriter.writeNext(zeile.toArray(new String[0]));
                }
                csvWriter.flush();
            }
            return os.toByteArray();
        }
    }
}
