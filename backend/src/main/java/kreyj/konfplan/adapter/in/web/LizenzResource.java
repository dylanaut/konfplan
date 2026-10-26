package kreyj.konfplan.adapter.in.web;

import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import kreyj.konfplan.adapter.in.web.dto.LizenzAnforderungDto;
import kreyj.konfplan.adapter.in.web.dto.LizenzStatusDto;
import kreyj.konfplan.domain.service.LizenzStatusService;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Path("/api/lizenz")
@RolesAllowed("ADMINISTRATOR")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Lizenz", description = "Lizenzstatus dieser Instanz, Anforderungsdatei und Installation einer Offline-Lizenz (nur Administratoren)")
public class LizenzResource {
    private final LizenzStatusService lizenzStatusService;
    private final boolean durchsetzung;

    public LizenzResource(LizenzStatusService lizenzStatusService,
                          @ConfigProperty(name = "konfplan.lizenz.durchsetzung", defaultValue = "false") boolean durchsetzung) {
        this.lizenzStatusService = lizenzStatusService;
        this.durchsetzung = durchsetzung;
    }

    @GET
    @Operation(summary = "Lizenzstatus abrufen", description = "Liefert den Zustand der Lizenz dieser Instanz und, falls gültig, deren Eckdaten.")
    public LizenzStatusDto status() {
        return LizenzStatusDto.from(lizenzStatusService.status(), durchsetzung);
    }

    @GET
    @Path("/anforderung")
    @Operation(summary = "Anforderungsdatei abrufen", description = "Instanz-ID und Public Key dieser Instanz; der Hersteller stellt daraus eine an diese Instanz gebundene Offline-Lizenz aus.")
    public LizenzAnforderungDto anforderung() {
        return LizenzAnforderungDto.from(lizenzStatusService.anforderung());
    }

    @PUT
    @Consumes(MediaType.TEXT_PLAIN)
    @Operation(summary = "Offline-Lizenz installieren", description = "Prüft die Lizenz und installiert sie. Eine ungültige Lizenz wird mit 400 abgelehnt und ersetzt keine bestehende.")
    public LizenzStatusDto installieren(@RequestBody(description = "Die Lizenz (JWS) als Text") String lizenz) {
        lizenzStatusService.installiere(lizenz);
        return LizenzStatusDto.from(lizenzStatusService.status(), durchsetzung);
    }
}
