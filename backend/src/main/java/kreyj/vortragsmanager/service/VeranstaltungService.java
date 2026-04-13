package kreyj.vortragsmanager.service;

import com.opencsv.bean.CsvToBeanBuilder;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import kreyj.vortragsmanager.dto.VeranstaltungCsvDto;
import kreyj.vortragsmanager.dto.VeranstaltungDto;
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

    public List<VeranstaltungDto> listAll() {
        return Veranstaltung.<Veranstaltung>listAll().stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    public VeranstaltungDto findById(Long id) {
        Veranstaltung v = Veranstaltung.findById(id);
        return v != null ? mapToDto(v) : null;
    }

    @Transactional
    public VeranstaltungDto save(VeranstaltungDto dto) {
        Veranstaltung entity;
        if (dto.id == null) {
            entity = new Veranstaltung();
        } else {
            entity = Veranstaltung.findById(dto.id);
            if (entity == null) return null;
        }

        entity.name = dto.name;
        entity.beginntAm = dto.beginntAm;
        entity.endetAm = dto.endetAm;
        entity.logo = dto.logo;
        entity.logo_link = dto.logo_link;

        if (dto.organisatorId != null) {
            User admin = User.findById(dto.organisatorId);
            if (admin != null && "ADMIN".equals(admin.role)) {
                entity.organisator = admin;
            } else {
                throw new IllegalArgumentException("Der Organisator muss ein Benutzer mit der Rolle ADMIN sein.");
            }
        } else if (entity.id == null) {
            throw new IllegalArgumentException("Ein Organisator ist für eine neue Veranstaltung zwingend erforderlich.");
        }

        // Gebaeude-Relation (ManyToMany) aktualisieren
        entity.gebaeude.clear();
        if (dto.gebaeude != null) {
            for (Gebaeude gDto : dto.gebaeude) {
                Gebaeude attached = Gebaeude.findById(gDto.id);
                if (attached != null) {
                    entity.gebaeude.add(attached);
                }
            }
        }

        if (entity.id == null) {
            entity.persist();
        }
        
        return mapToDto(entity);
    }

    @Transactional
    public int importFromCsv(Path csvFilePath) throws Exception {
        int count = 0;
        try (FileReader reader = new FileReader(csvFilePath.toFile())) {
            List<VeranstaltungCsvDto> beans = new CsvToBeanBuilder<VeranstaltungCsvDto>(reader)
                    .withType(VeranstaltungCsvDto.class).withSeparator(';').withIgnoreLeadingWhiteSpace(true).build().parse();

            for (VeranstaltungCsvDto dto : beans) {
                User admin = User.findByEmail(dto.organisatorEmail);
                if (admin instanceof Admin) {
                    Veranstaltung v = new Veranstaltung();
                    v.name = dto.name;
                    v.beginntAm = LocalDateTime.parse(dto.beginntAm, DATE_FORMAT);
                    if (dto.endetAm != null && !dto.endetAm.isEmpty()) {
                        v.endetAm = LocalDateTime.parse(dto.endetAm, DATE_FORMAT);
                    }
                    v.organisator = admin;
                    if (dto.gebaeudeNamen != null && !dto.gebaeudeNamen.isEmpty()) {
                        Arrays.stream(dto.gebaeudeNamen.split("\\|")).map(String::trim).forEach(name -> {
                            Gebaeude g = Gebaeude.find("name", name).firstResult();
                            if (g != null) v.gebaeude.add(g);
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

    private VeranstaltungDto mapToDto(Veranstaltung v) {
        VeranstaltungDto dto = new VeranstaltungDto();
        dto.id = v.id;
        dto.name = v.name;
        dto.beginntAm = v.beginntAm;
        dto.endetAm = v.endetAm;
        dto.logo = v.logo;
        dto.logo_link = v.logo_link;
        dto.organisatorId = v.organisator != null ? v.organisator.id : null;
        dto.organisatorName = v.organisator != null ? v.organisator.lastName : "";
        dto.gebaeude = v.gebaeude;
        dto.version = v.version;
        return dto;
    }
}
