package kreyj.konfplan.adapter.in.web;

import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import kreyj.konfplan.adapter.in.web.dto.WartungshinweisDto;
import kreyj.konfplan.domain.service.WartungshinweisService;
import kreyj.konfplan.persistence.Wartungshinweis;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Path("/api/wartungshinweis")
@RolesAllowed({"ORGANISATOR", "ADMINISTRATOR", "REFERENT", "TEILNEHMER"})
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Wartungshinweis", description = "Administrator-Ankündigung eines geplanten Wartungsfensters, von allen eingeloggten Nutzern abgerufen")
public class WartungshinweisResource {
    private final WartungshinweisService wartungshinweisService;

    public WartungshinweisResource(WartungshinweisService wartungshinweisService) {
        this.wartungshinweisService = wartungshinweisService;
    }

    @GET
    @Operation(summary = "Aktuellen Wartungshinweis abrufen", description = "Liefert die aktuelle Ankündigung, oder leere Felder, falls keine gesetzt oder bereits abgelaufen ist.")
    public WartungshinweisDto get() {
        Wartungshinweis w = wartungshinweisService.getAktuelle();
        return null == w ? WartungshinweisDto.leer() : WartungshinweisDto.from(w);
    }

    @PUT
    @RolesAllowed("ADMINISTRATOR")
    @Operation(summary = "Wartungshinweis setzen", description = "Legt Start- und Endzeitpunkt des angekündigten Wartungsfensters fest (überschreibt eine bestehende Ankündigung).")
    public WartungshinweisDto setzen(@RequestBody(description = "Start- und Endzeitpunkt des Wartungsfensters") WartungshinweisDto dto) {
        Wartungshinweis w = wartungshinweisService.setzen(dto.startZeitpunkt, dto.endeZeitpunkt);
        return WartungshinweisDto.from(w);
    }

    @DELETE
    @RolesAllowed("ADMINISTRATOR")
    @Operation(summary = "Wartungshinweis löschen", description = "Entfernt eine bestehende Ankündigung.")
    public Response loeschen() {
        wartungshinweisService.loeschen();
        return Response.noContent().build();
    }
}
