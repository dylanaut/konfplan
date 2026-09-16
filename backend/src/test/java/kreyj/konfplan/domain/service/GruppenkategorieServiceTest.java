package kreyj.konfplan.domain.service;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import kreyj.konfplan.adapter.in.web.DatabaseCleaner;
import kreyj.konfplan.domain.exception.BusinessException;
import kreyj.konfplan.persistence.Betrachter;
import kreyj.konfplan.persistence.Gruppenkategorie;
import kreyj.konfplan.persistence.GruppenkategorieWert;
import kreyj.konfplan.persistence.Teilnehmer;
import kreyj.konfplan.persistence.Veranstaltung;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@QuarkusTest
class GruppenkategorieServiceTest extends DatabaseCleaner {

    @Inject
    GruppenkategorieService gruppenkategorieService;

    private Long veranstaltungId;


    @BeforeEach
    @Transactional
    void setup() {
        Veranstaltung veranstaltung = new Veranstaltung();
        veranstaltung.setName("Gruppenkategorie-Service-Test");
        veranstaltung.setBeginntAm(LocalDateTime.now());
        veranstaltung.persist();
        veranstaltungId = veranstaltung.getId();
    }


    private Teilnehmer neuerTeilnehmer(String email) {
        Teilnehmer tn = new Teilnehmer();
        tn.assignLoginName(email);
        tn.setEmail(email);
        tn.persist();
        // veranstaltung stammt aus der @BeforeEach-Transaktion und ist in dieser Test-
        // Transaktion detached - Nutzer.veranstaltungen cascade-persisted (PERSIST) beim
        // addVeranstaltung(...) sonst ein detached Entity (siehe gleiches Problem in
        // PlanServiceTest dieser Session).
        tn.addVeranstaltung(Veranstaltung.findById(veranstaltungId));
        return tn;
    }


    private Betrachter neuerBetrachter(String email) {
        Betrachter b = new Betrachter();
        b.assignLoginName(email);
        b.setEmail(email);
        b.persist();
        b.addVeranstaltung(Veranstaltung.findById(veranstaltungId));
        return b;
    }


    @Test
    @Transactional
    void createGruppenkategorie_undGetGruppenkategorien() {
        gruppenkategorieService.createGruppenkategorie(veranstaltungId, "Schule", false, true);

        List<Gruppenkategorie> kategorien = gruppenkategorieService.getGruppenkategorien(veranstaltungId);

        assertThat(kategorien).hasSize(1);
        assertThat(kategorien.get(0).getName()).isEqualTo("Schule");
        assertThat(kategorien.get(0).isMehrwertig()).isFalse();
        assertThat(kategorien.get(0).isPflicht()).isTrue();
    }


    @Test
    @Transactional
    void createGruppenkategorie_beiDoppeltemNamen_wirftBusinessException() {
        gruppenkategorieService.createGruppenkategorie(veranstaltungId, "Schule", false, true);

        assertThatThrownBy(() -> gruppenkategorieService.createGruppenkategorie(veranstaltungId, "Schule", true, false))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("existiert bereits");
    }


    @Test
    @Transactional
    void addWert_beiDoppeltemWertInDerselbenKategorie_wirftBusinessException() {
        Gruppenkategorie schule = gruppenkategorieService.createGruppenkategorie(veranstaltungId, "Schule", false, true);
        gruppenkategorieService.addWert(schule.getId(), "RKS");

        assertThatThrownBy(() -> gruppenkategorieService.addWert(schule.getId(), "RKS"))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("existiert");
    }


