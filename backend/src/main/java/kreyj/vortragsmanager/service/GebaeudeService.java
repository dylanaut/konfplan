package kreyj.vortragsmanager.service;

import com.opencsv.bean.CsvToBean;
import com.opencsv.bean.CsvToBeanBuilder;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import kreyj.vortragsmanager.dto.csv.GebaeudeRaeumeCsvDto;
import kreyj.vortragsmanager.entity.Gebaeude;
import kreyj.vortragsmanager.entity.Raum;
import org.jboss.logging.Logger;

import java.io.FileReader;
import java.nio.file.Path;
import java.util.List;

@ApplicationScoped
public class GebaeudeService {
    private static final Logger LOG = Logger.getLogger(GebaeudeService.class);
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

            csvToBean.getCapturedExceptions().forEach(e ->
                LOG.error("CSV-Parsing-Fehler in " + csvFilePath.getFileName() + " (Zeile " + e.getLineNumber() + "): " + e.getMessage())
            );

            for (GebaeudeRaeumeCsvDto dto : beans) {
                if (dto.name == null || dto.name.isBlank()) {
                    LOG.warn("Gebäude-Zeile übersprungen: Name fehlt.");
                    continue;
                }

                String gebaeudeName = dto.name;

                if (Gebaeude.find("name = ?1", gebaeudeName).count() > 0) {
                    LOG.warn("Gebäude '" + gebaeudeName + "' übersprungen: Existiert bereits.");
                    continue;
                }

                Gebaeude g = new Gebaeude();
                g.name = gebaeudeName;
                try {
                    g.typ = Gebaeude.Gebaeudetyp.valueOf(dto.typ.toUpperCase());
                } catch (IllegalArgumentException e) {
                    LOG.warn("Gebäude '" + gebaeudeName + "' übersprungen: Ungültiger Gebäudetyp '" + dto.typ + "'.");
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
                            } catch (NumberFormatException e) {
                                LOG.warn("Gebäude '" + gebaeudeName + "': Raum '" + rs + "' übersprungen: Ungültige Kapazität. " + e.getMessage());
                            }
                        } else {
                            LOG.warn("Gebäude '" + gebaeudeName + "': Raum '" + rs + "' übersprungen: Ungültiges Format (erwartet 'Name:Kapazität[:Etage]').");
                        }
                    }
                    g.persist();
                }
                count++;
            }
        } catch (Exception e) {
            LOG.error("Kritischer Fehler beim Importieren der Gebäude und Räume aus CSV: " + csvFilePath, e);
            throw e;
        }
        LOG.info("Gebäude-Import abgeschlossen: " + count + " Gebäude erfolgreich importiert.");
        return count;
    }

    @Transactional
    public boolean delete(Long id) {
        return Gebaeude.deleteById(id);
    }
}
