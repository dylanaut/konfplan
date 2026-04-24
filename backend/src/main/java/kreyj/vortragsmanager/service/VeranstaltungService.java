package kreyj.vortragsmanager.service;

import com.opencsv.bean.CsvToBean;
import com.opencsv.bean.CsvToBeanBuilder;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import kreyj.vortragsmanager.dto.VeranstaltungDto;
import kreyj.vortragsmanager.dto.csv.VeranstaltungCsvDto;
import kreyj.vortragsmanager.entity.Admin;
import kreyj.vortragsmanager.entity.Gebaeude;
import kreyj.vortragsmanager.entity.Nutzer;
import kreyj.vortragsmanager.entity.Veranstaltung;
import kreyj.vortragsmanager.resource.VeranstaltungResource;
import kreyj.vortragsmanager.util.DateHelper;
import org.jboss.logging.Logger;

import java.io.FileReader;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static kreyj.vortragsmanager.util.DateHelper.DATE_FORMAT;

@ApplicationScoped
public class VeranstaltungService {
    private static final Logger LOG = Logger.getLogger(VeranstaltungService.class);

    public List<Veranstaltung> listAll() {
        return Veranstaltung.listAll();
    }

    public Veranstaltung findById(Long id) {
        return Veranstaltung.findById(id);
    }

    @Transactional
    public VeranstaltungDto save(VeranstaltungDto dto) {
        Veranstaltung entity;
        if (dto.id != null) {
            entity = Veranstaltung.findById(dto.id);
            if (entity == null) return null;
        } else {
            entity = new Veranstaltung();
        }

        entity.name = dto.name;
        entity.beginntAm = dto.beginntAm;
        entity.endetAm = dto.endetAm;
        entity.deadlineReferenten = dto.deadlineReferenten;
        entity.deadlineTeilnehmer = dto.deadlineTeilnehmer;
        entity.logo = dto.logo;
        entity.logo_link = dto.logo_link;

        // Gebäude zuweisen
        entity.gebaeude.clear();
        if (dto.gebaeude != null) {
            for (var gDto : dto.gebaeude) {
                Gebaeude g = Gebaeude.findById(gDto.id);
                if (g != null) entity.gebaeude.add(g);
            }
        }

        // Organisatoren zuweisen
        if (dto.organisatorIds != null) {
            entity.nutzer.removeIf(u -> u instanceof Admin);
            for (Long aid : dto.organisatorIds) {
                Admin a = Admin.findById(aid);
                if (a != null) entity.addNutzer(a);
            }
        }

        if (dto.id == null) entity.persist();
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

                Nutzer admin = Nutzer.findByEmail(dto.organisatorEmail);
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
                    admin.veranstaltungen.add(v);

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
