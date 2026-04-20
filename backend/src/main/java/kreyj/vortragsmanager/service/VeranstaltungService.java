package kreyj.vortragsmanager.service;

import com.opencsv.bean.CsvToBean;
import com.opencsv.bean.CsvToBeanBuilder;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import kreyj.vortragsmanager.dto.GebaeudeSimpleDto;
import kreyj.vortragsmanager.dto.VeranstaltungDto;
import kreyj.vortragsmanager.dto.csv.VeranstaltungCsvDto;
import kreyj.vortragsmanager.entity.Admin;
import kreyj.vortragsmanager.entity.Gebaeude;
import kreyj.vortragsmanager.entity.User;
import kreyj.vortragsmanager.entity.Veranstaltung;
import kreyj.vortragsmanager.resource.VeranstaltungResource;
import kreyj.vortragsmanager.util.SQLiteBackup;
import org.jboss.logging.Logger;

import java.io.FileReader;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;

@ApplicationScoped
public class VeranstaltungService {
    private static final Logger LOG = Logger.getLogger(VeranstaltungService.class);
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    @Inject
    private SQLiteBackup backupService;

    public List<Veranstaltung> listAll() {
        return Veranstaltung.listAll();
    }

    public Veranstaltung findById(Long id) {
        return Veranstaltung.findById(id);
    }

    @Transactional
    public VeranstaltungDto save(VeranstaltungDto dto) {
        Veranstaltung entity;
        if (dto.id == null) {
            entity = new Veranstaltung();
        } else {
            entity = Veranstaltung.findById(dto.id);
            if (entity == null) {
                return null;
            }
        }

        entity.name = dto.name;
        entity.beginntAm = dto.beginntAm;
        entity.endetAm = dto.endetAm;
        entity.logo = dto.logo;
        entity.logo_link = dto.logo_link;

        if (entity.id == null) {
            entity.persist();
        }

        // Organisatoren (Admins) verknüpfen
        if (dto.organisatorIds != null && !dto.organisatorIds.isEmpty()) {
            for (Long adminId : dto.organisatorIds) {
                User admin = User.findById(adminId);
                if (admin != null && "ADMIN".equals(admin.role)) {
                    if (!admin.veranstaltungen.contains(entity)) {
                        admin.veranstaltungen.add(entity);
                    }
                }
            }
        }

        // Gebaeude-Relation (ManyToMany) aktualisieren
        entity.gebaeude.clear();
        if (dto.gebaeude != null) {
            for (GebaeudeSimpleDto gebaeudeId : dto.gebaeude) {
                Gebaeude attached = Gebaeude.findById(gebaeudeId);
                if (attached != null) {
                    entity.gebaeude.add(attached);
                }
            }
        }

        return VeranstaltungResource.mapToDto(entity);
    }

    @Transactional
    public int importFromCsv(Path csvFilePath) throws Exception {
        int count = 0;
        try (FileReader reader = new FileReader(csvFilePath.toFile())) {
            CsvToBean<VeranstaltungCsvDto> csvToBean = new CsvToBeanBuilder<VeranstaltungCsvDto>(reader)
                    .withType(VeranstaltungCsvDto.class)
                    .withSeparator(';')
                    .withIgnoreLeadingWhiteSpace(true)
                    .withThrowExceptions(false)
                    .build();

            List<VeranstaltungCsvDto> beans = csvToBean.parse();

            csvToBean.getCapturedExceptions().forEach(e ->
                    LOG.error("CSV-Parsing-Fehler in " + csvFilePath.getFileName() + " (Zeile " + e.getLineNumber() + "): " + e.getMessage())
            );

            for (VeranstaltungCsvDto dto : beans) {
                if (dto.name == null || dto.name.isBlank()) {
                    LOG.warn("Veranstaltung übersprungen: Name fehlt.");
                    continue;
                }

                User admin = User.findByEmail(dto.organisatorEmail);
                if (admin instanceof Admin) {
                    Veranstaltung v = new Veranstaltung();
                    v.name = dto.name;
                    try {
                        v.beginntAm = LocalDateTime.parse(dto.beginntAm, DATE_FORMAT);
                        if (dto.endetAm != null && !dto.endetAm.isEmpty()) {
                            v.endetAm = LocalDateTime.parse(dto.endetAm, DATE_FORMAT);
                        }
                    } catch (Exception e) {
                        LOG.error("Fehler beim Parsen des Datums für Veranstaltung '" + dto.name + "': " + e.getMessage());
                        continue;
                    }
                    v.logo = dto.logo;
                    v.logo_link = dto.logo_link;
                    v.persist();
                    
                    // Admin verknüpfen
                    admin.addVeranstaltung(v);
                    admin.persist();

                    // Gebäude verknüpfen

                    if (dto.gebaeudeNamen != null && !dto.gebaeudeNamen.isEmpty()) {
                        Arrays.stream(dto.gebaeudeNamen.split("\\|")).map(String::trim).forEach(name -> {
                            Gebaeude g = Gebaeude.find("name", name).firstResult();
                            if (g != null) {
                                v.gebaeude.add(g);
                            } else {
                                LOG.warn("Veranstaltung '" + dto.name + "': Gebäude nicht gefunden: '" + name + "'");
                            }
                        });
                    }
                    count++;
                } else {
                    LOG.warn("Veranstaltung '" + dto.name + "' übersprungen: Organisator (Admin) mit Email " + dto.organisatorEmail + " nicht gefunden.");
                }
            }
        } catch (Exception e) {
            LOG.error("Kritischer Fehler beim Importieren der Veranstaltungen aus CSV: " + csvFilePath, e);
            throw e;
        }
        LOG.info("Veranstaltungs-Import abgeschlossen: " + count + " Veranstaltungen importiert.");
        return count;
    }

    @Transactional
    public boolean delete(Long id) {
        return Veranstaltung.deleteById(id);
    }
}
