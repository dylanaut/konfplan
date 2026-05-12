package kreyj.konfplan.service;

import com.opencsv.bean.CsvToBean;
import com.opencsv.bean.CsvToBeanBuilder;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.OptimisticLockException;
import jakarta.transaction.Transactional;
import kreyj.konfplan.dto.VeranstaltungDto;
import kreyj.konfplan.dto.csv.VeranstaltungCsvDto;
import kreyj.konfplan.persistence.*;
import kreyj.konfplan.resource.VeranstaltungResource;
import org.apache.commons.lang3.StringUtils;
import org.jboss.logging.Logger;

import java.io.FileReader;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static kreyj.konfplan.util.DateHelper.DATE_FORMAT;

@ApplicationScoped
public class VeranstaltungService {
    private static final Logger LOG = Logger.getLogger(VeranstaltungService.class);

    @Inject
    ProtokollService protokollService; // Inject ProtokollService

    public List<Veranstaltung> listAll() {
        return Veranstaltung.listAll();
    }

    public Veranstaltung findById(Long id) {
        return Veranstaltung.findById(id);
    }

    @Transactional
    public VeranstaltungDto save(VeranstaltungDto dto) {
        Veranstaltung entity;
        String aktion;

        if (dto.id != null) {
            entity = Veranstaltung.findById(dto.id);
            if (entity == null) {
                return null;
            }
            
            // Optimistic Locking Prüfung
            if (dto.version != null && !entity.version.equals(dto.version)) {
                throw new OptimisticLockException("Die Veranstaltung wurde in der Zwischenzeit von einem anderen Benutzer geändert.");
            }

            aktion = "aktualisiert";
        } else {
            entity = new Veranstaltung();
            aktion = "erstellt";
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
                if (g != null) {
                    entity.gebaeude.add(g);
                }
            }
        }

        // Organisatoren zuweisen
        if (dto.organisatorIds != null) {
            entity.nutzer.removeIf(u -> u instanceof Admin);
            for (Long aid : dto.organisatorIds) {
                Admin a = Admin.findById(aid);
                if (a != null) {
                    entity.addNutzer(a);
                }
            }
        }

        if (dto.id == null) {
            entity.persist();
            protokollService.log(ProtokollKategorie.VERANSTALTUNG, "Veranstaltung erstellt", "Neue Veranstaltung '" + entity.name + "' erstellt.", entity.id);
        } else {
            // ZWINGEND ERFORDERLICH FÜR OPTIMISTIC LOCKING RESPONSE:
            // Hibernate zwingen, das Update jetzt durchzuführen, damit persistence.version hochgezählt wird.
            entity.flush();
            protokollService.log(ProtokollKategorie.VERANSTALTUNG, "Veranstaltung aktualisiert", "Veranstaltung '" + entity.name + "' aktualisiert.", entity.id);
        }
        return VeranstaltungResource.mapVeranstaltungToDto(entity);
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

                String[] organisatorenEmails = StringUtils.split(dto.organisatorenEmails, ",");
                for (String organisatorenEmail : organisatorenEmails) {
                    Nutzer admin = Nutzer.findByEmail(organisatorenEmail.trim());

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
                        protokollService.log(ProtokollKategorie.VERANSTALTUNG, "Veranstaltung importiert", "Veranstaltung '" + v.name + "' via CSV importiert.", v.id);
                    } else {
                        LOG.warn("Veranstaltung '" + dto.name + "' übersprungen: Organisator (Admin) mit Email " + organisatorenEmail + " nicht gefunden.");
                    }
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
        Veranstaltung veranstaltung = Veranstaltung.findById(id);
        if (veranstaltung != null) {
            boolean deleted = Veranstaltung.deleteById(id);
            if (deleted) {
                protokollService.log(ProtokollKategorie.VERANSTALTUNG, "Veranstaltung gelöscht", "Veranstaltung '" + veranstaltung.name + "' gelöscht.", veranstaltung.id);
            }
            return deleted;
        }
        return false;
    }
}
