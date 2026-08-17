package kreyj.konfplan.adapter.in.web;

import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import kreyj.konfplan.adapter.in.web.dto.VeranlagungDto;
import kreyj.konfplan.persistence.Veranlagung;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

@Path("/api/veranlagungen")
@RolesAllowed({"ADMIN", "REFERENT", "TEILNEHMER"})
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Veranlagungen", description = "Auflistung der verfügbaren Veranlagungen (fachliche/berufliche Ausrichtungen)")
public class VeranlagungResource {

    @GET
    @Operation(summary = "Alle Veranlagungen abrufen", description = "Gibt alle Veranlagungen inkl. Beschreibung zurück.")
    public List<VeranlagungDto> getAll() {
        return Arrays.stream(Veranlagung.values())
                .sorted(Comparator.comparing(Veranlagung::getBezeichnung))
                .map(VeranlagungDto::from)
                .toList();
    }
}
