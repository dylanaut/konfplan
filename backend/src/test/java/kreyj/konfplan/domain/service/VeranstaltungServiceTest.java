package kreyj.konfplan.domain.service;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import kreyj.konfplan.adapter.in.web.DatabaseCleaner;
import kreyj.konfplan.adapter.in.web.dto.GebaeudeSimpleDto;
import kreyj.konfplan.adapter.in.web.dto.VeranstaltungDto;
import kreyj.konfplan.persistence.Gebaeude;
import kreyj.konfplan.persistence.Gebaeudetyp;
import kreyj.konfplan.persistence.Veranstaltung;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
class VeranstaltungServiceTest extends DatabaseCleaner {

    @Inject
    VeranstaltungService veranstaltungService;


    @Test
    @Transactional
    void save_assignsGebaeudeByItsOwnId_notByTheVeranstaltungId() {
        Gebaeude gebaeude = new Gebaeude("Testhaus", "Teststadt", "Teststraße", "12345", Gebaeudetyp.SCHULE);
        gebaeude.persistAndFlush();

        VeranstaltungDto dto = new VeranstaltungDto();
        dto.setName("Neue Veranstaltung");
        dto.setBeginntAm(LocalDateTime.now());
        dto.setEndetAm(LocalDateTime.now().plusHours(1));
        dto.setGebaeude(List.of(GebaeudeSimpleDto.from(gebaeude)));

        VeranstaltungDto saved = veranstaltungService.save(dto);

        Veranstaltung persisted = Veranstaltung.findById(saved.id);
        assertThat(persisted.getGebaeude())
            .describedAs("Gebäude aus dem DTO muss der Veranstaltung zugewiesen werden, nicht ignoriert")
            .extracting(Gebaeude::getId)
            .containsExactly(gebaeude.getId());
    }
}
