package kreyj.konfplan.adapter.in.web;

import jakarta.annotation.security.RolesAllowed;
import jakarta.persistence.OptimisticLockException;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import kreyj.konfplan.adapter.in.web.dto.NutzerDto;
import kreyj.konfplan.adapter.in.web.dto.NutzerVerfuegbarkeitDto;
import kreyj.konfplan.adapter.in.web.dto.TeilnehmerDto;
import kreyj.konfplan.adapter.in.web.dto.TeilnehmerVeranstaltungDto;
import kreyj.konfplan.adapter.in.web.dto.VortragDto;
import kreyj.konfplan.adapter.in.web.dto.ZuweisungDto;
import kreyj.konfplan.application.port.in.AdminServiceInterface;
import kreyj.konfplan.application.port.in.TeilnehmerServiceInterface;
import kreyj.konfplan.domain.service.PlanService;
import kreyj.konfplan.domain.service.PrioritaetService;
import kreyj.konfplan.persistence.Nutzer;
import kreyj.konfplan.persistence.NutzerVerfuegbarkeit;
import kreyj.konfplan.persistence.Teilnehmer;
import kreyj.konfplan.persistence.Veranstaltung;
import kreyj.konfplan.util.JwtHelper;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.List;

import static jakarta.ws.rs.core.Response.Status.FORBIDDEN;
import static kreyj.konfplan.persistence.NutzerVerfuegbarkeitId.nvIdL;

@Path("/api/teilnehmer")
@RolesAllowed({"ADMIN", "TEILNEHMER"})
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Teilnehmer", description = "Endpunkte zur Verwaltung von Teilnehmern, deren Plänen und Prioritäten")
public class TeilnehmerResource {
    private final JsonWebToken jwt;
    private final TeilnehmerServiceInterface teilnehmerService;
    private final PlanService planService;
    private final PrioritaetService prioritaetService;
    private final ReportResource reportResource;
    private final AdminServiceInterface adminService;


    public TeilnehmerResource(JsonWebToken jwt, TeilnehmerServiceInterface teilnehmerService, PlanService planService,
                              PrioritaetService prioritaetService, ReportResource reportResource, AdminServiceInterface adminService) {
        this.jwt = jwt;
        this.teilnehmerService = teilnehmerService;
        this.planService = planService;
        this.prioritaetService = prioritaetService;
        this.reportResource = reportResource;
        this.adminService = adminService;
    }


    // -------------------------------------------------------------------
    // ADMIN Endpunkte
    // -------------------------------------------------------------------


    @GET
    @RolesAllowed("ADMIN")
    @Operation(summary = "Alle Teilnehmer einer Veranstaltung abrufen")
    public Response getAlleVeranstaltungsteilnehmer(@QueryParam("vid") Long vid) {
        List<Teilnehmer> alleTeilnehmer = teilnehmerService.findAll(vid);
        return Response.ok(alleTeilnehmer.stream().map(TeilnehmerDto::from).toList()).build();
    }


    @GET
    @Path("/{id}")
    @RolesAllowed("ADMIN")
    @Operation(summary = "Einen Teilnehmer abrufen")
    public Response getTeilnehmer(@PathParam("id") Long id) {
        Nutzer nutzer = teilnehmerService.findById(id);
        if (null == nutzer) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.ok(NutzerDto.from(nutzer)).build();
    }


    @POST
    @Transactional
    @RolesAllowed("ADMIN")
    @Operation(summary = "Neuen Teilnehmer erstellen")
    public Response createTeilnehmer(@RequestBody(description = "Der zu erstellende Teilnehmer") Teilnehmer user, @QueryParam("vid") Long vid) {
        Teilnehmer created = teilnehmerService.createTeilnehmer(user, vid);
        return Response.status(Response.Status.CREATED).entity(created).build();
    }


    @PUT
    @Path("/{id}")
    @Transactional
    @RolesAllowed("ADMIN")
    @Operation(summary = "Teilnehmer aktualisieren")
    public Response updateTeilnehmer(@PathParam("id") Long id, @RequestBody(description = "Die aktualisierten Teilnehmerdaten") NutzerDto user, @QueryParam("vid") Long vid) {
        try {
            Teilnehmer updated = teilnehmerService.updateTeilnehmer(id, user, vid);
            if (null == updated) {
                return Response.status(Response.Status.NOT_FOUND).build();
            }
            return Response.ok(NutzerDto.from(updated)).build();
        } catch (OptimisticLockException e) {
            return Response.status(Response.Status.CONFLICT).entity(e.getMessage()).build();
        }
    }


    @DELETE
    @Path("/{id}")
    @RolesAllowed("ADMIN")
    @Operation(summary = "Teilnehmer löschen")
    public Response deleteTeilnehmer(@PathParam("id") Long id) {
        Nutzer nutzer = teilnehmerService.findById(id);
        if (null == nutzer) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        teilnehmerService.deleteUser(nutzer);
        return Response.noContent().build();
    }


    @PATCH
    @Path("/{id}/toggle")
    @RolesAllowed("ADMIN")
    @Operation(summary = "Aktivierungsstatus umschalten")
    public Response toggleActive(@PathParam("id") Long id) {
        Nutzer byId = teilnehmerService.findById(id);
        if (null == byId) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        teilnehmerService.toggleActive(byId);
        return Response.noContent().build();
    }


