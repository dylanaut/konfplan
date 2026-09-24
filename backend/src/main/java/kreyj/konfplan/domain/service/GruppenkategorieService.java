package kreyj.konfplan.domain.service;

import com.opencsv.bean.CsvToBean;
import com.opencsv.bean.CsvToBeanBuilder;
import io.quarkus.hibernate.orm.panache.Panache;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import kreyj.konfplan.adapter.in.web.dto.csv.GruppenkategorieCsvDto;
import kreyj.konfplan.domain.exception.BusinessException;
import kreyj.konfplan.persistence.Betrachter;
import kreyj.konfplan.persistence.Gruppenkategorie;
import kreyj.konfplan.persistence.GruppenkategorieWert;
import kreyj.konfplan.persistence.Nutzer;
import kreyj.konfplan.persistence.ProtokollKategorie;
import kreyj.konfplan.persistence.Teilnehmer;
import kreyj.konfplan.persistence.Veranstaltung;
import kreyj.konfplan.util.CsvHelper;
import org.apache.commons.lang3.StringUtils;
import org.jboss.logging.Logger;

import java.io.Reader;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;

/**
 * Verwaltet Gruppenkategorien einer Veranstaltung (z.B. "Schule", "Messe", "Klasse") und die
 * Zuordnung ihrer Werte zu Teilnehmern - ersetzt schrittweise die bisherige flache,
 * unstrukturierte {@code Teilnehmer.gruppen}/{@code Veranstaltung.gruppen} (siehe #690). Nur
 * Organisatoren/Administratoren dürfen Kategorien pflegen und Teilnehmer-Werte setzen (siehe
 * {@code @RolesAllowed} an {@code OrganisatorResource}, dem einzigen Aufrufer).
 */
@ApplicationScoped
public class GruppenkategorieService {

    private static final Logger LOG = Logger.getLogger(GruppenkategorieService.class);

    private final ProtokollService protokollService;


    public GruppenkategorieService(ProtokollService protokollService) {
        this.protokollService = protokollService;
    }


    @Transactional
    public List<Gruppenkategorie> getGruppenkategorien(Long veranstaltungId) {
        Veranstaltung veranstaltung = ladeVeranstaltung(veranstaltungId);
        return Gruppenkategorie.<Gruppenkategorie>find("veranstaltung", veranstaltung).list().stream()
            .sorted(Comparator.comparing(Gruppenkategorie::getName, String.CASE_INSENSITIVE_ORDER))
            .toList();
    }


    @Transactional
    public Gruppenkategorie createGruppenkategorie(Long veranstaltungId, String name, boolean mehrwertig, boolean pflicht) {
        if (StringUtils.isBlank(name)) {
            throw new BusinessException("Name der Gruppenkategorie darf nicht leer sein.");
        }
        Veranstaltung veranstaltung = ladeVeranstaltung(veranstaltungId);
        // Bewusst eine direkte Query statt veranstaltung.getGruppenkategorien(): diese
        // @OneToMany-Kollektion wird, sobald sie einmal in der Session geladen ist, bei
        // spaeteren Inserts innerhalb derselben Transaktion NICHT automatisch aktualisiert -
        // eine Query loest dagegen (Hibernate-Standardverhalten) vor der Ausfuehrung einen
        // Auto-Flush aus und sieht daher immer den aktuellen Stand.
        if (Gruppenkategorie.count("veranstaltung = ?1 and name = ?2", veranstaltung, name) > 0) {
            throw new BusinessException("Gruppenkategorie '" + name + "' existiert bereits.");
        }

        Gruppenkategorie kategorie = new Gruppenkategorie(veranstaltung, name, mehrwertig, pflicht);
        kategorie.persist();

        protokollService.log(ProtokollKategorie.STAMMDATEN, "Gruppenkategorie erstellt",
            "Gruppenkategorie '" + name + "' zu Veranstaltung '" + veranstaltung.getName() + "' hinzugefügt.",
            kategorie.getId(), veranstaltungId);
        return kategorie;
    }


