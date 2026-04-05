package kreyj.vortragsmanager.service;

import com.opencsv.bean.CsvToBeanBuilder;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import kreyj.vortragsmanager.dto.RaumCsvDto;
import kreyj.vortragsmanager.entity.Gebaeude;
import kreyj.vortragsmanager.entity.Raum;
import kreyj.vortragsmanager.entity.EventSlot;

import java.io.FileReader;
import java.nio.file.Path;
import java.util.List;

@ApplicationScoped
public class RaumService {

    public List<Raum> listAll() {
        return Raum.listAll();
    }

    public List<Raum> listByGebaeude(Long gebaeudeId) {
        return Raum.list("gebaeude.id", gebaeudeId);
    }

    public Raum findById(Long id) {
        return Raum.findById(id);
    }

    @Transactional
    public Raum save(Raum r, Long gebaeudeId) {
        Gebaeude gebaeude = Gebaeude.findById(gebaeudeId);
        if (gebaeude == null) {
            throw new IllegalArgumentException("Gebäude mit ID " + gebaeudeId + " nicht gefunden.");
        }

        if (r.id == null) {
            r.persist();
            gebaeude.raeume.add(r);
            gebaeude.persist();
            return r;
        } else {
            Raum raum = Raum.findById(r.id);
            if (raum == null) return null;
            
            raum.name = r.name;
            raum.kapazitaet = r.kapazitaet;
            raum.etage = r.etage;

            raum.persist();

            return raum;
        }
    }

    @Transactional
    public int importFromCsv(Path csvFilePath, Long gebaeudeId) throws Exception {
        int count = 0;
        Gebaeude gebaeude = Gebaeude.findById(gebaeudeId);
        if (gebaeude == null) {
            throw new IllegalArgumentException("Gebäude mit ID " + gebaeudeId + " nicht gefunden.");
        }

        try (FileReader reader = new FileReader(csvFilePath.toFile())) {
            List<RaumCsvDto> beans = new CsvToBeanBuilder<RaumCsvDto>(reader)
                    .withType(RaumCsvDto.class)
                    .withSeparator(';')
                    .withIgnoreLeadingWhiteSpace(true)
                    .build()
                    .parse();

            for (RaumCsvDto dto : beans) {
                Raum r = new Raum();
                r.name = dto.name;
                r.kapazitaet = dto.kapazitaet;
                r.etage = dto.etage;
                r.persist();

                gebaeude.raeume.add(r);

                count++;
            }

            gebaeude.persist();
        }
        return count;
    }

    @Transactional
    public boolean delete(Long id) {
        return Raum.deleteById(id);
    }
}
