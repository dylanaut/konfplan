package kreyj.vortragsmanager.service;

import com.opencsv.bean.CsvToBeanBuilder;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import kreyj.vortragsmanager.dto.VeranstaltungCsvDto;
import kreyj.vortragsmanager.entity.Admin;
import kreyj.vortragsmanager.entity.Gebaeude;
import kreyj.vortragsmanager.entity.User;
import kreyj.vortragsmanager.entity.Veranstaltung;

import java.io.FileReader;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@ApplicationScoped
public class VeranstaltungService {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    public List<Veranstaltung> listAll() {
        return Veranstaltung.listAll();
    }

    public Veranstaltung findById(Long id) {
        return Veranstaltung.findById(id);
    }

    @Transactional
    public Veranstaltung save(Veranstaltung v) {
        if (v.organisator == null || !"ADMIN".equals(v.organisator.role)) {
            throw new IllegalArgumentException("Der Organisator muss ein Benutzer mit der Rolle ADMIN sein.");
        }
        if (v.id == null) {
            v.persist();
            return v;
        } else {
            Veranstaltung entity = Veranstaltung.findById(v.id);
            if (entity == null) return null;
            entity.name = v.name;
            entity.beginntAm = v.beginntAm;
            entity.endetAm = v.endetAm;
            // 'ort' wurde entfernt
            entity.logo = v.logo;
            entity.logo_link = v.logo_link;
            entity.organisator = v.organisator;
            
            // Gebaeude aktualisieren
            entity.gebaeude.clear();
            if (v.gebaeude != null) {
                v.gebaeude.forEach(g -> {
                    Gebaeude attachedGebaeude = Gebaeude.findById(g.id);
                    if (attachedGebaeude != null) {
                        entity.gebaeude.add(attachedGebaeude);
                    }
                });
            }
            return entity;
        }
    }

    @Transactional
    public int importFromCsv(Path csvFilePath) throws Exception {
        int count = 0;
        try (FileReader reader = new FileReader(csvFilePath.toFile())) {
            List<VeranstaltungCsvDto> beans = new CsvToBeanBuilder<VeranstaltungCsvDto>(reader)
                    .withType(VeranstaltungCsvDto.class)
                    .withSeparator(';')
                    .withIgnoreLeadingWhiteSpace(true)
                    .build()
                    .parse();

            for (VeranstaltungCsvDto dto : beans) {
                User admin = User.findByEmail(dto.organisatorEmail);
                if (admin instanceof Admin) {
                    Veranstaltung v = new Veranstaltung();
                    v.name = dto.name;
                    v.beginntAm = LocalDateTime.parse(dto.beginntAm, DATE_FORMAT);
                    if (dto.endetAm != null && !dto.endetAm.isEmpty()) {
                        v.endetAm = LocalDateTime.parse(dto.endetAm, DATE_FORMAT);
                    }
                    // 'ort' wurde entfernt
                    v.organisator = admin;

                    // Gebaeude aus Namen zuweisen
                    if (dto.gebaeudeNamen != null && !dto.gebaeudeNamen.isEmpty()) {
                        Arrays.stream(dto.gebaeudeNamen.split("\\|"))
                              .map(String::trim)
                              .forEach(gebaeudeName -> {
                                  Gebaeude g = Gebaeude.find("name", gebaeudeName).firstResult();
                                  if (g != null) {
                                      v.gebaeude.add(g);
                                  }
                              });
                    }
                    v.persist();
                    count++;
                }
            }
        }
        return count;
    }

    @Transactional
    public boolean delete(Long id) {
        return Veranstaltung.deleteById(id);
    }
}
