package kreyj.konfplan.application.service;

import com.opencsv.bean.CsvToBean;
import com.opencsv.bean.CsvToBeanBuilder;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.OptimisticLockException;
import jakarta.transaction.Transactional;
import kreyj.konfplan.persistence.Admin;
import kreyj.konfplan.persistence.Gebaeude;
import kreyj.konfplan.persistence.Nutzer;
import kreyj.konfplan.persistence.ProtokollKategorie;
import kreyj.konfplan.persistence.Raum;
import kreyj.konfplan.persistence.Veranstaltung;
import kreyj.konfplan.presentation.dto.GebaeudeSimpleDto;
import kreyj.konfplan.presentation.dto.RaumDto;
import kreyj.konfplan.presentation.dto.VeranstaltungDto;
import kreyj.konfplan.presentation.dto.csv.VeranstaltungCsvDto;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.jboss.logging.Logger;

import java.io.FileReader;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static kreyj.konfplan.util.DateHelper.DATE_FORMAT;

@ApplicationScoped
public class VeranstaltungService {
    private static final Logger LOG = Logger.getLogger(VeranstaltungService.class);

    private final ProtokollService protokollService; // Inject ProtokollService

    public VeranstaltungService(ProtokollService protokollService) {
        this.protokollService = protokollService;
    }


    public List<Veranstaltung> listAll() {
        return Veranstaltung.listAll();
    }

    public Veranstaltung findById(Long id) {
        return Veranstaltung.findById(id);
    }

