package kreyj.vortragsmanager.service;

import com.opencsv.bean.CsvToBeanBuilder;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import kreyj.vortragsmanager.dto.GebaeudeRaeumeCsvDto; // Neues DTO
import kreyj.vortragsmanager.entity.Gebaeude;
import kreyj.vortragsmanager.entity.Raum;
import java.io.FileReader;
import java.nio.file.Path;
import java.util.List;

@ApplicationScoped
public class GebaeudeService {

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
            List<GebaeudeRaeumeCsvDto> beans = new CsvToBeanBuilder<GebaeudeRaeumeCsvDto>(reader)
                    .withType(GebaeudeRaeumeCsvDto.class)
                    .withSeparator(';')
                    .withIgnoreLeadingWhiteSpace(true)
                    .build()
                    .parse();

            for (GebaeudeRaeumeCsvDto dto : beans) {
                Gebaeude g = new Gebaeude();
                g.name = dto.name;
                g.typ = Gebaeude.Gebaeudetyp.valueOf(dto.typ.toUpperCase());
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
                            Raum r = new Raum();
                            r.name = parts[0].trim();
                            r.kapazitaet = Integer.parseInt(parts[1].trim());
                            if (parts.length >= 3) {
                                r.etage = parts[2].trim();
                            }
                            r.persist();
                            g.raeume.add(r);
                        }
                    }
                    g.persist();
                }
                count++;
            }
        }
        return count;
    }

    @Transactional
    public boolean delete(Long id) {
        return Gebaeude.deleteById(id);
    }
}
