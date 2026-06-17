package kreyj.konfplan.application.service;

import com.opencsv.bean.CsvToBean;
import com.opencsv.bean.CsvToBeanBuilder;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import kreyj.konfplan.presentation.dto.csv.GebaeudeRaeumeCsvDto;
import kreyj.konfplan.persistence.Gebaeude;
import kreyj.konfplan.persistence.Gebaeudetyp;
import kreyj.konfplan.persistence.ProtokollKategorie;
import kreyj.konfplan.persistence.Raum;
import org.jboss.logging.Logger;

import java.io.FileReader;
import java.nio.file.Path;
import java.util.List;

@ApplicationScoped
public class GebaeudeService {
    private static final Logger LOG = Logger.getLogger(GebaeudeService.class);

    private final ProtokollService protokollService;

    public GebaeudeService(ProtokollService protokollService) {
        this.protokollService = protokollService;
    }

    public List<Gebaeude> listAll() {
        return Gebaeude.listAll();
    }

    public Gebaeude findById(Long id) {
        return Gebaeude.findById(id);
    }

    @Transactional
    public Gebaeude save(Gebaeude g) {
        if (g.getId() == null) {
            g.persist();
            protokollService.log(ProtokollKategorie.GEBAEUDE, "Gebäude erstellt", "Gebäude '" + g.getName() + "' erstellt.", g.getId());
            return g;
        } else {
            Gebaeude entity = Gebaeude.findById(g.getId());
            if (entity == null) {
                return null;
            }
            entity.setName(g.getName());
            entity.setTyp(g.getTyp());
            entity.setStrasse(g.getStrasse());
            entity.setHausnummer(g.getHausnummer());
            entity.setPostleitzahl(g.getPostleitzahl());
            entity.setOrt(g.getOrt());
            protokollService.log(ProtokollKategorie.GEBAEUDE, "Gebäude aktualisiert", "Gebäude '" + entity.getName() + "' aktualisiert.", entity.getId());
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
                g.setName(gebaeudeName);
                try {
                    g.setTyp(Gebaeudetyp.valueOf(dto.typ.toUpperCase()));
                } catch (IllegalArgumentException e) {
                    LOG.warn("Gebäude '" + gebaeudeName + "' übersprungen: Ungültiger Gebäudetyp '" + dto.typ + "'.");
                    protokollService.log(ProtokollKategorie.GEBAEUDE, "Gebäude-Import übersprungen", "Gebäude '" + gebaeudeName + "': Ungültiger Gebäudetyp '" + dto.typ + "'.");
                    continue;
                }
                g.setStrasse(dto.strasse);
                g.setHausnummer(dto.hausnummer);
                g.setPostleitzahl(dto.plz);
                g.setOrt(dto.ort);
                g.persist();

                // Räume parsen und zuweisen
                if (dto.raeumeRaw != null && !dto.raeumeRaw.isBlank()) {
                    String[] raumStrings = dto.raeumeRaw.split("\\|");
                    for (String rs : raumStrings) {
                        String[] parts = rs.trim().split(":");
                        if (parts.length >= 2) {
                            try {
                                Raum r = new Raum();
                                r.setName(parts[0].trim());
                                r.setKapazitaet(Integer.parseInt(parts[1].trim()));
                                if (parts.length >= 3) {
                                    r.setEtage(parts[2].trim());
                                }
                                r.persist();
                                g.addRaum(r);
                                protokollService.log(ProtokollKategorie.RAUM, "Raum importiert (via Gebäude-Import)", "Raum '" + r.getName() + "' für Gebäude '" + g.getName() + "' importiert.", r.getId());
                            } catch (NumberFormatException e) {
                                LOG.warn("Gebäude '" + gebaeudeName + "': Raum '" + rs + "' übersprungen: Ungültige Kapazität. " + e.getMessage());
                                protokollService.log(ProtokollKategorie.RAUM, "Raum-Import übersprungen (via Gebäude-Import)", "Gebäude '" + gebaeudeName + "': Raum '" + rs + "' ungültige Kapazität.");
                            }
                        } else {
                            LOG.warn("Gebäude '" + gebaeudeName + "': Raum '" + rs + "' übersprungen: Ungültiges Format (erwartet 'Name:Kapazität[:Etage]').");
                            protokollService.log(ProtokollKategorie.RAUM, "Raum-Import übersprungen (via Gebäude-Import)", "Gebäude '" + gebaeudeName + "': Raum '" + rs + "' ungültiges Format.");
                        }
                    }
                }
                count++;
                protokollService.log(ProtokollKategorie.GEBAEUDE, "Gebäude importiert", "Gebäude '" + g.getName() + "' via CSV importiert.", g.getId());
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
            String name = gebaeude.getName();
            boolean deleted = Gebaeude.deleteById(id);
            if (deleted) {
                protokollService.log(ProtokollKategorie.GEBAEUDE, "Gebäude gelöscht", "Gebäude '" + name + "' gelöscht.", id);
            }
            return deleted;
        }
        return false;
    }
}
