package kreyj.konfplan.adapter.in.web;

import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import kreyj.konfplan.adapter.in.web.dto.SlotDto;
import kreyj.konfplan.persistence.Slot;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.List;

@Path("/api/slots")
@RolesAllowed({"ORGANISATOR", "ADMINISTRATOR", "REFERENT", "TEILNEHMER"})
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Slots", description = "Verwaltung von Zeit-Slots")
public class SlotResource {

    @GET
    @Operation(summary = "Alle Slots abrufen", description = "Gibt eine Liste aller Zeit-Slots zurück.")
    public List<SlotDto> getAll() {
        return Slot.<Slot>listAll().stream()
                .map(SlotDto::from)
                .toList();
    }
}
