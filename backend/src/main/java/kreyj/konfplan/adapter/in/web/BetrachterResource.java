package kreyj.konfplan.adapter.in.web;

import jakarta.annotation.security.RolesAllowed;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import kreyj.konfplan.adapter.in.web.dto.BetrachterVeranstaltungDto;
import kreyj.konfplan.adapter.in.web.dto.NutzerDto;
import kreyj.konfplan.adapter.in.web.dto.NutzerVerfuegbarkeitDto;
import kreyj.konfplan.adapter.in.web.dto.VortragTitelDto;
import kreyj.konfplan.adapter.in.web.dto.ZuweisungDto;
import kreyj.konfplan.domain.service.BetrachterService;
import kreyj.konfplan.persistence.Betrachter;
import kreyj.konfplan.persistence.Nutzer;
import kreyj.konfplan.persistence.Veranstaltung;
import kreyj.konfplan.persistence.Wahlvortrag;
import kreyj.konfplan.util.JwtHelper;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Rein lesende Endpunkte für die Rolle Betrachter (siehe #718): liefert für eine der eigenen
 * Veranstaltungen ausschließlich die Teilnehmer der zugewiesenen Gruppen sowie deren
 * Verfügbarkeiten und Buchungen - kein Nutzer-/Stammdaten-Schreibzugriff.
 */
@Path("/api/betrachter")
@RolesAllowed("BETRACHTER")
@Produces(MediaType.APPLICATION_JSON)
@Transactional
@Tag(name = "Betrachter", description = "Rein lesende Endpunkte für die Rolle Betrachter")
public class BetrachterResource {

    private final JsonWebToken jwt;
    private final BetrachterService betrachterService;

    @SuppressWarnings("CdiInjectionPointsInspection")
    public BetrachterResource(JsonWebToken jwt, BetrachterService betrachterService) {
        this.jwt = jwt;
        this.betrachterService = betrachterService;
    }


    private Betrachter aktuellerBetrachter() {
        Nutzer nutzer = Nutzer.findByLoginName(JwtHelper.getUserPrincipalName(jwt));
        return nutzer instanceof Betrachter b ? b : null;
    }


    @GET
    @Path("/veranstaltungen")
    @Operation(summary = "Eigene Veranstaltungen abrufen")
    public List<BetrachterVeranstaltungDto> getVeranstaltungen() {
        Betrachter betrachter = aktuellerBetrachter();
        if (null == betrachter) {
            return List.of();
        }
        return betrachter.getVeranstaltungen().stream()
            .map(v -> {
                BetrachterVeranstaltungDto dto = new BetrachterVeranstaltungDto();
                dto.id = v.getId();
                dto.name = v.getName();
                dto.beginntAm = v.getBeginntAm();
                dto.endetAm = v.getEndetAm();
                return dto;
            })
            .sorted(Comparator.comparing(dto -> dto.beginntAm))
            .toList();
    }


    @GET
    @Path("/veranstaltungen/{vid}/teilnehmer")
    @Operation(summary = "Teilnehmer der eigenen Gruppen abrufen")
    public Response getTeilnehmer(@PathParam("vid") Long vid) {
        Veranstaltung veranstaltung = veranstaltungFuerBetrachter(vid);
        if (null == veranstaltung) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        Betrachter betrachter = aktuellerBetrachter();
        List<NutzerDto> teilnehmer = betrachterService.getSichtbareTeilnehmer(betrachter, veranstaltung).stream()
            .map(NutzerDto::from)
            .toList();
        return Response.ok(teilnehmer).build();
    }


    @GET
    @Path("/veranstaltungen/{vid}/verfuegbarkeiten")
    @Operation(summary = "Verfügbarkeiten der eigenen Gruppen abrufen")
    public Response getVerfuegbarkeiten(@PathParam("vid") Long vid) {
        Veranstaltung veranstaltung = veranstaltungFuerBetrachter(vid);
        if (null == veranstaltung) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        Betrachter betrachter = aktuellerBetrachter();
        List<NutzerVerfuegbarkeitDto> verfuegbarkeiten = betrachterService.getVerfuegbarkeiten(betrachter, veranstaltung);
        return Response.ok(verfuegbarkeiten).build();
    }


    @GET
    @Path("/veranstaltungen/{vid}/vortraege")
    @Operation(summary = "Titel der Wahlvorträge abrufen (für die Anzeige der Prioritäten)")
    public Response getVortraege(@PathParam("vid") Long vid) {
        Veranstaltung veranstaltung = veranstaltungFuerBetrachter(vid);
        if (null == veranstaltung) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        List<VortragTitelDto> vortraege = Wahlvortrag.<Wahlvortrag>find("veranstaltung", veranstaltung).stream()
            .map(v -> new VortragTitelDto(v.getId(), v.getTitel()))
            .toList();
        return Response.ok(vortraege).build();
    }


    @GET
    @Path("/veranstaltungen/{vid}/zuweisungen")
    @Operation(summary = "Buchungen der eigenen Gruppen abrufen (nur veröffentlichter Plan)")
    public Response getZuweisungen(@PathParam("vid") Long vid) {
        Veranstaltung veranstaltung = veranstaltungFuerBetrachter(vid);
        if (null == veranstaltung) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        Betrachter betrachter = aktuellerBetrachter();
        Map<Long, List<ZuweisungDto>> zuweisungen = betrachterService.getZuweisungen(betrachter, veranstaltung);
        return Response.ok(zuweisungen).build();
    }


    /**
     * Lädt die Veranstaltung nur, wenn der aktuelle Betrachter ihr tatsächlich zugeordnet ist -
     * verhindert, dass ein Betrachter per vid-Pfadparameter Daten einer fremden Veranstaltung
     * abfragt.
     */
    private Veranstaltung veranstaltungFuerBetrachter(Long vid) {
        Betrachter betrachter = aktuellerBetrachter();
        if (null == betrachter) {
            return null;
        }
        Veranstaltung veranstaltung = Veranstaltung.findById(vid);
        if (null == veranstaltung || !betrachter.getVeranstaltungen().contains(veranstaltung)) {
            return null;
        }
        return veranstaltung;
    }
}