    @Transactional
    public VeranstaltungDto save(VeranstaltungDto dto) {
        Veranstaltung v;

        if (dto.id != null) {
            v = Veranstaltung.findById(dto.id);
            if (v == null) {
                return null;
            }

            // Optimistic Locking Prüfung
            if (dto.version != null && !dto.version.equals(v.getVersion())) {
                throw new OptimisticLockException("Die Veranstaltung wurde in der Zwischenzeit von einem anderen Benutzer geändert.");
            }
        } else {
            v = new Veranstaltung();
        }

        v.setName(dto.name);
        v.setBeginntAm(dto.beginntAm);
        v.setEndetAm(dto.endetAm);
        v.setDeadlineReferenten(dto.deadlineReferenten);
        v.setDeadlineTeilnehmer(dto.deadlineTeilnehmer);
        v.setLogo(dto.logo);
        v.setLogo_link(dto.logo_link);

        // Gebäude zuweisen
        if (dto.gebaeude != null) {
            for (var gDto : dto.gebaeude) {
                Gebaeude g = Gebaeude.findById(gDto.id);
                if (g != null) {
                    v.addGebaeude(g);
                }
            }
        }

        // Organisatoren zuweisen
        if (CollectionUtils.isNotEmpty(dto.organisatorIds)) {
            // alte Admins entfernen und neue zufügen
            v.getNutzer().stream().filter(u -> u instanceof Admin)
                    .forEach(v::removeNutzer);
            for (Long adminId : dto.organisatorIds) {
                Admin a = Admin.findById(adminId);
                if (a == null) {
                    LOG.warn("Unbekannter Admin mit ID " + adminId + " beim Aktualisieren der Veranstaltung '" + v.getName() + "'.");
                } else {
                    v.addNutzer(a);
                }
            }
        }

        if (dto.id == null) {
            v.persist();
            protokollService.log(ProtokollKategorie.VERANSTALTUNG, "Veranstaltung erstellt", "Neue Veranstaltung '" + v.getName() + "' erstellt.", v.getId());
        } else {
            // ZWINGEND ERFORDERLICH FÜR OPTIMISTIC LOCKING RESPONSE:
            // Hibernate zwingen, das Update jetzt durchzuführen, damit persistence.getVersion() hochgezählt wird.
            v.persistAndFlush();
            protokollService.log(ProtokollKategorie.VERANSTALTUNG, "Veranstaltung aktualisiert", "Veranstaltung '" + v.getName() + "' aktualisiert.", v.getId());
        }
        return mapVeranstaltungToDto(v);
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
                        v.setName(dto.name);
                        try {
                            v.setBeginntAm(LocalDateTime.parse(dto.beginntAm, DATE_FORMAT));
                            if (dto.endetAm != null && !dto.endetAm.isEmpty()) {
                                v.setEndetAm(LocalDateTime.parse(dto.endetAm, DATE_FORMAT));
                            }
                        } catch (Exception e) {
                            LOG.error("Fehler beim Parsen des Datums für Veranstaltung '" + dto.name + "': " + e.getMessage());
                            continue;
                        }
                        v.setLogo(dto.logo);
                        v.setLogo_link(dto.logo_link);
                        v.persist();

                        // Admin verknüpfen
                        admin.addVeranstaltung(v);

                        if (StringUtils.isNotBlank(dto.gebaeudeNamen)) {
                            Arrays.stream(dto.gebaeudeNamen.split("\\|")).map(String::trim).forEach(name -> {
                                Gebaeude g = Gebaeude.find("name", name).firstResult();
                                if (g != null) {
                                    v.addGebaeude(g);
                                } else {
                                    LOG.warn("Veranstaltung '" + dto.name + "': Gebäude nicht gefunden: '" + name + "'");
                                }
                            });
                        }

                        if (StringUtils.isNotBlank(dto.gruppen)) {
                            Arrays.stream(dto.gruppen.split("\\|")).map(String::trim).forEach(v::addGruppe);
                        }

                        count++;
                        protokollService.log(ProtokollKategorie.VERANSTALTUNG, "Veranstaltung importiert", "Veranstaltung '" + v.getName() + "' via CSV importiert.", v.getId());
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
                protokollService.log(ProtokollKategorie.VERANSTALTUNG, "Veranstaltung gelöscht", "Veranstaltung '" + veranstaltung.getName() + "' gelöscht.", veranstaltung.getId());
            }
            return deleted;
        }
        return false;
    }


    // -------------------------------------------------------------------
    // mapper methods
    // -------------------------------------------------------------------
    public static VeranstaltungDto mapVeranstaltungToDto(Veranstaltung v) {
        VeranstaltungDto dto = new VeranstaltungDto();
        dto.id = v.getId();
        dto.version = v.getVersion();

        dto.name = v.getName();
        dto.beginntAm = v.getBeginntAm();
        dto.endetAm = v.getEndetAm();
        dto.deadlineReferenten = v.getDeadlineReferenten();
        dto.deadlineTeilnehmer = v.getDeadlineTeilnehmer();
        dto.logo = v.getLogo();
        dto.logo_link = v.getLogo_link();

        // Organisatoren filtern und hinzufügen
        if (v.getNutzer() != null) {
            v.getNutzer().stream()
                    .filter(u -> u instanceof Admin)
                    .forEach(u -> {
                        dto.organisatorIds.add(u.getId());
                        dto.organisatorNamen.add(u.getLastName());
                    });
        }

        dto.gebaeude = v.getGebaeude().stream().map(VeranstaltungService::mapToDto).toList();
        dto.gruppen = v.getGruppen();

        return dto;
    }

    public static GebaeudeSimpleDto mapToDto(Gebaeude gebaeude) {
        GebaeudeSimpleDto dto = new GebaeudeSimpleDto();
        dto.id = gebaeude.getId();
        dto.version = gebaeude.getVersion();

        dto.name = gebaeude.getName();
        dto.strasse = gebaeude.getStrasse();
        dto.hausnummer = gebaeude.getHausnummer();
        dto.ort = gebaeude.getOrt();
        dto.postleitzahl = gebaeude.getPostleitzahl();
        dto.typ = gebaeude.getTyp();

        dto.raeume = gebaeude.getRaeume().stream()
                .map(VeranstaltungService::mapRaumToDto)
                .toList();

        return dto;
    }

    public static RaumDto mapRaumToDto(Raum raum) {
        RaumDto dto = new RaumDto();

        dto.id = raum.getId();
        dto.version = raum.getVersion();
        dto.name = raum.getName();
        dto.kapazitaet = raum.getKapazitaet();
        dto.etage = raum.getEtage();

        dto.gebaeudeId = raum.getGebaeude().getId();

        return dto;
    }

}
