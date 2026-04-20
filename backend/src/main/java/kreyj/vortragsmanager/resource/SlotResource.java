package kreyj.vortragsmanager.resource;

import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import kreyj.vortragsmanager.dto.EventSlotDto;
import kreyj.vortragsmanager.entity.EventSlot;

import java.util.List;
import java.util.stream.Collectors;

@Path("/api/slots")
@RolesAllowed({"ADMIN", "REFERENT", "TEILNEHMER"})
@Produces(MediaType.APPLICATION_JSON)
public class SlotResource {

    @GET
    public List<EventSlotDto> getAll() {
        return EventSlot.<EventSlot>listAll().stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    private EventSlotDto mapToDto(EventSlot s) {
        EventSlotDto dto = new EventSlotDto();
        dto.id = s.id;
        dto.version = s.version;
        dto.startTime = s.startTime;
        dto.endTime = s.endTime;
        dto.description = s.description;
        return dto;
    }
}
