package kreyj.konfplan.adapter.in.web;

import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import kreyj.konfplan.domain.service.ProtokollService;
import kreyj.konfplan.adapter.in.web.dto.ProtokollDto;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.List;

@Path("/api/admin/protokolle")
@RolesAllowed("ADMIN")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Protokoll", description = "Endpunkt zum Abrufen von Protokolleinträgen")
public class ProtokollResource {
    private final ProtokollService protokollService;

    @SuppressWarnings("unused")
    public ProtokollResource(ProtokollService protokollService) {
        this.protokollService = protokollService;
    }

    @GET
    @Operation(summary = "Alle Protokolleinträge abrufen", description = "Gibt eine Liste aller Protokolleinträge im System zurück.")
    public List<ProtokollDto> getAllProtokollEntries() {
        return protokollService.listAll().stream()
                .map(ProtokollDto::new)
                .toList();
    }
}