    @Transactional
    public Gruppenkategorie updateGruppenkategorie(Long kategorieId, String neuerName, boolean mehrwertig, boolean pflicht) {
        if (StringUtils.isBlank(neuerName)) {
            throw new BusinessException("Name der Gruppenkategorie darf nicht leer sein.");
        }
        Gruppenkategorie kategorie = ladeKategorie(kategorieId);
        Veranstaltung veranstaltung = kategorie.getVeranstaltung();

        if (!kategorie.getName().equals(neuerName)
            && Gruppenkategorie.count("veranstaltung = ?1 and name = ?2", veranstaltung, neuerName) > 0) {
            throw new BusinessException("Gruppenkategorie '" + neuerName + "' existiert bereits.");
        }
        if (kategorie.isMehrwertig() && !mehrwertig && hatTeilnehmerMitMehrerenWerten(kategorieId)) {
            throw new BusinessException("Kategorie kann nicht auf einwertig geändert werden, da mindestens ein Teilnehmer "
                + "bereits mehrere Werte dieser Kategorie besitzt.");
        }

        kategorie.setName(neuerName);
        kategorie.setMehrwertig(mehrwertig);
        kategorie.setPflicht(pflicht);

        protokollService.log(ProtokollKategorie.STAMMDATEN, "Gruppenkategorie geändert",
            "Gruppenkategorie '" + neuerName + "' in Veranstaltung '" + veranstaltung.getName() + "' geändert.",
            kategorie.getId(), veranstaltung.getId());
        return kategorie;
    }


    @Transactional
    public void deleteGruppenkategorie(Long kategorieId) {
        Gruppenkategorie kategorie = ladeKategorie(kategorieId);
        Veranstaltung veranstaltung = kategorie.getVeranstaltung();

        // Teilnehmer-Zuordnungen zuerst loesen, dann Werte per Cascade/orphanRemoval ueber
        // kategorie.delete() mitloeschen (siehe Gruppenkategorie#nimmWertAuf fuer den
        // notwendigen bidirektionalen Java-Sync, der das zuverlaessig macht).
        for (GruppenkategorieWert wert : new HashSet<>(kategorie.getWerte())) {
            entferneVonAllenTeilnehmern(wert);
            entferneVonAllenBetrachtern(wert);
        }
        kategorie.delete();

        protokollService.log(ProtokollKategorie.STAMMDATEN, "Gruppenkategorie gelöscht",
            "Gruppenkategorie '" + kategorie.getName() + "' aus Veranstaltung '" + veranstaltung.getName() + "' entfernt.",
            kategorieId, veranstaltung.getId());
    }


    @Transactional
    public GruppenkategorieWert addWert(Long kategorieId, String wert) {
        if (StringUtils.isBlank(wert)) {
            throw new BusinessException("Wert darf nicht leer sein.");
        }
        Gruppenkategorie kategorie = ladeKategorie(kategorieId);
        if (GruppenkategorieWert.count("gruppenkategorie = ?1 and wert = ?2", kategorie, wert) > 0) {
            throw new BusinessException("Wert '" + wert + "' existiert in dieser Kategorie bereits.");
        }

        GruppenkategorieWert neuerWert = new GruppenkategorieWert(kategorie, wert);
        neuerWert.persist();

        protokollService.log(ProtokollKategorie.STAMMDATEN, "Gruppenkategorie-Wert erstellt",
            "Wert '" + wert + "' zu Gruppenkategorie '" + kategorie.getName() + "' hinzugefügt.",
            kategorie.getId(), kategorie.getVeranstaltung().getId());
        return neuerWert;
    }


    @Transactional
    public GruppenkategorieWert renameWert(Long wertId, String neuerWert) {
        if (StringUtils.isBlank(neuerWert)) {
            throw new BusinessException("Wert darf nicht leer sein.");
        }
        GruppenkategorieWert wert = ladeWert(wertId);
        Gruppenkategorie kategorie = wert.getGruppenkategorie();

        if (!wert.getWert().equals(neuerWert)
            && GruppenkategorieWert.count("gruppenkategorie = ?1 and wert = ?2", kategorie, neuerWert) > 0) {
            throw new BusinessException("Wert '" + neuerWert + "' existiert in dieser Kategorie bereits.");
        }

        wert.setWert(neuerWert);

        protokollService.log(ProtokollKategorie.STAMMDATEN, "Gruppenkategorie-Wert umbenannt",
            "Wert in Gruppenkategorie '" + kategorie.getName() + "' zu '" + neuerWert + "' umbenannt.",
            kategorie.getId(), kategorie.getVeranstaltung().getId());
        return wert;
    }


