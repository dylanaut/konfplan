package kreyj.konfplan.presentation;

import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import kreyj.konfplan.presentation.dto.SlotDto;
import kreyj.konfplan.persistence.Slot;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.List;

@Path("/api/slots")
@RolesAllowed({"ADMIN", "REFERENT", "TEILNEHMER"})
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Slots", description = "Verwaltung von Zeit-Slots")
public class SlotResource {

    @GET
    @Operation(summary = "Alle Slots abrufen", description = "Gibt eine Liste aller Zeit-Slots zurück.")
    public List<SlotDto> getAll() {
        return Slot.<Slot>listAll().stream()
                .map(SlotResource::mapSlotToDto)
                .toList();
    }


    // -------------------------------------------------------------------
    // helper methods
    // -------------------------------------------------------------------

    public static SlotDto mapSlotToDto(Slot slot) {
        SlotDto dto = new SlotDto();

        dto.id = slot.getId();
        dto.version = slot.getVersion();
        dto.description = slot.getDescription();
        dto.startTime = slot.getStartTime();
        dto.endTime = slot.getEndTime();
        dto.veranstaltungId = slot.getVeranstaltung().getId();

        return dto;
    }
}