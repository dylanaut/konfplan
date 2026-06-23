package kreyj.konfplan.application.devsupport;

import io.agroal.api.AgroalDataSource;
import io.quarkus.logging.Log;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.transaction.Transactional;
import kreyj.konfplan.application.service.AdminService;
import kreyj.konfplan.application.service.GebaeudeService;
import kreyj.konfplan.application.service.ReferentService;
import kreyj.konfplan.application.service.TeilnehmerService;
import kreyj.konfplan.application.service.VeranstaltungService;
import kreyj.konfplan.persistence.Veranstaltung;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@ApplicationScoped
public class DevDataInitService {
    private static final Logger LOG = Logger.getLogger(DevDataInitService.class);

    @ConfigProperty(name = "konfplan.dev-data.init", defaultValue = "false")
    boolean devInitEnabled;

    @ConfigProperty(name = "konfplan.dev-data.csv-path", defaultValue = "src/test/resources/csv_import/")
    String csvBasePath;

    @ConfigProperty(name = "konfplan.dev-data.datasets", defaultValue = "medium")
    List<String> dataSets;

    private final AgroalDataSource datasource;

    private final TeilnehmerService teilnehmerService;

    private final AdminService adminService;

    private final VeranstaltungService veranstaltungService;

    private final GebaeudeService gebaeudeService;

    private final ReferentService referentService;


    public DevDataInitService(AgroalDataSource datasource, TeilnehmerService teilnehmerService,
                              AdminService adminService, VeranstaltungService veranstaltungService,
                              GebaeudeService gebaeudeService, ReferentService referentService) {
        this.datasource = datasource;
        this.teilnehmerService = teilnehmerService;
        this.adminService = adminService;
        this.veranstaltungService = veranstaltungService;
        this.gebaeudeService = gebaeudeService;
        this.referentService = referentService;
    }


    @Transactional
    public void onStart(@Observes StartupEvent ev) {
        if (!devInitEnabled) {
            return;
        }

        try {
            String connectionUrl = datasource.getConnection().getMetaData().getURL();

            LOG.info("Starte Dev-Daten-Initialisierung für " + connectionUrl + " ...");
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }

        Set<Long> importierteVeranstaltungen = new HashSet<>();

        for (String dataSet : dataSets) {
            Log.info("Lade DataSet >>> " + dataSet + " <<<");
            try {
                Path basePath = Paths.get(csvBasePath, dataSet);

                // 1. Gebäude & Räume
                gebaeudeService.importGebaeudeWithRaeumeFromCsv(basePath.resolve("gebaeude.csv"));

                // 2. Organisatoren (Admins)
                adminService.importAdminsFromCsv(basePath.resolve("organisatoren.csv"));

                // 3. Veranstaltungen
                int anzahlVeranstaltungen = veranstaltungService.importFromCsv(basePath.resolve("veranstaltungen.csv"));

                if (anzahlVeranstaltungen == 0) {
                    LOG.warn("Keine Veranstaltung für DataSet '" + dataSet + "' gefunden");
                } else if (anzahlVeranstaltungen > 1) {
                    LOG.warn("Bitte in DEV-Mode nur eine Veranstaltung pro DataSet importieren");
                }

                Veranstaltung dataSetEvent = Veranstaltung.find("ORDER BY id DESC").firstResult();
                if (importierteVeranstaltungen.contains(dataSetEvent.getId())) {
                    LOG.error("Veranstaltung '" + dataSetEvent.getName() + "' wurde schon importiert.");
                    continue;
                }

                Long vid = dataSetEvent.getId();
                // 4. Slots - gilt für *ALLE* Veranstaltungen
                adminService.importSlotsFromCsv(basePath.resolve("slots.csv"), vid);

                // 5. Referenten
                referentService.importFromCsv(basePath.resolve("referenten.csv"), vid);

                // 6. Teilnehmer
                teilnehmerService.importFromCsv(basePath.resolve("teilnehmer.csv"), vid);

                // 7. Vorträge (Pflicht & Wahl)
                adminService.importVortraegeFromCsv(basePath.resolve("wahl_vortraege.csv"), vid);
                adminService.importVortraegeFromCsv(basePath.resolve("pflicht_vortraege.csv"), vid);
                adminService.importPrioritaetenFromCsv(basePath.resolve("tn_prios.csv"), vid);

                // 8. Verfügbarkeiten
                adminService.importRaumVerfuegbarkeitenFromCsv(basePath.resolve("raum_verfuegbarkeiten.csv"), vid);
                adminService.importNutzerVerfuegbarkeitenFromCsv(basePath.resolve("nutzer_verfuegbarkeiten.csv"), vid);

                importierteVeranstaltungen.add(vid);
            } catch (Exception e) {
                LOG.error("Fehler beim Laden von " + dataSet, e);
            }
        }

        LOG.info("###\n### Mailpit: http://localhost:9000/q/dev-ui/quarkus-mailpit/mailpit-ui");
    }
}
