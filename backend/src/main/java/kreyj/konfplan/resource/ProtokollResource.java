package kreyj.konfplan.resource;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import kreyj.konfplan.dto.ProtokollDto;
import kreyj.konfplan.service.ProtokollService;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.List;
import java.util.stream.Collectors;

@Path("/api/admin/protokolle")
@RolesAllowed("ADMIN")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Protokoll", description = "Endpunkt zum Abrufen von Protokolleinträgen")
public class ProtokollResource {

    @Inject
    ProtokollService protokollService;

    @GET
    @Operation(summary = "Alle Protokolleinträge abrufen", description = "Gibt eine Liste aller Protokolleinträge im System zurück.")
    public List<ProtokollDto> getAllProtokollEntries() {
        return protokollService.listAll().stream()
                .map(ProtokollDto::new)
                .collect(Collectors.toList());
    }
}