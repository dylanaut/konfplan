package kreyj.konfplan.persistence;

import io.quarkus.hibernate.orm.panache.Panache;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.transaction.Transactional;
import kreyj.konfplan.adapter.in.web.DatabaseCleaner;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
class GruppenkategorieTest extends DatabaseCleaner {

    private Veranstaltung neueVeranstaltung() {
        return neueVeranstaltung("Gruppenkategorie-Test");
    }


    private Veranstaltung neueVeranstaltung(String name) {
        Veranstaltung veranstaltung = new Veranstaltung();
        veranstaltung.setName(name + "-" + System.nanoTime());
        veranstaltung.setBeginntAm(LocalDateTime.now());
        veranstaltung.persist();
        return veranstaltung;
    }


    private Teilnehmer neuerTeilnehmer(String email) {
        Teilnehmer tn = new Teilnehmer();
        tn.assignLoginName(email);
        tn.setEmail(email);
        tn.persist();
        return tn;
    }


    @Test
    @Transactional
    void gruppenkategorieUndWerte_werdenPersistiertUndSindUeberVeranstaltungErreichbar() {
        Veranstaltung veranstaltung = neueVeranstaltung();

        Gruppenkategorie schule = new Gruppenkategorie(veranstaltung, "Schule", false, true);
        schule.persist();
        GruppenkategorieWert rks = new GruppenkategorieWert(schule, "RKS");
        rks.persist();
        GruppenkategorieWert mgl = new GruppenkategorieWert(schule, "MGL");
        mgl.persist();

        // Die inverse Seite der @OneToMany-Assoziation wird beim Persistieren des Kindes nicht
        // automatisch im bereits geladenen Parent-Objekt der Session nachgezogen - erst
        // flushen (die Inserts sind sonst noch nicht an die DB gesendet), dann frisch laden.
        Panache.getEntityManager().flush();
        Panache.getEntityManager().clear();
        Veranstaltung geladen = Veranstaltung.findById(veranstaltung.getId());
        assertThat(geladen.getGruppenkategorien()).hasSize(1);
        Gruppenkategorie geladeneKategorie = geladen.getGruppenkategorien().iterator().next();
        assertThat(geladeneKategorie.getName()).isEqualTo("Schule");
        assertThat(geladeneKategorie.isMehrwertig()).isFalse();
        assertThat(geladeneKategorie.isPflicht()).isTrue();
        assertThat(geladeneKategorie.getWerte()).extracting(GruppenkategorieWert::getWert).containsExactlyInAnyOrder("RKS", "MGL");
    }


    @Test
    @Transactional
    void addGruppenwert_beiEinwertigerKategorie_ersetztVorherigenWert() {
        Veranstaltung veranstaltung = neueVeranstaltung();
        Gruppenkategorie schule = new Gruppenkategorie(veranstaltung, "Schule", false, true);
        schule.persist();
        GruppenkategorieWert rks = new GruppenkategorieWert(schule, "RKS");
        rks.persist();
        GruppenkategorieWert mgl = new GruppenkategorieWert(schule, "MGL");
        mgl.persist();

        Teilnehmer tn = neuerTeilnehmer("tn-einwertig@test.com");
        tn.addGruppenwert(rks);
        assertThat(tn.getGruppenwerte()).containsExactly(rks);

        tn.addGruppenwert(mgl);
        assertThat(tn.getGruppenwerte()).describedAs("einwertige Kategorie: neuer Wert ersetzt den alten").containsExactly(mgl);
    }


    @Test
    @Transactional
    void addGruppenwert_beiMehrwertigerKategorie_erlaubtMehrereWerte() {
        Veranstaltung veranstaltung = neueVeranstaltung();
        Gruppenkategorie messe = new Gruppenkategorie(veranstaltung, "Messe", true, false);
        messe.persist();
        GruppenkategorieWert m1 = new GruppenkategorieWert(messe, "M_1");
        m1.persist();
        GruppenkategorieWert m2 = new GruppenkategorieWert(messe, "M_2");
        m2.persist();

        Teilnehmer tn = neuerTeilnehmer("tn-mehrwertig@test.com");
        tn.addGruppenwert(m1);
        tn.addGruppenwert(m2);

        assertThat(tn.getGruppenwerte()).containsExactlyInAnyOrder(m1, m2);
    }


    @Test
    @Transactional
    void removeGruppenwert_entferntGenauDiesenWert() {
        Veranstaltung veranstaltung = neueVeranstaltung();
        Gruppenkategorie klasse = new Gruppenkategorie(veranstaltung, "Klasse", false, true);
        klasse.persist();
        GruppenkategorieWert zehnA = new GruppenkategorieWert(klasse, "10a");
        zehnA.persist();

        Teilnehmer tn = neuerTeilnehmer("tn-remove@test.com");
        tn.addGruppenwert(zehnA);
        assertThat(tn.hatGruppenwert(zehnA)).isTrue();

        tn.removeGruppenwert(zehnA);
        assertThat(tn.hatGruppenwert(zehnA)).isFalse();
        assertThat(tn.getGruppenwerte()).isEmpty();
    }


