package kreyj.konfplan.service;

import com.opencsv.bean.CsvToBean;
import com.opencsv.bean.CsvToBeanBuilder;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import kreyj.konfplan.dto.csv.GebaeudeRaeumeCsvDto;
import kreyj.konfplan.persistence.Gebaeude;
import kreyj.konfplan.persistence.Raum;
import kreyj.konfplan.persistence.ProtokollKategorie;
import org.jboss.logging.Logger;
import jakarta.inject.Inject;

import java.io.FileReader;
import java.nio.file.Path;
import java.util.List;

@ApplicationScoped
public class GebaeudeService {
    private static final Logger LOG = Logger.getLogger(GebaeudeService.class);

    @Inject
    ProtokollService protokollService;

    public List<Gebaeude> listAll() {
        return Gebaeude.listAll();
    }

    public Gebaeude findById(Long id) {
        return Gebaeude.findById(id);
    }

    @Transactional
    public Gebaeude save(Gebaeude g) {
        if (g.id == null) {
            g.persist();
            protokollService.log(ProtokollKategorie.GEBAEUDE, "Gebäude erstellt", "Gebäude '" + g.name + "' erstellt.", g.id);
            return g;
        } else {
            Gebaeude entity = Gebaeude.findById(g.id);
            if (entity == null) return null;
            entity.name = g.name;
            entity.typ = g.typ;
            entity.strasse = g.strasse;
            entity.hausnummer = g.hausnummer;
            entity.postleitzahl = g.postleitzahl;
            entity.ort = g.ort;
            protokollService.log(ProtokollKategorie.GEBAEUDE, "Gebäude aktualisiert", "Gebäude '" + entity.name + "' aktualisiert.", entity.id);
            return entity;
        }
    }

    @Transactional
    public int importGebaeudeWithRaeumeFromCsv(Path csvFilePath) throws Exception {
        int count = 0;
        try (FileReader reader = new FileReader(csvFilePath.toFile())) {
            CsvToBean<GebaeudeRaeumeCsvDto> csvToBean = new CsvToBeanBuilder<GebaeudeRaeumeCsvDto>(reader)
                    .withType(GebaeudeRaeumeCsvDto.class)
                    .withSeparator(';')
                    .withIgnoreLeadingWhiteSpace(true)
                    .withThrowExceptions(false)
                    .build();

            List<GebaeudeRaeumeCsvDto> beans = csvToBean.parse();

            csvToBean.getCapturedExceptions().forEach(e -> {
                LOG.error("CSV-Parsing-Fehler in " + csvFilePath.getFileName() + " (Zeile " + e.getLineNumber() + "): " + e.getMessage());
                protokollService.log(ProtokollKategorie.SYSTEM, "CSV-Parsing-Fehler", "Gebäude-Import: " + e.getMessage() + " in Zeile " + e.getLineNumber());
            });

            for (GebaeudeRaeumeCsvDto dto : beans) {
                if (dto.name == null || dto.name.isBlank()) {
                    LOG.warn("Gebäude-Zeile übersprungen: Name fehlt.");
                    protokollService.log(ProtokollKategorie.GEBAEUDE, "Gebäude-Import übersprungen", "Gebäudename fehlte in CSV-Zeile.");
                    continue;
                }

                String gebaeudeName = dto.name;

                if (Gebaeude.find("name = ?1", gebaeudeName).count() > 0) {
                    LOG.warn("Gebäude '" + gebaeudeName + "' übersprungen: Existiert bereits.");
                    protokollService.log(ProtokollKategorie.GEBAEUDE, "Gebäude-Import übersprungen", "Gebäude '" + gebaeudeName + "' existiert bereits.");
                    continue;
                }

                Gebaeude g = new Gebaeude();
                g.name = gebaeudeName;
                try {
                    g.typ = Gebaeude.Gebaeudetyp.valueOf(dto.typ.toUpperCase());
                } catch (IllegalArgumentException e) {
                    LOG.warn("Gebäude '" + gebaeudeName + "' übersprungen: Ungültiger Gebäudetyp '" + dto.typ + "'.");
                    protokollService.log(ProtokollKategorie.GEBAEUDE, "Gebäude-Import übersprungen", "Gebäude '" + gebaeudeName + "': Ungültiger Gebäudetyp '" + dto.typ + "'.");
                    continue;
                }
                g.strasse = dto.strasse;
                g.hausnummer = dto.hausnummer;
                g.postleitzahl = dto.plz;
                g.ort = dto.ort;
                g.persist();

                // Räume parsen und zuweisen
                if (dto.raeumeRaw != null && !dto.raeumeRaw.isBlank()) {
                    String[] raumStrings = dto.raeumeRaw.split("\\|");
                    for (String rs : raumStrings) {
                        String[] parts = rs.trim().split(":");
                        if (parts.length >= 2) {
                            try {
                                Raum r = new Raum();
                                r.name = parts[0].trim();
                                r.kapazitaet = Integer.parseInt(parts[1].trim());
                                if (parts.length >= 3) {
                                    r.etage = parts[2].trim();
                                }
                                r.gebaeude = g;
                                r.persist();
                                g.raeume.add(r);
                                protokollService.log(ProtokollKategorie.RAUM, "Raum importiert (via Gebäude-Import)", "Raum '" + r.name + "' für Gebäude '" + g.name + "' importiert.", r.id);
                            } catch (NumberFormatException e) {
                                LOG.warn("Gebäude '" + gebaeudeName + "': Raum '" + rs + "' übersprungen: Ungültige Kapazität. " + e.getMessage());
                                protokollService.log(ProtokollKategorie.RAUM, "Raum-Import übersprungen (via Gebäude-Import)", "Gebäude '" + gebaeudeName + "': Raum '" + rs + "' ungültige Kapazität.");
                            }
                        } else {
                            LOG.warn("Gebäude '" + gebaeudeName + "': Raum '" + rs + "' übersprungen: Ungültiges Format (erwartet 'Name:Kapazität[:Etage]').");
                            protokollService.log(ProtokollKategorie.RAUM, "Raum-Import übersprungen (via Gebäude-Import)", "Gebäude '" + gebaeudeName + "': Raum '" + rs + "' ungültiges Format.");
                        }
                    }
                    g.persist();
                }
                count++;
                protokollService.log(ProtokollKategorie.GEBAEUDE, "Gebäude importiert", "Gebäude '" + g.name + "' via CSV importiert.", g.id);
            }
        } catch (Exception e) {
            LOG.error("Kritischer Fehler beim Importieren der Gebäude und Räume aus CSV: " + csvFilePath, e);
            protokollService.log(ProtokollKategorie.SYSTEM, "Kritischer Fehler beim Gebäude-Import", e.getMessage());
            throw e;
        }
        LOG.info("Gebäude-Import abgeschlossen: " + count + " Gebäude erfolgreich importiert.");
        protokollService.log(ProtokollKategorie.GEBAEUDE, "Gebäude-Import abgeschlossen", count + " Gebäude importiert.");
        return count;
    }

    @Transactional
    public boolean delete(Long id) {
        Gebaeude gebaeude = Gebaeude.findById(id);
        if (gebaeude != null) {
            String name = gebaeude.name;
            boolean deleted = Gebaeude.deleteById(id);
            if (deleted) {
                protokollService.log(ProtokollKategorie.GEBAEUDE, "Gebäude gelöscht", "Gebäude '" + name + "' gelöscht.", id);
            }
            return deleted;
        }
        return false;
    }
}
