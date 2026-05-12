package kreyj.konfplan.resource;

import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import kreyj.konfplan.dto.EventSlotDto;
import kreyj.konfplan.persistence.EventSlot;

import java.util.List;

@Path("/api/slots")
@RolesAllowed({"ADMIN", "REFERENT", "TEILNEHMER"})
@Produces(MediaType.APPLICATION_JSON)
public class SlotResource {

    @GET
    public List<EventSlotDto> getAll() {
        return EventSlot.<EventSlot>listAll().stream()
                .map(SlotResource::mapSlotToDto)
                .toList();
    }


    // -------------------------------------------------------------------
    // helper methods
    // -------------------------------------------------------------------

    public static EventSlotDto mapSlotToDto(EventSlot eventSlot) {
        EventSlotDto dto = new EventSlotDto();

        dto.id = eventSlot.id;
        dto.version = eventSlot.version;

        dto.description = eventSlot.description;
        dto.startTime = eventSlot.startTime;
        dto.endTime = eventSlot.endTime;
        dto.veranstaltungId = eventSlot.veranstaltung.id;

        return dto;
    }
}
