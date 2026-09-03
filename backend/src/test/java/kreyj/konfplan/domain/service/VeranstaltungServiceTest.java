package kreyj.konfplan.domain.service;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import kreyj.konfplan.adapter.in.web.DatabaseCleaner;
import kreyj.konfplan.adapter.in.web.dto.GebaeudeSimpleDto;
import kreyj.konfplan.adapter.in.web.dto.VeranstaltungDto;
import kreyj.konfplan.domain.exception.VeranstaltungException;
import kreyj.konfplan.persistence.Gebaeude;
import kreyj.konfplan.persistence.Gebaeudetyp;
import kreyj.konfplan.persistence.Organisator;
import kreyj.konfplan.persistence.Veranstaltung;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

@QuarkusTest
class VeranstaltungServiceTest extends DatabaseCleaner {

    @Inject
    VeranstaltungService veranstaltungService;


    private Organisator persistedOrganisator() {
        Organisator organisator = new Organisator();
        organisator.assignLoginName("organisator" + System.nanoTime());
        organisator.setEmail(organisator.getLoginName() + "@test.de");
        organisator.persistAndFlush();
        return organisator;
    }


    @Test
    @Transactional
    void save_assignsGebaeudeByItsOwnId_notByTheVeranstaltungId() {
        Gebaeude gebaeude = new Gebaeude("Testhaus", "Teststadt", "Teststraße", "12345", Gebaeudetyp.SCHULE);
        gebaeude.persistAndFlush();
        Organisator organisator = persistedOrganisator();

        VeranstaltungDto dto = new VeranstaltungDto();
        dto.setName("Neue Veranstaltung");
        dto.setBeginntAm(LocalDateTime.now());
        dto.setEndetAm(LocalDateTime.now().plusHours(1));
        dto.setGebaeude(List.of(GebaeudeSimpleDto.from(gebaeude)));
        dto.setOrganisatorIds(List.of(organisator.getId()));

        VeranstaltungDto saved = veranstaltungService.save(dto);

        Veranstaltung persisted = Veranstaltung.findById(saved.id);
        assertThat(persisted.getGebaeude())
            .describedAs("Gebäude aus dem DTO muss der Veranstaltung zugewiesen werden, nicht ignoriert")
            .extracting(Gebaeude::getId)
            .containsExactly(gebaeude.getId());
    }


    @Test
    @Transactional
    void save_ohneOrganisatorIds_wirdAbgelehnt() {
        VeranstaltungDto dto = new VeranstaltungDto();
        dto.setName("Veranstaltung ohne Organisator");
        dto.setBeginntAm(LocalDateTime.now());
        dto.setEndetAm(LocalDateTime.now().plusHours(1));

        assertThatExceptionOfType(VeranstaltungException.class)
            .isThrownBy(() -> veranstaltungService.save(dto))
            .withMessageContaining("mindestens eine/n Organisator");
    }


    @Test
    @Transactional
    void save_mitAusschliesslichUnbekanntenOrganisatorIds_wirdAbgelehnt() {
        VeranstaltungDto dto = new VeranstaltungDto();
        dto.setName("Veranstaltung mit unbekanntem Organisator");
        dto.setBeginntAm(LocalDateTime.now());
        dto.setEndetAm(LocalDateTime.now().plusHours(1));
        dto.setOrganisatorIds(List.of(-1L));

        assertThatExceptionOfType(VeranstaltungException.class)
            .isThrownBy(() -> veranstaltungService.save(dto))
            .withMessageContaining("mindestens eine/n Organisator");
    }
}
