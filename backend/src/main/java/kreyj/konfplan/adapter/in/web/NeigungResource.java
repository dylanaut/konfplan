package kreyj.konfplan.adapter.in.web;

import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import kreyj.konfplan.adapter.in.web.dto.NeigungDto;
import kreyj.konfplan.persistence.Neigung;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

@Path("/api/neigungen")
@RolesAllowed({"ORGANISATOR", "ADMINISTRATOR", "REFERENT", "TEILNEHMER"})
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Neigungen", description = "Auflistung der verfügbaren Neigungen (fachliche/berufliche Ausrichtungen)")
public class NeigungResource {

    @GET
    @Operation(summary = "Alle Neigungen abrufen", description = "Gibt alle Neigungen inkl. Beschreibung zurück.")
    public List<NeigungDto> getAll() {
        return Arrays.stream(Neigung.values())
                .sorted(Comparator.comparing(Neigung::getBezeichnung))
                .map(NeigungDto::from)
                .toList();
    }
}
