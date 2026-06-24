package kreyj.konfplan.application.service;

import com.opencsv.bean.CsvToBean;
import com.opencsv.bean.CsvToBeanBuilder;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.OptimisticLockException;
import jakarta.transaction.Transactional;
import kreyj.konfplan.adapter.in.web.dto.GebaeudeSimpleDto;
import kreyj.konfplan.adapter.in.web.dto.RaumDto;
import kreyj.konfplan.adapter.in.web.dto.VeranstaltungDto;
import kreyj.konfplan.adapter.in.web.dto.csv.VeranstaltungCsvDto;
import kreyj.konfplan.application.port.in.VeranstaltungServiceInterface;
import kreyj.konfplan.persistence.Admin;
import kreyj.konfplan.persistence.Gebaeude;
import kreyj.konfplan.persistence.Nutzer;
import kreyj.konfplan.persistence.ProtokollKategorie;
import kreyj.konfplan.persistence.Raum;
import kreyj.konfplan.persistence.Veranstaltung;
import kreyj.konfplan.util.StringHelper;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.jboss.logging.Logger;

import java.io.FileReader;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static kreyj.konfplan.util.DateHelper.DATE_FORMAT;

@ApplicationScoped
public class VeranstaltungService implements VeranstaltungServiceInterface {
    private static final Logger LOG = Logger.getLogger(VeranstaltungService.class);

    private final ProtokollService protokollService; // Inject ProtokollService


    public VeranstaltungService(ProtokollService protokollService) {
        this.protokollService = protokollService;
    }


    @Override
    public List<Veranstaltung> listAll() {
        return Veranstaltung.listAll();
    }


    @Override
    public Veranstaltung findById(Long id) {
        return Veranstaltung.findById(id);
    }


    @Transactional
    @Override
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

        v.setName(dto.getName());

        v.setBeginntAm(dto.getBeginntAm());
        v.setEndetAm(dto.getEndetAm());

        if (dto.getBeginntAm() != null && dto.getEndetAm() != null && dto.getBeginntAm().isAfter(dto.getEndetAm())) {
            throw new IllegalArgumentException("Beginn der Veranstaltung muss vor Ende liegen.");
        }

        v.setDeadlineReferenten(smartDeadLine(dto.getDeadlineReferenten(), dto.getBeginntAm(), 7));
        v.setDeadlineTeilnehmer(smartDeadLine(dto.getDeadlineTeilnehmer(), dto.getBeginntAm(), 3));

        v.setLogo(dto.getLogo());
        v.setLogo_link(dto.getLogo_link());

        // Gebäude zuweisen
        if (dto.getGebaeude() != null) {
            for (var gDto : dto.getGebaeude()) {
                Gebaeude g = Gebaeude.findById(dto.id);
                if (g != null) {
                    v.addGebaeude(g);
                }
            }
        }

        // Organisatoren zuweisen
        if (CollectionUtils.isNotEmpty(dto.getOrganisatorIds())) {
            // alte Admins entfernen und neue zufügen
            ArrayList<Nutzer> alteNutzer = new ArrayList<>(v.getNutzer());
            alteNutzer.stream()
                .filter(u -> u instanceof Admin)
                .forEach(v::removeNutzer);
            for (Long adminId : dto.getOrganisatorIds()) {
                Admin a = Admin.findById(adminId);
                if (a == null) {
                    LOG.warn("Unbekannter Admin mit ID " + adminId + " beim Aktualisieren der Veranstaltung '" + v.getName() + "'.");
                } else {
                    v.addNutzer(a);
                }
            }
        }

