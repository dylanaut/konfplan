package kreyj.konfplan.presentation;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
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
import kreyj.konfplan.application.service.AdminService;
import kreyj.konfplan.application.service.PlanService;
import kreyj.konfplan.application.service.PrioritaetService;
import kreyj.konfplan.application.service.TeilnehmerService;
import kreyj.konfplan.persistence.Nutzer;
import kreyj.konfplan.persistence.NutzerVerfuegbarkeit;
import kreyj.konfplan.persistence.Prioritaet;
import kreyj.konfplan.persistence.Teilnehmer;
import kreyj.konfplan.persistence.Veranstaltung;
import kreyj.konfplan.presentation.dto.FileUploadDto;
import kreyj.konfplan.presentation.dto.NutzerDto;
import kreyj.konfplan.presentation.dto.NutzerVerfuegbarkeitDto;
import kreyj.konfplan.presentation.dto.PrioritaetRequest;
import kreyj.konfplan.presentation.dto.TeilnehmerVeranstaltungDto;
import kreyj.konfplan.presentation.dto.ZuweisungDto;
import kreyj.konfplan.util.JwtHelper;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.time.LocalDateTime;
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
    private final TeilnehmerService teilnehmerService;
    private final PlanService planService;
    private final PrioritaetService prioritaetService;

    @Inject
    ReportResource reportResource;


    public TeilnehmerResource(JsonWebToken jwt, TeilnehmerService teilnehmerService, PlanService planService, PrioritaetService prioritaetService) {
        this.jwt = jwt;
        this.teilnehmerService = teilnehmerService;
        this.planService = planService;
        this.prioritaetService = prioritaetService;
    }


    // -------------------------------------------------------------------
    // ADMIN Endpunkte
    // -------------------------------------------------------------------

    @GET
    @RolesAllowed("ADMIN")
    @Operation(summary = "Alle Teilnehmer einer Veranstaltung abrufen")
    public Response getAlleVeranstaltungsteilnehmer(@QueryParam("vid") Long vid) {
        return Response.ok(teilnehmerService.findAll(vid)).build();
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
        return Response.ok(AdminService.mapNutzerToDto(nutzer)).build();
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
            if (updated == null) {
                return Response.status(Response.Status.NOT_FOUND).build();
            }
            return Response.ok(updated).build();
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
        if (nutzer == null) {
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
        if (byId == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        teilnehmerService.toggleActive(byId);
        return Response.noContent().build();
    }


    @POST
    @Path("/import")
    @RolesAllowed("ADMIN")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Operation(summary = "Teilnehmer importieren")
    public Response uploadCsv(FileUploadDto data, @QueryParam("vid") Long vid) {
        try {
            int count = teilnehmerService.importFromCsv(data.file.uploadedFile().toFile().toPath(), vid);
            return Response.ok("Import erfolgreich: " + count + " Teilnehmer angelegt.").build();
        } catch (Exception e) {
            return Response.status(Response.Status.BAD_REQUEST).entity("Fehler beim Import: " + e.getMessage()).build();
        }
    }


    // -------------------------------------------------------------------
    // TEILNEHMER Endpunkte
    // -------------------------------------------------------------------

    @GET
    @Path("/profile")
    @RolesAllowed("TEILNEHMER")
    @Operation(summary = "Eigenes Teilnehmerprofil abrufen")
    public Response getTeilnehmerProfile() {
        Teilnehmer teilnehmer = teilnehmerService.findByEmail(JwtHelper.getUserPrincipalName(jwt));
        if (null == teilnehmer) {
            throw new WebApplicationException("Teilnehmer not found", Response.Status.NOT_FOUND);
        }
        return Response.ok(AdminService.mapNutzerToDto(teilnehmer)).build();
    }


    @PUT
    @Path("/profile")
    @RolesAllowed("TEILNEHMER")
    @Transactional
    @Operation(summary = "Eigenes Teilnehmerprofil aktualisieren")
    public Response updateTeilnehmerProfile(@RequestBody(description = "Die aktualisierten Profildaten") NutzerDto teilnehmerDto) {
        if (teilnehmerDto == null) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }
        String email = JwtHelper.getUserPrincipalName(jwt);
        Teilnehmer teilnehmer = teilnehmerService.findByEmail(email);
        try {
            Teilnehmer updated = teilnehmerService.updateTeilnehmerProfile(teilnehmer, teilnehmerDto);
            if (updated == null) {
                return Response.status(Response.Status.NOT_FOUND).build();
            }
            return Response.ok(AdminService.mapNutzerToDto(updated)).build();
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
    @Path("/veranstaltungen/{vid}/laufzettel")
    @RolesAllowed("TEILNEHMER")
    @Produces(MediaType.TEXT_HTML)
    @Operation(summary = "Persönlichen Laufzettel abrufen (HTML)")
    public Response getLaufzettel(@PathParam("vid") Long vid) {
        Veranstaltung veranstaltung = Veranstaltung.findById(vid);
        if (null == veranstaltung) {
            throw new WebApplicationException("Veranstaltung not found", Response.Status.NOT_FOUND);
        }
        Teilnehmer teilnehmer = teilnehmerService.findByEmail(JwtHelper.getUserPrincipalName(jwt));
        if (null == teilnehmer) {
            throw new WebApplicationException("Teilnehmer not found", Response.Status.NOT_FOUND);
        }
        return reportResource.getLaufzettelTeilnehmer(vid, teilnehmer.getId());
    }


    @GET
    @Path("/veranstaltungen/{vid}/laufzettel-pdf")
    @RolesAllowed("TEILNEHMER")
    @Produces("application/pdf")
    @Operation(summary = "Persönlichen Laufzettel abrufen (PDF)")
    public Response getLaufzettelPdf(@PathParam("vid") Long vid) {
        Veranstaltung veranstaltung = Veranstaltung.findById(vid);
        if (null == veranstaltung) {
            throw new WebApplicationException("Veranstaltung not found", Response.Status.NOT_FOUND);
        }
        Teilnehmer teilnehmer = teilnehmerService.findByEmail(JwtHelper.getUserPrincipalName(jwt));
        if (null == teilnehmer) {
            throw new WebApplicationException("Teilnehmer not found", Response.Status.NOT_FOUND);
        }
        return reportResource.getLaufzettelTeilnehmerPdf(vid, teilnehmer.getId());
    }


    @GET
    @Path("/zuweisungen")
    @RolesAllowed("TEILNEHMER")
    @Operation(summary = "Persönlichen Plan abrufen")
    public Response getPlan(@QueryParam("vid") Long vid) {
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
    @Path("/prios")
    @RolesAllowed("TEILNEHMER")
    @Operation(summary = "Meine Prioritäten abrufen")
    public Response getPrios(@QueryParam("vid") Long vid) {
        Veranstaltung veranstaltung = Veranstaltung.findById(vid);
        if (veranstaltung == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        List<Prioritaet> prioritaetenForUser = prioritaetService.getNutzerPrioritaeten(JwtHelper.getUserPrincipalName(jwt));
        return Response.ok(prioritaetenForUser).build();
    }


    @POST
    @Path("/prios")
    @RolesAllowed("TEILNEHMER")
    @Operation(summary = "Prioritäten speichern")
    public Response savePriorities(@RequestBody(description = "Liste der Prioritäts-Anfragen") List<PrioritaetRequest> requests) {
        prioritaetService.savePrioritaeten(JwtHelper.getUserPrincipalName(jwt), requests);
        return Response.ok().build();
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
    @Transactional
    @RolesAllowed("TEILNEHMER")
    @Operation(summary = "Verfügbarkeit aktualisieren")
    public Response updateVerfuegbarkeit(@PathParam("vid") Long vid, @RequestBody(description = "Die Verfügbarkeitsdaten") NutzerVerfuegbarkeitDto dto) {
        Nutzer nutzer = Nutzer.findByEmail(JwtHelper.getUserPrincipalName(jwt));
        if (!(nutzer instanceof Teilnehmer) || !nutzer.getId().equals(dto.nutzerId)) {
            return Response.status(FORBIDDEN).build();
        }
        Veranstaltung veranstaltung = Veranstaltung.findById(vid);
        if (veranstaltung == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        if (veranstaltung.getDeadlineTeilnehmer() != null && veranstaltung.getDeadlineTeilnehmer().isBefore(LocalDateTime.now())) {
            return Response.status(FORBIDDEN).entity("Die Deadline für Teilnehmer ist bereits abgelaufen.").build();
        }
        NutzerVerfuegbarkeit v = NutzerVerfuegbarkeit.findById(nvIdL(nutzer.getId(), vid));
        if (v == null) {
            return Response.status(Response.Status.NOT_FOUND).entity("Verfügbarkeitseintrag nicht gefunden.").build();
        }
        v.getVerfuegbareSlotIds().clear();
        v.getVerfuegbareSlotIds().addAll(dto.verfuegbareSlotIds);
        v.persist();
        return Response.ok().build();
    }
}
