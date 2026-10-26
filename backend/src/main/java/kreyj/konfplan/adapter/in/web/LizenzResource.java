package kreyj.konfplan.adapter.in.web;

import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import kreyj.konfplan.adapter.in.web.dto.LizenzAktivierungDto;
import kreyj.konfplan.adapter.in.web.dto.LizenzAnforderungDto;
import kreyj.konfplan.adapter.in.web.dto.LizenzStatusDto;
import kreyj.konfplan.domain.lizenz.LizenzServerException;
import kreyj.konfplan.domain.lizenz.LizenzStatus;
import kreyj.konfplan.domain.service.LizenzStatusService;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.Map;
import java.util.function.Supplier;

@Path("/api/lizenz")
@RolesAllowed("ADMINISTRATOR")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Lizenz", description = "Lizenzstatus dieser Instanz, Anforderungsdatei, Offline-Lizenz und Online-Aktivierung (nur Administratoren)")
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
        return dto(lizenzStatusService.status());
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
        return dto(lizenzStatusService.status());
    }

    @POST
    @Path("/aktivierung")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = "Online aktivieren", description = "Aktiviert diese Instanz mit einem Lizenzschlüssel beim Lizenzserver. 400 bei Ablehnung, 502 wenn der Server nicht erreichbar ist.")
    public LizenzStatusDto aktivieren(@RequestBody(description = "Der Lizenzschlüssel") LizenzAktivierungDto anfrage) {
        return serverAufruf(() -> lizenzStatusService.aktiviere(null == anfrage ? null : anfrage.lizenzschluessel));
    }

    @POST
    @Path("/revalidierung")
    @Operation(summary = "Online-Lizenz jetzt erneuern", description = "Erneuert die Online-Lizenz sofort beim Lizenzserver statt auf den nächsten automatischen Lauf zu warten.")
    public LizenzStatusDto revalidieren() {
        return serverAufruf(lizenzStatusService::revalidiere);
    }

    @DELETE
    @Path("/aktivierung")
    @Operation(summary = "Lizenz entfernen", description = "Gibt den Aktivierungsplatz beim Lizenzserver frei und entfernt die Lizenz von dieser Instanz. Mit nurLokal=true wird nur lokal entfernt (der Platz muss dann beim Hersteller freigegeben werden).")
    public LizenzStatusDto deaktivieren(@QueryParam("nurLokal") @DefaultValue("false") boolean nurLokal) {
        return serverAufruf(() -> lizenzStatusService.deaktiviere(nurLokal));
    }

    /** Ein nicht erreichbarer Lizenzserver ist ein Gateway-Problem (502), eine Ablehnung ein Fehler der Anfrage (400). */
    private LizenzStatusDto serverAufruf(Supplier<LizenzStatus> aufruf) {
        try {
            return dto(aufruf.get());
        } catch (LizenzServerException e) {
            if (LizenzServerException.Art.ABGELEHNT == e.getArt()) {
                throw e;
            }
            throw new WebApplicationException(Response.status(Response.Status.BAD_GATEWAY)
                .type(MediaType.APPLICATION_JSON)
                .entity(Map.of("error", e.getMessage(), "code", e.getArt().name()))
                .build());
        }
    }

    private LizenzStatusDto dto(LizenzStatus status) {
        return LizenzStatusDto.from(status, durchsetzung, lizenzStatusService.onlineMoeglich(), lizenzStatusService.zustand());
    }
}