    @Transactional
    public void removeWert(Long wertId) {
        GruppenkategorieWert wert = ladeWert(wertId);
        Gruppenkategorie kategorie = wert.getGruppenkategorie();

        entferneVonAllenTeilnehmern(wert);
        entferneVonAllenBetrachtern(wert);
        kategorie.entferneWert(wert);
        wert.delete();

        protokollService.log(ProtokollKategorie.STAMMDATEN, "Gruppenkategorie-Wert gelöscht",
            "Wert aus Gruppenkategorie '" + kategorie.getName() + "' entfernt.",
            kategorie.getId(), kategorie.getVeranstaltung().getId());
    }


    @Transactional
    public void setGruppenwert(Long teilnehmerId, Long wertId) {
        Nutzer teilnehmer = ladeTeilnehmer(teilnehmerId);
        GruppenkategorieWert wert = ladeWert(wertId);
        pruefeVeranstaltungszugehoerigkeit(teilnehmer, wert);

        teilnehmer.addTeilnehmerGruppenwert(wert);

        protokollService.log(ProtokollKategorie.STAMMDATEN, "Teilnehmer-Gruppenwert gesetzt",
            teilnehmer.getFullName() + " -> '" + wert.getWert() + "' (" + wert.getGruppenkategorie().getName() + ")",
            teilnehmerId, wert.getGruppenkategorie().getVeranstaltung().getId());
    }


    @Transactional
    public void removeGruppenwert(Long teilnehmerId, Long wertId) {
        Nutzer teilnehmer = ladeTeilnehmer(teilnehmerId);
        GruppenkategorieWert wert = ladeWert(wertId);

        teilnehmer.removeTeilnehmerGruppenwert(wert);

        protokollService.log(ProtokollKategorie.STAMMDATEN, "Teilnehmer-Gruppenwert entfernt",
            teilnehmer.getFullName() + " -/-> '" + wert.getWert() + "' (" + wert.getGruppenkategorie().getName() + ")",
            teilnehmerId, wert.getGruppenkategorie().getVeranstaltung().getId());
    }


    /**
     * Importiert Gruppenkategorien samt Werten aus einer CSV-Datei (Spalten Kategorie;Mehrwertig;
     * Pflicht;Werte, Werte getrennt durch '|') - siehe #690. Muss vor dem Teilnehmer-Import
     * laufen, damit dessen Gruppenkategorie-Spalten (eine Spalte je hier angelegter Kategorie)
     * bereits Werte zum Zuordnen vorfinden. Idempotent: bereits vorhandene Kategorien/Werte
     * gleichen Namens werden übersprungen, nicht dupliziert.
     */
    @Transactional
    public int importFromCsv(Path csvFilePath, Long veranstaltungId) throws Exception {
        Veranstaltung veranstaltung = ladeVeranstaltung(veranstaltungId);

        int anzahlNeueKategorien = 0;
        try (Reader reader = CsvHelper.openCsvReader(csvFilePath)) {
            CsvToBean<GruppenkategorieCsvDto> csvToBean = new CsvToBeanBuilder<GruppenkategorieCsvDto>(reader)
                .withType(GruppenkategorieCsvDto.class)
                .withFilter(line -> line.length > 0 && !line[0].startsWith("#"))
                .withIgnoreEmptyLine(true)
                .withIgnoreLeadingWhiteSpace(true)
                .withSeparator(';')
                .withThrowExceptions(false).build();

            List<GruppenkategorieCsvDto> beans = csvToBean.parse();

            csvToBean.getCapturedExceptions().forEach(e ->
                LOG.error("CSV-Parsing-Fehler in " + csvFilePath.getFileName() + " (Zeile " + e.getLineNumber() + "): " + e.getMessage()));

            for (GruppenkategorieCsvDto dto : beans) {
                if (StringUtils.isBlank(dto.kategorie)) {
                    continue;
                }
                String name = dto.kategorie.trim();

                Gruppenkategorie kategorie = Gruppenkategorie.<Gruppenkategorie>find(
                    "veranstaltung = ?1 and name = ?2", veranstaltung, name).firstResult();
                if (null == kategorie) {
                    kategorie = new Gruppenkategorie(veranstaltung, name, dto.mehrwertig, dto.pflicht);
                    kategorie.persist();
                    anzahlNeueKategorien++;
                }

                if (StringUtils.isNotBlank(dto.werte)) {
                    for (String token : dto.werte.split("\\|")) {
                        String wert = token.trim();
                        if (StringUtils.isBlank(wert)) {
                            continue;
                        }
                        if (GruppenkategorieWert.count("gruppenkategorie = ?1 and wert = ?2", kategorie, wert) == 0) {
                            new GruppenkategorieWert(kategorie, wert).persist();
                        }
                    }
                }
            }
        }

        protokollService.log(ProtokollKategorie.STAMMDATEN, "Gruppenkategorien importiert",
            anzahlNeueKategorien + " Gruppenkategorie(n) aus " + csvFilePath.getFileName()
                + " für Veranstaltung '" + veranstaltung.getName() + "' importiert.", null, veranstaltungId);
        return anzahlNeueKategorien;
    }