    @Test
    @Transactional
    void setGruppenwert_beiEinwertigerKategorie_ersetztVorherigenWert() {
        Gruppenkategorie schule = gruppenkategorieService.createGruppenkategorie(veranstaltungId, "Schule", false, true);
        GruppenkategorieWert rks = gruppenkategorieService.addWert(schule.getId(), "RKS");
        GruppenkategorieWert mgl = gruppenkategorieService.addWert(schule.getId(), "MGL");
        Teilnehmer tn = neuerTeilnehmer("tn-service-einwertig@test.com");

        gruppenkategorieService.setGruppenwert(tn.getId(), rks.getId());
        gruppenkategorieService.setGruppenwert(tn.getId(), mgl.getId());

        Teilnehmer aktualisiert = Teilnehmer.findById(tn.getId());
        assertThat(aktualisiert.getGruppenwerte()).containsExactly(mgl);
    }


    @Test
    @Transactional
    void setGruppenwert_beiTeilnehmerAusserhalbDerVeranstaltung_wirftBusinessException() {
        Gruppenkategorie schule = gruppenkategorieService.createGruppenkategorie(veranstaltungId, "Schule", false, true);
        GruppenkategorieWert rks = gruppenkategorieService.addWert(schule.getId(), "RKS");

        Teilnehmer tn = new Teilnehmer();
        tn.assignLoginName("tn-fremd@test.com");
        tn.setEmail("tn-fremd@test.com");
        tn.persist();
        // bewusst NICHT zur Veranstaltung hinzugefuegt

        assertThatThrownBy(() -> gruppenkategorieService.setGruppenwert(tn.getId(), rks.getId()))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("nicht Teil der Veranstaltung");
    }


    @Test
    @Transactional
    void removeGruppenwert_entferntZuordnung() {
        Gruppenkategorie klasse = gruppenkategorieService.createGruppenkategorie(veranstaltungId, "Klasse", false, true);
        GruppenkategorieWert zehnA = gruppenkategorieService.addWert(klasse.getId(), "10a");
        Teilnehmer tn = neuerTeilnehmer("tn-service-remove@test.com");
        gruppenkategorieService.setGruppenwert(tn.getId(), zehnA.getId());

        gruppenkategorieService.removeGruppenwert(tn.getId(), zehnA.getId());

        Teilnehmer aktualisiert = Teilnehmer.findById(tn.getId());
        assertThat(aktualisiert.getGruppenwerte()).isEmpty();
    }


    @Test
    @Transactional
    void updateGruppenkategorie_vonMehrwertigAufEinwertig_beiTeilnehmerMitMehrerenWerten_wirftBusinessException() {
        Gruppenkategorie messe = gruppenkategorieService.createGruppenkategorie(veranstaltungId, "Messe", true, false);
        GruppenkategorieWert m1 = gruppenkategorieService.addWert(messe.getId(), "M_1");
        GruppenkategorieWert m2 = gruppenkategorieService.addWert(messe.getId(), "M_2");
        Teilnehmer tn = neuerTeilnehmer("tn-service-mehrwertig@test.com");
        gruppenkategorieService.setGruppenwert(tn.getId(), m1.getId());
        gruppenkategorieService.setGruppenwert(tn.getId(), m2.getId());

        assertThatThrownBy(() -> gruppenkategorieService.updateGruppenkategorie(messe.getId(), "Messe", false, false))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("mehrere Werte");
    }


    @Test
    @Transactional
    void deleteGruppenkategorie_entferntAuchZuordnungenBeiTeilnehmern() {
        Gruppenkategorie klasse = gruppenkategorieService.createGruppenkategorie(veranstaltungId, "Klasse", false, true);
        GruppenkategorieWert zehnA = gruppenkategorieService.addWert(klasse.getId(), "10a");
        Teilnehmer tn = neuerTeilnehmer("tn-service-delete@test.com");
        gruppenkategorieService.setGruppenwert(tn.getId(), zehnA.getId());

        gruppenkategorieService.deleteGruppenkategorie(klasse.getId());

        Gruppenkategorie geloeschteKategorie = Gruppenkategorie.findById(klasse.getId());
        GruppenkategorieWert geloeschterWert = GruppenkategorieWert.findById(zehnA.getId());
        assertThat(geloeschteKategorie).isNull();
        assertThat(geloeschterWert).isNull();
        Teilnehmer aktualisiert = Teilnehmer.findById(tn.getId());
        assertThat(aktualisiert.getGruppenwerte()).isEmpty();
    }


