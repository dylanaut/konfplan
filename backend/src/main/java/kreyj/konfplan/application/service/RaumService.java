package kreyj.konfplan.application.service;

import com.opencsv.bean.CsvToBean;
import com.opencsv.bean.CsvToBeanBuilder;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import kreyj.konfplan.presentation.dto.csv.RaumCsvDto;
import kreyj.konfplan.persistence.Gebaeude;
import kreyj.konfplan.persistence.ProtokollKategorie;
import kreyj.konfplan.persistence.Raum;
import org.jboss.logging.Logger;

import java.io.FileReader;
import java.nio.file.Path;
import java.util.List;

@ApplicationScoped
public class RaumService {

    private static final Logger LOG = Logger.getLogger(RaumService.class);

    private final ProtokollService protokollService;

    public RaumService(ProtokollService protokollService) {
        this.protokollService = protokollService;
    }

    public List<Raum> listAll() {
        return Raum.listAll();
    }

    public List<Raum> listByGebaeude(Long gebaeudeId) {
        return Raum.list("gebaeude.id = ?1", gebaeudeId);
    }

    public Raum findById(Long id) {
        return Raum.findById(id);
    }

    @Transactional
    public Raum save(Raum r, Long gebaeudeId) {
        Gebaeude gebaeude = Gebaeude.findById(gebaeudeId);
        if (gebaeude == null) {
            protokollService.log(ProtokollKategorie.RAUM, "Raum-Speicherung fehlgeschlagen", "Gebäude mit ID " + gebaeudeId + " nicht gefunden.");
            throw new IllegalArgumentException("Gebäude mit ID " + gebaeudeId + " nicht gefunden.");
        }

        if (r.getId() == null) {
            r.persist();
            gebaeude.addRaum(r);
            gebaeude.persist();
            protokollService.log(ProtokollKategorie.RAUM, "Raum erstellt", "Raum '" + r.getName() + "' im Gebäude '" + gebaeude.getName() + "' erstellt.", r.getId());
            return r;
        } else {
            Raum raum = Raum.findById(r.getId());
            if (raum == null) {
                return null;
            }

            raum.setName(r.getName());
            raum.setKapazitaet(r.getKapazitaet());
            raum.setEtage(r.getEtage());

            raum.persist();
            protokollService.log(ProtokollKategorie.RAUM, "Raum aktualisiert", "Raum '" + raum.getName() + "' im Gebäude '" + gebaeude.getName() + "' aktualisiert.", raum.getId());
            return raum;
        }
    }

    @Transactional
    public int importFromCsv(Path csvFilePath, Long gebaeudeId) throws Exception {
        int count = 0;
        Gebaeude gebaeude = Gebaeude.findById(gebaeudeId);
        if (gebaeude == null) {
            LOG.error("CSV-Import abgebrochen: Gebäude mit ID " + gebaeudeId + " nicht gefunden.");
            protokollService.log(ProtokollKategorie.RAUM, "Raum-Import fehlgeschlagen", "Gebäude mit ID " + gebaeudeId + " nicht gefunden.");
            throw new IllegalArgumentException("Gebäude mit ID " + gebaeudeId + " nicht gefunden.");
        }

        try (FileReader reader = new FileReader(csvFilePath.toFile())) {
            CsvToBean<RaumCsvDto> csvToBean = new CsvToBeanBuilder<RaumCsvDto>(reader)
                    .withType(RaumCsvDto.class)
                    .withSeparator(';')
                    .withIgnoreLeadingWhiteSpace(true)
                    .withThrowExceptions(false)
                    .build();

            List<RaumCsvDto> beans = csvToBean.parse();

            csvToBean.getCapturedExceptions().forEach(e -> {
                LOG.error("CSV-Parsing-Fehler in " + csvFilePath.getFileName() + " (Zeile " + e.getLineNumber() + "): " + e.getMessage());
                protokollService.log(ProtokollKategorie.SYSTEM, "CSV-Parsing-Fehler", "Raum-Import: " + e.getMessage() + " in Zeile " + e.getLineNumber());
            });

            for (RaumCsvDto dto : beans) {
                if (dto.name == null || dto.name.isBlank()) {
                    LOG.warn("Raum-Zeile übersprungen: Name fehlt.");
                    protokollService.log(ProtokollKategorie.RAUM, "Raum-Import übersprungen", "Raumname fehlte in CSV-Zeile.");
                    continue;
                }

                Raum r = new Raum();
                r.setName(dto.name);
                r.setKapazitaet(dto.kapazitaet);
                r.setEtage(dto.etage);
                r.persist();

                gebaeude.addRaum(r);
                count++;
                protokollService.log(ProtokollKategorie.RAUM, "Raum importiert", "Raum '" + r.getName() + "' für Gebäude '" + gebaeude.getName() + "' importiert.", r.getId());
            }
        } catch (Exception e) {
            LOG.error("Kritischer Fehler beim Importieren der Räume aus CSV: " + csvFilePath, e);
            protokollService.log(ProtokollKategorie.SYSTEM, "Kritischer Fehler beim Raum-Import", e.getMessage());
            throw e;
        }
        LOG.info("Raum-Import abgeschlossen: " + count + " Räume für Gebäude '" + gebaeude.getName() + "' importiert.");
        protokollService.log(ProtokollKategorie.RAUM, "Raum-Import abgeschlossen", count + " Räume für Gebäude '" + gebaeude.getName() + "' importiert.");
        return count;
    }

    @Transactional
    public boolean delete(Long id) {
        Raum raum = Raum.findById(id);
        if (raum != null) {
            String name = raum.getName();
            String gName = raum.getGebaeude() != null ? raum.getGebaeude().getName() : "unbekannt";
            boolean deleted = Raum.deleteById(id);
            if (deleted) {
                protokollService.log(ProtokollKategorie.RAUM, "Raum gelöscht", "Raum '" + name + "' aus Gebäude '" + gName + "' gelöscht.", id);
            }
            return deleted;
        }
        return false;
    }
}