        if (dto.id == null) {
            v.persistAndFlush();
            protokollService.log(ProtokollKategorie.VERANSTALTUNG, "Veranstaltung erstellt",
                "Neue Veranstaltung '" + v.getName() + "' erstellt.", v.getId());
        } else {
            // ZWINGEND ERFORDERLICH FÜR OPTIMISTIC LOCKING RESPONSE:
            // Hibernate zwingen, das Update jetzt durchzuführen, damit persistence.version() hochgezählt wird.
            v.persistAndFlush();
            protokollService.log(ProtokollKategorie.VERANSTALTUNG, "Veranstaltung aktualisiert",
                "Veranstaltung '" + v.getName() + "' aktualisiert.", v.getId());
        }
        return mapVeranstaltungToDto(v);
    }


    private static LocalDateTime smartDeadLine(LocalDateTime dtoDeadline, LocalDateTime beginntAm, int numDays) {
        if (null == dtoDeadline) {
            if (null == beginntAm) {
                return LocalDateTime.now().minusDays(numDays);
            } else {
                return beginntAm.minusDays(numDays);
            }
        } else {
            return dtoDeadline;
        }
    }


    @Transactional
    @Override
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

            for (VeranstaltungCsvDto csvDto : beans) {
                if (StringUtils.isBlank(csvDto.name)) {
                    LOG.warn("Veranstaltung übersprungen: Name fehlt.");
                    continue;
                }

                Veranstaltung veranstaltung = new Veranstaltung();
                veranstaltung.setName(csvDto.name);
                try {
                    veranstaltung.setBeginntAm(LocalDateTime.parse(csvDto.beginntAm, DATE_FORMAT));
                    if (csvDto.endetAm != null && !csvDto.endetAm.isEmpty()) {
                        veranstaltung.setEndetAm(LocalDateTime.parse(csvDto.endetAm, DATE_FORMAT));
                    }
                } catch (Exception e) {
                    LOG.error("Fehler beim Parsen des Datums für Veranstaltung '" + csvDto.name + "': " + e.getMessage());
                    continue;
                }

                veranstaltung.setDeadlineReferenten(veranstaltung.getBeginntAm().minusDays(7));
                veranstaltung.setDeadlineTeilnehmer(veranstaltung.getBeginntAm().minusDays(3));

                veranstaltung.setLogo(csvDto.logo);
                veranstaltung.setLogo_link(csvDto.logo_link);

                if (StringUtils.isNotBlank(csvDto.gruppen)) {
                    Arrays.stream(csvDto.gruppen.split("\\|")).map(String::trim).forEach(veranstaltung::addGruppe);
                }

                if (StringUtils.isNotBlank(csvDto.gebaeudeNamen)) {
                    Arrays.stream(csvDto.gebaeudeNamen.split("\\|")).map(String::trim).forEach(name -> {
                        Gebaeude g = Gebaeude.find("name", name).firstResult();
                        if (g != null) {
                            veranstaltung.addGebaeude(g);
                        } else {
                            LOG.warn("Veranstaltung '" + csvDto.name + "': Gebäude nicht gefunden: '" + name + "'");
                        }
                    });
                }

                veranstaltung.persistAndFlush();

                String[] organisatorenEmails = StringUtils.split(csvDto.organisatorenEmails, ",");
                for (String organisatorenEmail : organisatorenEmails) {
                    Nutzer admin = Nutzer.findByEmail(organisatorenEmail.trim());

                    if (admin instanceof Admin) {
                        // Admin verknüpfen
                        admin.addVeranstaltung(veranstaltung);
                    } else {
                        LOG.warn("Veranstaltung '" + csvDto.name + "' übersprungen: Organisator (Admin) mit Email " + organisatorenEmail + " nicht gefunden.");
                    }
                }

                count++;
                protokollService.log(ProtokollKategorie.VERANSTALTUNG, "Veranstaltung importiert",
                    "Veranstaltung '" + veranstaltung.getName() + "' via CSV importiert.", veranstaltung.getId());
            }
        } catch (Exception e) {
            LOG.error("Kritischer Fehler beim Importieren der Veranstaltungen aus CSV: " + csvFilePath, e);
            throw e;
        }
        LOG.info("Veranstaltungs-Import abgeschlossen: " + count + " Veranstaltungen aus " + csvFilePath + " importiert.");
        return count;
    }


    @Transactional
    @Override
    public boolean delete(Long id) {
        Veranstaltung veranstaltung = Veranstaltung.findById(id);
        if (veranstaltung != null) {
            boolean deleted = Veranstaltung.deleteById(id);
            if (deleted) {
                protokollService.log(ProtokollKategorie.VERANSTALTUNG, "Veranstaltung gelöscht",
                    "Veranstaltung '" + veranstaltung.getName() + "' gelöscht.", veranstaltung.getId());
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

        dto.setName(v.getName());
        dto.setBeginntAm(v.getBeginntAm());
        dto.setEndetAm(v.getEndetAm());
        dto.setDeadlineReferenten(v.getDeadlineReferenten());
        dto.setDeadlineTeilnehmer(v.getDeadlineTeilnehmer());
        dto.setLogo(v.getLogo());
        dto.setLogo_link(v.getLogo_link());

        // Organisatoren filtern und hinzufügen
        if (v.getNutzer() != null) {
            v.getNutzer().stream()
                .filter(u -> u instanceof Admin)
                .forEach(u -> {
                    dto.getOrganisatorIds().add(u.getId());
                    dto.getOrganisatorNamen().add(u.getLastName());
                });
        }

        dto.setGebaeude(v.getGebaeude().stream().map(VeranstaltungService::mapGebaeudeToDto).toList());
        dto.setGruppen(v.getGruppen().stream().sorted(StringHelper.NUM_OR_ALPHA_COMPARATOR).toList());

        return dto;
    }


    public static GebaeudeSimpleDto mapGebaeudeToDto(Gebaeude gebaeude) {
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
        dto.gebaeudeName = raum.getGebaeude().getName();

        return dto;
    }
}
