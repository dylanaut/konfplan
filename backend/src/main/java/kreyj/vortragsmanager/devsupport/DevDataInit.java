package kreyj.vortragsmanager.devsupport;

import io.agroal.api.AgroalDataSource;
import io.quarkus.agroal.DataSource;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import kreyj.vortragsmanager.entity.Veranstaltung;
import kreyj.vortragsmanager.service.*;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.util.List;

@ApplicationScoped
public class DevDataInit {
    private static final Logger LOG = Logger.getLogger(DevDataInit.class);

    @ConfigProperty(name = "vortragsmanager.dev-data.init", defaultValue = "false")
    boolean devInitEnabled;

    @ConfigProperty(name = "vortragsmanager.dev-data.csv-path", defaultValue = "src/test/resources/csv_import/bo_26_09")
    String csvBasePath;

    @Inject
    AgroalDataSource datasource;

    @Inject
    TeilnehmerService teilnehmerService;

    @Inject
    AdminService adminService;

    @Inject
    VeranstaltungService veranstaltungService;

    @Inject
    GebaeudeService gebaeudeService;

    @Inject
    ReferentService referentService;

    @Transactional
    void onStart(@Observes StartupEvent ev) throws SQLException {
        if (!devInitEnabled) {
            return;
        }

        LOG.info("Starte Dev-Daten-Initialisierung für " + datasource.getConnection().getMetaData().getURL() + " ...");

        try {
            Path basePath = Paths.get(csvBasePath);

            // 1. Gebäude & Räume
            gebaeudeService.importGebaeudeWithRaeumeFromCsv(basePath.resolve("gebaeude.csv"));

            // 2. Organisatoren (Admins)
            adminService.importAdminsFromCsv(basePath.resolve("organisatoren.csv"));

            // 3. Veranstaltungen
            veranstaltungService.importFromCsv(basePath.resolve("veranstaltungen.csv"));

            // Wir nehmen die erste Veranstaltung für die weiteren Importe
            List<Veranstaltung> veranstaltungen = Veranstaltung.listAll();
            if (veranstaltungen.isEmpty()) {
                LOG.error("Keine Veranstaltung importiert. Breche Dev-Daten-Initialisierung ab.");
                return;
            }
            Long vid = veranstaltungen.getFirst().id;

            // 4. Slots
            adminService.importSlotsFromCsv(basePath.resolve("slots.csv"), vid);

            // 5. Referenten
            referentService.importFromCsv(basePath.resolve("referenten.csv"), vid);

            // 6. Teilnehmer
            teilnehmerService.importFromCsv(basePath.resolve("teilnehmer_9.1.csv"), vid);

            // 7. Vorträge (Pflicht & Wahl)
            adminService.importVortraegeFromCsv(basePath.resolve("pflicht_vortraege.csv"), vid);
            adminService.importVortraegeFromCsv(basePath.resolve("wahl_vortraege.csv"), vid);

            LOG.info("Dev-Daten-Initialisierung erfolgreich abgeschlossen.");
        } catch (Exception e) {
            LOG.error("Fehler bei der Dev-Daten-Initialisierung", e);
        }
    }
}