    // -------------------------------------------------------------------
    // TEILNEHMER Endpunkte
    // -------------------------------------------------------------------


    @GET
    @Path("/profile")
    @RolesAllowed("TEILNEHMER")
    @Operation(summary = "Eigenes Teilnehmerprofil abrufen")
    @Transactional
    public Response getTeilnehmerProfile() {
        Teilnehmer teilnehmer = teilnehmerService.findByEmail(JwtHelper.getUserPrincipalName(jwt));
        if (null == teilnehmer) {
            throw new WebApplicationException("Teilnehmer not found", Response.Status.NOT_FOUND);
        }
        return Response.ok(NutzerDto.from(teilnehmer)).build();
    }


    @PUT
    @Path("/profile")
    @RolesAllowed("TEILNEHMER")
    @Transactional
    @Operation(summary = "Eigenes Teilnehmerprofil aktualisieren")
    public Response updateTeilnehmerProfile(@RequestBody(description = "Die aktualisierten Profildaten") NutzerDto teilnehmerDto) {
        if (null == teilnehmerDto) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }
        String email = JwtHelper.getUserPrincipalName(jwt);
        Teilnehmer teilnehmer = teilnehmerService.findByEmail(email);
        try {
            Teilnehmer updated = teilnehmerService.updateTeilnehmerProfile(teilnehmer, teilnehmerDto);
            if (null == updated) {
                return Response.status(Response.Status.NOT_FOUND).build();
            }
            return Response.ok(NutzerDto.from(updated)).build();
        } catch (OptimisticLockException e) {
            return Response.status(Response.Status.CONFLICT).entity(e.getMessage()).build();
        }
    }


    @GET
    @Path("/veranstaltungen")
    @RolesAllowed("TEILNEHMER")
    @Operation(summary = "Meine Veranstaltungen abrufen")
    public List<TeilnehmerVeranstaltungDto> getTeilnehmerVeranstaltungen() {
        return teilnehmerService.getTeilnehmerVeranstaltungen(JwtHelper.getUserPrincipalName(jwt));
    }


    @GET
    @Path("/veranstaltungen/{vid}/vortraege")
    @RolesAllowed("TEILNEHMER")
    @Operation(summary = "Meine Vorträge für eine Veranstaltung abrufen")
    public Response getMeineVortraege(@PathParam("vid") Long vid) {
        List<VortragDto> meineVortraege = teilnehmerService.getVortraegeFuerTeilnehmerInVeranstaltung(vid, JwtHelper.getUserPrincipalName(jwt));
        return Response.ok(meineVortraege).build();
    }


    @GET
    @Path("/veranstaltungen/{vid}/zuweisungen")
    @RolesAllowed("TEILNEHMER")
    @Operation(summary = "Persönlichen Plan abrufen")
    public Response getPlan(@PathParam("vid") Long vid) {
        Veranstaltung veranstaltung = Veranstaltung.findById(vid);
        if (null == veranstaltung) {
            throw new WebApplicationException("Veranstaltung not found", Response.Status.NOT_FOUND);
        }
        Teilnehmer teilnehmer = teilnehmerService.findByEmail(JwtHelper.getUserPrincipalName(jwt));
        if (null == teilnehmer) {
            throw new WebApplicationException("Teilnehmer not found", Response.Status.NOT_FOUND);
        }
        List<ZuweisungDto> planFuerTeilnehmer = planService.getPlanFuerTeilnehmer(teilnehmer, veranstaltung);
        return Response.ok(planFuerTeilnehmer).build();
    }


    @GET
    @Path("/veranstaltungen/{vid}/verfuegbarkeiten")
    @RolesAllowed("TEILNEHMER")
    @Operation(summary = "Meine Verfügbarkeiten abrufen")
    public NutzerVerfuegbarkeitDto getVerfuegbarkeiten(@PathParam("vid") Long vid) {
        Nutzer nutzer = Nutzer.findByEmail(JwtHelper.getUserPrincipalName(jwt));
        if (!(nutzer instanceof Teilnehmer)) {
            throw new WebApplicationException("Nutzer ist kein Teilnehmer", FORBIDDEN.getStatusCode());
        }
        NutzerVerfuegbarkeit nv = NutzerVerfuegbarkeit.findById(nvIdL(nutzer.getId(), vid));
        if (null == nv) {
            throw new WebApplicationException("Keine Verfügbarkeit für diesen Nutzer und diese Veranstaltung gefunden.", Response.Status.NOT_FOUND);
        }
        return new NutzerVerfuegbarkeitDto(nv);
    }


    @POST
    @Path("/veranstaltungen/{vid}/verfuegbarkeiten")
    @RolesAllowed("TEILNEHMER")
    @Operation(summary = "Verfügbarkeit aktualisieren")
    public Response updateVerfuegbarkeit(@PathParam("vid") Long vid, @RequestBody(description = "Die Verfügbarkeitsdaten") NutzerVerfuegbarkeitDto dto) {
        teilnehmerService.updateVerfuegbarkeit(vid, dto, JwtHelper.getUserPrincipalName(jwt));
        return Response.ok().build();
    }
}