    @Test
    @Transactional
    void deleteGruppenkategorie_entferntAuchZuordnungenBeiBetrachtern() {
        Gruppenkategorie klasse = gruppenkategorieService.createGruppenkategorie(veranstaltungId, "Klasse", false, true);
        GruppenkategorieWert zehnA = gruppenkategorieService.addWert(klasse.getId(), "10a");
        Betrachter betrachter = neuerBetrachter("b-service-delete@test.com");
        betrachter.addGruppenwert(zehnA);

        gruppenkategorieService.deleteGruppenkategorie(klasse.getId());

        Gruppenkategorie geloeschteKategorie = Gruppenkategorie.findById(klasse.getId());
        GruppenkategorieWert geloeschterWert = GruppenkategorieWert.findById(zehnA.getId());
        assertThat(geloeschteKategorie).isNull();
        assertThat(geloeschterWert).isNull();
        Betrachter aktualisiert = Betrachter.findById(betrachter.getId());
        assertThat(aktualisiert.getGruppenwerte()).isEmpty();
    }


    @Test
    @Transactional
    void importFromCsv_legtKategorienUndWerteAn(@TempDir Path tempDir) throws Exception {
        Path csv = tempDir.resolve("gruppenkategorien.csv");
        Files.writeString(csv, """
            Kategorie;Mehrwertig;Pflicht;Werte
            Klasse;false;true;9a|9b|10a
            Schule;true;false;MGL|RKS
            """, StandardCharsets.UTF_8);

        int anzahl = gruppenkategorieService.importFromCsv(csv, veranstaltungId);

        assertThat(anzahl).isEqualTo(2);
        List<Gruppenkategorie> kategorien = gruppenkategorieService.getGruppenkategorien(veranstaltungId);
        assertThat(kategorien).extracting(Gruppenkategorie::getName).containsExactlyInAnyOrder("Klasse", "Schule");

        Gruppenkategorie klasse = kategorien.stream().filter(k -> "Klasse".equals(k.getName())).findFirst().orElseThrow();
        assertThat(klasse.isMehrwertig()).isFalse();
        assertThat(klasse.isPflicht()).isTrue();
        assertThat(klasse.getWerte()).extracting(GruppenkategorieWert::getWert).containsExactlyInAnyOrder("9a", "9b", "10a");

        Gruppenkategorie schule = kategorien.stream().filter(k -> "Schule".equals(k.getName())).findFirst().orElseThrow();
        assertThat(schule.isMehrwertig()).isTrue();
        assertThat(schule.getWerte()).extracting(GruppenkategorieWert::getWert).containsExactlyInAnyOrder("MGL", "RKS");
    }


    @Test
    @Transactional
    void importFromCsv_istIdempotent_beiWiederholtemImport(@TempDir Path tempDir) throws Exception {
        Path csv = tempDir.resolve("gruppenkategorien.csv");
        Files.writeString(csv, """
            Kategorie;Mehrwertig;Pflicht;Werte
            Klasse;false;true;9a|9b
            """, StandardCharsets.UTF_8);

        gruppenkategorieService.importFromCsv(csv, veranstaltungId);
        int anzahlZweiterLauf = gruppenkategorieService.importFromCsv(csv, veranstaltungId);

        assertThat(anzahlZweiterLauf)
            .describedAs("bereits vorhandene Kategorie darf beim zweiten Import nicht erneut gezaehlt/angelegt werden")
            .isZero();
        List<Gruppenkategorie> kategorien = gruppenkategorieService.getGruppenkategorien(veranstaltungId);
        assertThat(kategorien).hasSize(1);
        assertThat(kategorien.get(0).getWerte()).extracting(GruppenkategorieWert::getWert)
            .containsExactlyInAnyOrder("9a", "9b");
    }
}
