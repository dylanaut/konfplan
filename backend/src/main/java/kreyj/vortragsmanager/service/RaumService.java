package kreyj.vortragsmanager.service;

import com.opencsv.bean.CsvToBeanBuilder;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import kreyj.vortragsmanager.dto.RaumCsvDto;
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

    public Raum findById(Long id) {
        return Raum.findById(id);
    }

    @Transactional
    public Raum save(Raum r) {
        if (r.id == null) {
            r.persist();
            return r;
        } else {
            Raum entity = Raum.findById(r.id);
            if (entity == null) return null;
            entity.name = r.name;
            entity.kapazitaet = r.kapazitaet;
            entity.etage = r.etage;
            entity.verfuegbareSlots = r.verfuegbareSlots;
            return entity;
        }
    }

    @Transactional
    public int importFromCsv(Path csvFilePath) throws Exception {
        int count = 0;
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
                count++;
            }
        }
        return count;
    }

    @Transactional
    public boolean delete(Long id) {
        return Raum.deleteById(id);
    }
}