    // -------------------------------------------------------------------
    // Helper methods
    // -------------------------------------------------------------------


    private boolean hatTeilnehmerMitMehrerenWerten(Long kategorieId) {
        List<Long> teilnehmerMitMehrerenWerten = Panache.getEntityManager().createQuery(
                "select t.id from Teilnehmer t join t.teilnehmerGruppenwerte w where w.gruppenkategorie.id = :kategorieId "
                    + "group by t.id having count(w) > 1", Long.class)
            .setParameter("kategorieId", kategorieId)
            .getResultList();
        return !teilnehmerMitMehrerenWerten.isEmpty();
    }


    private void entferneVonAllenTeilnehmern(GruppenkategorieWert wert) {
        for (Nutzer teilnehmer : new HashSet<>(wert.getTeilnehmer())) {
            teilnehmer.removeTeilnehmerGruppenwert(wert);
        }
    }


    private void entferneVonAllenBetrachtern(GruppenkategorieWert wert) {
        for (Nutzer betrachter : new HashSet<>(wert.getBetrachter())) {
            betrachter.removeBetrachterGruppenwert(wert);
        }
    }


    private void pruefeVeranstaltungszugehoerigkeit(Nutzer teilnehmer, GruppenkategorieWert wert) {
        Veranstaltung veranstaltung = wert.getGruppenkategorie().getVeranstaltung();
        if (!teilnehmer.getVeranstaltungen().contains(veranstaltung)) {
            throw new BusinessException("Teilnehmer ist nicht Teil der Veranstaltung, zu der diese Gruppenkategorie gehört.");
        }
    }


    private Veranstaltung ladeVeranstaltung(Long veranstaltungId) {
        Veranstaltung veranstaltung = Veranstaltung.findById(veranstaltungId);
        if (null == veranstaltung) {
            throw new BusinessException("Veranstaltung mit ID " + veranstaltungId + " nicht gefunden.");
        }
        return veranstaltung;
    }


    private Gruppenkategorie ladeKategorie(Long kategorieId) {
        Gruppenkategorie kategorie = Gruppenkategorie.findById(kategorieId);
        if (null == kategorie) {
            throw new BusinessException("Gruppenkategorie mit ID " + kategorieId + " nicht gefunden.");
        }
        return kategorie;
    }


    private GruppenkategorieWert ladeWert(Long wertId) {
        GruppenkategorieWert wert = GruppenkategorieWert.findById(wertId);
        if (null == wert) {
            throw new BusinessException("Gruppenkategorie-Wert mit ID " + wertId + " nicht gefunden.");
        }
        return wert;
    }


    private Nutzer ladeTeilnehmer(Long teilnehmerId) {
        Nutzer teilnehmer = Nutzer.findById(teilnehmerId);
        if (null == teilnehmer || !teilnehmer.hatRolle("TEILNEHMER")) {
            throw new BusinessException("Teilnehmer mit ID " + teilnehmerId + " nicht gefunden.");
        }
        return teilnehmer;
    }
}
