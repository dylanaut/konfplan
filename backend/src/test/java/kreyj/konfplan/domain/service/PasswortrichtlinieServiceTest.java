package kreyj.konfplan.domain.service;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import kreyj.konfplan.adapter.in.web.DatabaseCleaner;
import kreyj.konfplan.adapter.in.web.dto.PasswortrichtlinieAnfrageDto;
import kreyj.konfplan.adapter.in.web.dto.PasswortrichtlinieDto;
import kreyj.konfplan.domain.exception.BusinessException;
import kreyj.konfplan.persistence.Passwortrichtlinie;
import kreyj.konfplan.persistence.Referent;
import kreyj.konfplan.persistence.Teilnehmer;
import kreyj.konfplan.persistence.Veranstaltung;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@QuarkusTest
class PasswortrichtlinieServiceTest extends DatabaseCleaner {

    @Inject
    PasswortrichtlinieService passwortrichtlinieService;

    private Veranstaltung veranstaltung;


    @BeforeEach
    @Transactional
    void setup() {
        veranstaltung = new Veranstaltung();
        veranstaltung.setName("Passwortrichtlinie-Test");
        veranstaltung.setBeginntAm(LocalDateTime.now());
        veranstaltung.persist();
    }


    private PasswortrichtlinieAnfrageDto juniorPin(int laenge) {
        PasswortrichtlinieAnfrageDto dto = new PasswortrichtlinieAnfrageDto();
        dto.minLaenge = laenge;
        dto.maxLaenge = laenge;
        dto.nurZiffern = true;
        return dto;
    }


    @Test
    @Transactional
    void getRichtlinien_ohneKonfiguration_liefertStandardFuerAlleRollen() {
        List<PasswortrichtlinieDto> richtlinien = passwortrichtlinieService.getRichtlinien(veranstaltung);

        assertThat(richtlinien).hasSize(5);
        assertThat(richtlinien).allMatch(r -> r.istStandard);
        assertThat(richtlinien).allSatisfy(r -> {
            assertThat(r.minLaenge).isEqualTo(8);
            assertThat(r.erfordertGrossbuchstabe).isTrue();
            assertThat(r.erfordertKleinbuchstabe).isTrue();
            assertThat(r.erfordertZiffer).isTrue();
            assertThat(r.erfordertSonderzeichen).isTrue();
            assertThat(r.nurZiffern).isFalse();
        });
    }


    @Test
    @Transactional
    void save_legtUeberschreibungAnUndGetRichtlinienZeigtSie() {
        passwortrichtlinieService.save(veranstaltung, "TEILNEHMER", juniorPin(6));

        List<PasswortrichtlinieDto> richtlinien = passwortrichtlinieService.getRichtlinien(veranstaltung);

        PasswortrichtlinieDto teilnehmerRichtlinie = richtlinien.stream()
            .filter(r -> "TEILNEHMER".equals(r.rolle)).findFirst().orElseThrow();
        assertThat(teilnehmerRichtlinie.istStandard).isFalse();
        assertThat(teilnehmerRichtlinie.minLaenge).isEqualTo(6);
        assertThat(teilnehmerRichtlinie.maxLaenge).isEqualTo(6);
        assertThat(teilnehmerRichtlinie.nurZiffern).isTrue();

        PasswortrichtlinieDto referentRichtlinie = richtlinien.stream()
            .filter(r -> "REFERENT".equals(r.rolle)).findFirst().orElseThrow();
        assertThat(referentRichtlinie.istStandard).isTrue();
    }


    @Test
    @Transactional
    void save_erneuterAufrufAktualisiertBestehendeZeileStattNeueAnzulegen() {
        passwortrichtlinieService.save(veranstaltung, "TEILNEHMER", juniorPin(8));
        passwortrichtlinieService.save(veranstaltung, "TEILNEHMER", juniorPin(6));

        assertThat(Passwortrichtlinie.count()).isEqualTo(1);
        assertThat(passwortrichtlinieService.resolve(veranstaltung, "TEILNEHMER").getMinLaenge()).isEqualTo(6);
    }


    @Test
    @Transactional
    void save_mitUngueltigerMindestlaenge_wirftBusinessException() {
        PasswortrichtlinieAnfrageDto dto = new PasswortrichtlinieAnfrageDto();
        dto.minLaenge = 0;

        assertThatThrownBy(() -> passwortrichtlinieService.save(veranstaltung, "TEILNEHMER", dto))
            .isInstanceOf(BusinessException.class);
    }


    @Test
    @Transactional
    void save_mitMaxKleinerAlsMin_wirftBusinessException() {
        PasswortrichtlinieAnfrageDto dto = new PasswortrichtlinieAnfrageDto();
        dto.minLaenge = 10;
        dto.maxLaenge = 6;

        assertThatThrownBy(() -> passwortrichtlinieService.save(veranstaltung, "TEILNEHMER", dto))
            .isInstanceOf(BusinessException.class);
    }


    @Test
    @Transactional
    void save_mitUnbekannterRolle_wirftBusinessException() {
        assertThatThrownBy(() -> passwortrichtlinieService.save(veranstaltung, "UNBEKANNT", juniorPin(6)))
            .isInstanceOf(BusinessException.class);
    }


    @Test
    @Transactional
    void deleteRichtlinie_setztAufStandardZurueck() {
        passwortrichtlinieService.save(veranstaltung, "TEILNEHMER", juniorPin(6));
        passwortrichtlinieService.deleteRichtlinie(veranstaltung, "TEILNEHMER");

        assertThat(passwortrichtlinieService.resolve(veranstaltung, "TEILNEHMER")).isEqualTo(Passwortrichtlinie.STANDARD);
    }


    @Test
    @Transactional
    void validate_mitJuniorPinRichtlinie_akzeptiertNurGenauSechsZiffern() {
        passwortrichtlinieService.save(veranstaltung, "TEILNEHMER", juniorPin(6));
        Teilnehmer teilnehmer = new Teilnehmer();
        teilnehmer.assignLoginName("teilnehmer.pin@test.de");
        teilnehmer.setEmail("teilnehmer.pin@test.de");
        teilnehmer.persist();

        passwortrichtlinieService.validate(veranstaltung, teilnehmer, "123456");

        assertThatThrownBy(() -> passwortrichtlinieService.validate(veranstaltung, teilnehmer, "12345"))
            .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> passwortrichtlinieService.validate(veranstaltung, teilnehmer, "12345a"))
            .isInstanceOf(BusinessException.class);
    }


    @Test
    @Transactional
    void validate_ohneKonfigurationFuerDieRolle_wendetStandardRegelAn() {
        Referent referent = new Referent();
        referent.assignLoginName("referent.standard@test.de");
        referent.setEmail("referent.standard@test.de");
        referent.persist();

        passwortrichtlinieService.validate(veranstaltung, referent, "Abcdef1!");

        assertThatThrownBy(() -> passwortrichtlinieService.validate(veranstaltung, referent, "abcdefg1"))
            .isInstanceOf(BusinessException.class);
    }
}