    /**
     * Regressionstest: solange {@code Gruppenkategorie.werte} nie explizit angefasst wurde (z.B.
     * per direktem {@code new GruppenkategorieWert(kategorie, ...)} statt über eine Kategorie-
     * seitige Hinzufügemethode), sah Hibernates Cascade-Verarbeitung beim Löschen der Kategorie
     * die noch nicht in der Java-Kollektion nachgezogene Wert-Zeile faelschlich als verwaiste,
     * transiente Referenz an (TransientPropertyValueException statt kaskadierendem Löschen) -
     * behoben durch expliziten Sync der inversen Seite in {@code Gruppenkategorie#nimmWertAuf}.
     */
    @Test
    @Transactional
    void kategorieLoeschen_kaskadiertZumWert_ohneDassWerteZuvorGelesenWurde() {
        Veranstaltung veranstaltung = neueVeranstaltung();
        Gruppenkategorie klasse = new Gruppenkategorie(veranstaltung, "Klasse", false, true);
        klasse.persist();
        GruppenkategorieWert zehnA = new GruppenkategorieWert(klasse, "10a");
        zehnA.persist();
        Panache.getEntityManager().flush();

        klasse.delete();
        Panache.getEntityManager().flush();

        Gruppenkategorie geloeschteKategorie = Gruppenkategorie.findById(klasse.getId());
        GruppenkategorieWert geloeschterWert = GruppenkategorieWert.findById(zehnA.getId());
        assertThat(geloeschteKategorie).isNull();
        assertThat(geloeschterWert).isNull();
    }


    /**
     * Slice 3 (#690): Pflichtvortrag-Matching muss unabhängig davon greifen, ob ein Teilnehmer
     * über das alte flache Modell oder über das neue Gruppenkategorien-Modell derselben Gruppe
     * zugeordnet ist - sonst hätte die Verwaltungs-API aus Slice 2 keine tatsächliche Wirkung.
     */
    @Test
    @Transactional
    void istInGruppe_erkenntSowohlAltesAlsAuchNeuesModell() {
        Veranstaltung veranstaltung = neueVeranstaltung();
        Gruppenkategorie klasse = new Gruppenkategorie(veranstaltung, "Klasse", false, true);
        klasse.persist();
        GruppenkategorieWert zehnA = new GruppenkategorieWert(klasse, "10a");
        zehnA.persist();

        Teilnehmer altesModell = neuerTeilnehmer("tn-altes-modell@test.com");
        altesModell.addGruppe("10a");

        Teilnehmer neuesModell = neuerTeilnehmer("tn-neues-modell@test.com");
        neuesModell.addGruppenwert(zehnA);

        Teilnehmer keineGruppe = neuerTeilnehmer("tn-keine-gruppe@test.com");

        assertThat(altesModell.istInGruppe("10a", veranstaltung)).isTrue();
        assertThat(neuesModell.istInGruppe("10a", veranstaltung)).isTrue();
        assertThat(keineGruppe.istInGruppe("10a", veranstaltung)).isFalse();
        assertThat(altesModell.istInGruppe(null, veranstaltung)).isFalse();
    }


    @Test
    @Transactional
    void getGruppenTeilnehmer_findetTeilnehmerUeberBeideModelleUndScoptAufVeranstaltung() {
        Veranstaltung veranstaltung = neueVeranstaltung();
        Gruppenkategorie klasse = new Gruppenkategorie(veranstaltung, "Klasse", false, true);
        klasse.persist();
        GruppenkategorieWert zehnA = new GruppenkategorieWert(klasse, "10a");
        zehnA.persist();

        Teilnehmer altesModell = neuerTeilnehmer("tn-ggt-alt@test.com");
        altesModell.addGruppe("10a");
        altesModell.addVeranstaltung(veranstaltung);

        Teilnehmer neuesModell = neuerTeilnehmer("tn-ggt-neu@test.com");
        neuesModell.addGruppenwert(zehnA);
        neuesModell.addVeranstaltung(veranstaltung);

        // Gleicher Gruppenname "10a", aber in einer ANDEREN Veranstaltung - darf nicht gefunden werden.
        Veranstaltung zweiteVeranstaltung = neueVeranstaltung();
        Teilnehmer andereVeranstaltung = neuerTeilnehmer("tn-ggt-andere@test.com");
        andereVeranstaltung.addGruppe("10a");
        andereVeranstaltung.addVeranstaltung(zweiteVeranstaltung);

        List<Teilnehmer> gefunden = Teilnehmer.getGruppenTeilnehmer("10a", veranstaltung);

        assertThat(gefunden).containsExactlyInAnyOrder(altesModell, neuesModell);
    }
}
